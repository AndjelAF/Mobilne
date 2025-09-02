package com.example.mapmyst.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.MapState
import com.example.mapmyst.domain.repository.LocationEvidenceRepository
import com.example.mapmyst.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Data class za lokaciju kompatibilnu sa oba sistema
data class MapLocation(
    val latitude: Double,
    val longitude: Double
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationEvidenceRepository: LocationEvidenceRepository
) : ViewModel() {

    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    private val _currentLocation = MutableStateFlow<MapLocation?>(null)
    val currentLocation: StateFlow<MapLocation?> = _currentLocation.asStateFlow()

    companion object {
        private const val TAG = "MapViewModel"
    }

    fun startLocationUpdates(context: Context, userId: String) {
        viewModelScope.launch {
            try {
                if (!locationRepository.isLocationPermissionGranted(context)) {
                    Log.w(TAG, "Location permission not granted")
                    return@launch
                }

                locationRepository.getCurrentLocation(context)
                    .catch { exception ->
                        Log.e(TAG, "Error getting location", exception)
                    }
                    .collect { location ->
                        val mapLocation = MapLocation(location.latitude, location.longitude)
                        _currentLocation.value = mapLocation

                        _mapState.value = _mapState.value.copy(
                            currentLocation = com.google.android.gms.maps.model.LatLng(
                                location.latitude,
                                location.longitude
                            )
                        )

                        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting location updates", e)
            }
        }
    }

    fun loadLocationEvidenceForUser(userId: String) {
        viewModelScope.launch {
            locationEvidenceRepository.getLocationEvidenceForUser(userId)
                .onSuccess { evidences ->
                    _mapState.value = _mapState.value.copy(
                        locationEvidences = evidences
                    )
                    Log.d(TAG, "Loaded ${evidences.size} location evidences")
                }
                .onFailure { exception ->
                    Log.e(TAG, "Error loading location evidences", exception)
                }
        }
    }

    fun observeLocationEvidences(userId: String) {
        viewModelScope.launch {
            locationEvidenceRepository.getLocationEvidenceFlow(userId)
                .catch { exception ->
                    Log.e(TAG, "Error observing location evidences", exception)
                }
                .collect { evidences ->
                    _mapState.value = _mapState.value.copy(
                        locationEvidences = evidences
                    )
                    Log.d(TAG, "Real-time update: ${evidences.size} location evidences")
                }
        }
    }

    fun updatePermissionStatus(isGranted: Boolean) {
        _mapState.value = _mapState.value.copy(
            isLocationPermissionGranted = isGranted
        )
    }
}