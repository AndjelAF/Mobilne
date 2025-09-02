package com.example.mapmyst.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.CacheFind
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.domain.repository.CacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CacheDiscoveryState(
    val isScanning: Boolean = false,
    val nearbyCache: Cache? = null,
    val distanceToCache: Double? = null,
    val canDiscover: Boolean = false,
    val discoveryInProgress: Boolean = false,
    val discoveredCache: Cache? = null,
    val pointsEarned: Int = 0,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class CacheDiscoveryViewModel @Inject constructor(
    private val cacheRepository: CacheRepository
) : ViewModel() {

    private val _discoveryState = MutableStateFlow(CacheDiscoveryState())
    val discoveryState: StateFlow<CacheDiscoveryState> = _discoveryState.asStateFlow()

    companion object {
        private const val DISCOVERY_RADIUS_METERS = 10.0 //10m zbog testiranja smanjeno
        private const val SEARCH_RADIUS_METERS = 50.0
        private const val SCAN_INTERVAL_MS = 3000L
        private const val MIN_ACCURACY_METERS = 20.0
    }

    private var isMonitoring = false
    private var currentUserLocation: Location? = null

    fun startCacheDiscoveryMonitoring(userId: String, userLocation: Location) {
        if (isMonitoring) return

        isMonitoring = true
        currentUserLocation = userLocation

        viewModelScope.launch {
            while (isMonitoring) {
                try {
                    scanForNearbyCaches(userId, userLocation)
                    delay(SCAN_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e("CacheDiscovery", "Error in monitoring", e)
                    _discoveryState.value = _discoveryState.value.copy(
                        error = "Greška pri skeniranju: ${e.message}"
                    )
                    delay(SCAN_INTERVAL_MS * 2)
                }
            }
        }
    }

    fun stopCacheDiscoveryMonitoring() {
        isMonitoring = false
        _discoveryState.value = CacheDiscoveryState()
    }

    private suspend fun scanForNearbyCaches(userId: String, userLocation: Location) {
        android.util.Log.d("CacheDiscovery", "🔍 Scanning for caches at lat=${userLocation.latitude}, lng=${userLocation.longitude}")

        _discoveryState.value = _discoveryState.value.copy(isScanning = true, error = null)

        try {
            // KORISTI TRENUTNU LOKACIJU iz parametra, ne staru
            val currentLocation = currentUserLocation ?: userLocation
            android.util.Log.d("CacheDiscovery", "📍 Using location: lat=${currentLocation.latitude}, lng=${currentLocation.longitude}")

            cacheRepository.getDiscoverableCachesNear(currentLocation, SEARCH_RADIUS_METERS)
                .collect { nearbyCaches ->
                    android.util.Log.d("CacheDiscovery", "📦 Received ${nearbyCaches.size} nearby caches")

                    // Filtriraj cache-ove koje korisnik može da otkrije
                    val discoverableCaches = mutableListOf<Cache>()

                    for (cache in nearbyCaches) {
                        val canDiscover = canUserDiscoverCache(cache, userId)
                        val distance = Location.calculateDistance(currentLocation, cache.location)
                        android.util.Log.d("CacheDiscovery", "🎯 Cache ${cache.id}: distance=${distance}m, canDiscover=$canDiscover")

                        if (canDiscover) {
                            discoverableCaches.add(cache)
                        }
                    }

                    android.util.Log.d("CacheDiscovery", "✅ Discoverable caches: ${discoverableCaches.size}")

                    if (discoverableCaches.isEmpty()) {
                        _discoveryState.value = _discoveryState.value.copy(
                            isScanning = false,
                            nearbyCache = null,
                            message = null
                        )
                        return@collect
                    }

                    // Pronađi najbliži cache
                    val nearestCache = discoverableCaches.minByOrNull { cache ->
                        Location.calculateDistance(currentLocation, cache.location)
                    }

                    if (nearestCache != null) {
                        val distance = Location.calculateDistance(currentLocation, nearestCache.location)

                        android.util.Log.d("CacheDiscovery", "🎯 Nearest cache: ${nearestCache.id}, distance: ${distance}m")

                        _discoveryState.value = _discoveryState.value.copy(
                            isScanning = false,
                            nearbyCache = nearestCache,
                            distanceToCache = distance,
                            canDiscover = distance <= DISCOVERY_RADIUS_METERS,
                            message = when {
                                distance <= DISCOVERY_RADIUS_METERS ->
                                    "🎯 CACHE PRONAĐEN! Možete ga otkriti!"
                                distance <= 20.0 ->
                                    "🔥 Vrlo blizu! ${distance.toInt()}m"
                                distance <= 30.0 ->
                                    "🌡️ Toplije... ${distance.toInt()}m"
                                else ->
                                    "❄️ Hladnije... ${distance.toInt()}m"
                            }
                        )

                        // Automatski pokreni discovery ako je dovoljno blizu
                        if (distance <= DISCOVERY_RADIUS_METERS && !_discoveryState.value.discoveryInProgress) {
                            android.util.Log.d("CacheDiscovery", "🚀 Initiating automatic discovery!")
                            initiateDiscovery(nearestCache)
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("CacheDiscovery", "❌ Error scanning for caches", e)
            _discoveryState.value = _discoveryState.value.copy(
                isScanning = false,
                error = "Greška pri skeniranju: ${e.message}"
            )
        }
    }

    private suspend fun canUserDiscoverCache(cache: Cache, userId: String): Boolean {
        android.util.Log.d("CacheDiscovery", "🤔 Checking if user $userId can discover cache ${cache.id}")

        // Ne može svoj cache
        if (cache.createdByUser == userId) {
            android.util.Log.d("CacheDiscovery", "❌ User created this cache")
            return false
        }

        // Cache nije aktivan
        if (cache.status != com.example.mapmyst.data.model.CacheStatus.ACTIVE) {
            android.util.Log.d("CacheDiscovery", "❌ Cache is not active: ${cache.status}")
            return false
        }

        // Cache je expired
        if (cache.expires < System.currentTimeMillis()) {
            android.util.Log.d("CacheDiscovery", "❌ Cache is expired")
            return false
        }

        // Single cache je već pronađen
        if (cache.single && cache.findCount >= 1) {
            android.util.Log.d("CacheDiscovery", "❌ Single cache already found")
            return false
        }

        // Proverava da li je user već našao
        try {
            val hasFound = cacheRepository.hasUserFoundCache(cache.id, userId)
            val alreadyFound = hasFound.getOrDefault(false)
            android.util.Log.d("CacheDiscovery", "🔍 User already found this cache: $alreadyFound")
            return !alreadyFound
        } catch (e: Exception) {
            android.util.Log.e("CacheDiscovery", "❌ Error checking if user found cache", e)
            return false
        }
    }

    private fun initiateDiscovery(cache: Cache) {
        _discoveryState.value = _discoveryState.value.copy(
            discoveryInProgress = true,
            message = "🎉 Cache pronađen! Potvrdite otkriće."
        )
    }

    fun confirmCacheDiscovery(userId: String, username: String, note: String?) {
        val currentState = _discoveryState.value
        val cache = currentState.nearbyCache ?: return

        viewModelScope.launch {
            try {
                val cacheFind = CacheFind(
                    cacheId = cache.id,
                    userId = userId,
                    username = username,
                    locationAtFind = currentUserLocation ?: cache.location,
                    pointsEarned = cache.value,
                    note = note
                )

                val result = cacheRepository.saveCacheFind(cacheFind)

                result.fold(
                    onSuccess = {
                        _discoveryState.value = _discoveryState.value.copy(
                            discoveredCache = cache,
                            pointsEarned = cache.value,
                            message = "🎉 Čestitamo! Osvojili ste ${cache.value} poena!",
                            discoveryInProgress = false
                        )

                        // Očisti nakon 5 sekundi
                        viewModelScope.launch {
                            delay(5000)
                            clearDiscovery()
                        }
                    },
                    onFailure = { exception ->
                        _discoveryState.value = _discoveryState.value.copy(
                            error = "Greška pri potvrdi: ${exception.message}",
                            discoveryInProgress = false
                        )
                    }
                )
            } catch (e: Exception) {
                _discoveryState.value = _discoveryState.value.copy(
                    error = "Neočekivana greška: ${e.message}",
                    discoveryInProgress = false
                )
            }
        }
    }

    fun cancelDiscovery() {
        _discoveryState.value = _discoveryState.value.copy(
            discoveryInProgress = false,
            message = null
        )
    }

    fun clearError() {
        _discoveryState.value = _discoveryState.value.copy(error = null)
    }

    private fun clearDiscovery() {
        _discoveryState.value = CacheDiscoveryState()
    }

    fun updateUserLocation(newLocation: Location) {
        currentUserLocation = newLocation
    }
}