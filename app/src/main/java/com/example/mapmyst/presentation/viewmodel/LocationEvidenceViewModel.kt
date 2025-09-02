package com.example.mapmyst.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.data.model.LocationEvidence
import com.example.mapmyst.data.model.LocationSession
import com.example.mapmyst.domain.repository.LocationEvidenceRepository
import com.example.mapmyst.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationEvidenceViewModel @Inject constructor(
    private val locationEvidenceRepository: LocationEvidenceRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _currentSession = MutableStateFlow<LocationSession?>(null)
    val currentSession: StateFlow<LocationSession?> = _currentSession.asStateFlow()

    private val _locationEvidences = MutableStateFlow<List<LocationEvidence>>(emptyList())
    val locationEvidences: StateFlow<List<LocationEvidence>> = _locationEvidences.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private val _userSessions = MutableStateFlow<List<LocationSession>>(emptyList())
    val userSessions: StateFlow<List<LocationSession>> = _userSessions.asStateFlow()

    private var locationTrackingJob: Job? = null
    private var currentUserId: String = ""

    companion object {
        private const val TAG = "LocationEvidenceVM"
    }

    fun startLocationTracking(context: Context, userId: String) {
        if (_isTrackingActive.value) {
            Log.w(TAG, "Location tracking already active")
            return
        }

        currentUserId = userId
        viewModelScope.launch {
            try {
                // Pokreće novu sesiju ili dobija postojeću aktivnu
                val sessionResult = locationEvidenceRepository.startLocationSession(userId)
                val session = sessionResult.getOrThrow()
                _currentSession.value = session

                _isTrackingActive.value = true

                // Pokreće praćenje lokacije
                locationTrackingJob = viewModelScope.launch {
                    locationRepository.getCurrentLocation(context)
                        .catch { exception ->
                            Log.e(TAG, "Error in location tracking", exception)
                            stopLocationTracking()
                        }
                        .collect { location ->
                            saveLocationEvidence(location, session.id, userId)
                        }
                }

                Log.d(TAG, "Location tracking started for session: ${session.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting location tracking", e)
                _isTrackingActive.value = false
            }
        }
    }

    fun stopLocationTracking() {
        viewModelScope.launch {
            try {
                locationTrackingJob?.cancel()
                locationTrackingJob = null

                _currentSession.value?.let { session ->
                    locationEvidenceRepository.endLocationSession(session.id)
                        .onSuccess { updatedSession ->
                            _currentSession.value = updatedSession
                            Log.d(TAG, "Location session ended: ${session.id}")
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Error ending location session", e)
                        }
                }

                _isTrackingActive.value = false
                Log.d(TAG, "Location tracking stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping location tracking", e)
            }
        }
    }

    private suspend fun saveLocationEvidence(location: Location, sessionId: String, userId: String) {
        try {
            val evidence = LocationEvidence(
                userId = userId,
                location = location,
                sessionId = sessionId,
                accuracy = location.accuracy,
                speed = 0.0 // Može se kalkulisati na osnovu prethodnih lokacija
            )

            locationEvidenceRepository.saveLocationEvidence(evidence)
                .onSuccess {
                    Log.d(TAG, "Location evidence saved for session: $sessionId")
                }
                .onFailure { e ->
                    Log.e(TAG, "Error saving location evidence", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating location evidence", e)
        }
    }

    fun loadUserSessions(userId: String) {
        viewModelScope.launch {
            locationEvidenceRepository.getUserSessions(userId)
                .onSuccess { sessions ->
                    _userSessions.value = sessions
                    Log.d(TAG, "Loaded ${sessions.size} sessions for user: $userId")
                }
                .onFailure { e ->
                    Log.e(TAG, "Error loading user sessions", e)
                }
        }
    }

    fun loadLocationEvidenceForSession(sessionId: String) {
        viewModelScope.launch {
            locationEvidenceRepository.getLocationEvidenceForSession(sessionId)
                .onSuccess { evidences ->
                    _locationEvidences.value = evidences
                    Log.d(TAG, "Loaded ${evidences.size} evidences for session: $sessionId")
                }
                .onFailure { e ->
                    Log.e(TAG, "Error loading location evidence for session", e)
                }
        }
    }

    fun checkActiveSession(userId: String) {
        viewModelScope.launch {
            locationEvidenceRepository.getActiveSession(userId)
                .onSuccess { session ->
                    _currentSession.value = session
                    _isTrackingActive.value = session != null
                    if (session != null) {
                        Log.d(TAG, "Found active session: ${session.id}")
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Error checking active session", e)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTrackingJob?.cancel()
    }
}