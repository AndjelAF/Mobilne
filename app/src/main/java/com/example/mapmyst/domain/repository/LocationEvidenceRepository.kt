package com.example.mapmyst.domain.repository

import com.example.mapmyst.data.model.LocationEvidence
import com.example.mapmyst.data.model.LocationSession
import kotlinx.coroutines.flow.Flow

interface LocationEvidenceRepository {
    // Evidence management
    suspend fun saveLocationEvidence(evidence: LocationEvidence): Result<Unit>
    suspend fun getLocationEvidenceForUser(userId: String): Result<List<LocationEvidence>>
    suspend fun getLocationEvidenceForSession(sessionId: String): Result<List<LocationEvidence>>
    suspend fun deleteLocationEvidence(evidenceId: String): Result<Unit>

    // Session management
    suspend fun startLocationSession(userId: String): Result<LocationSession>
    suspend fun endLocationSession(sessionId: String): Result<LocationSession>
    suspend fun updateLocationSession(session: LocationSession): Result<Unit>
    suspend fun getActiveSession(userId: String): Result<LocationSession?>
    suspend fun getUserSessions(userId: String): Result<List<LocationSession>>

    // Real-time tracking
    fun getLocationEvidenceFlow(userId: String): Flow<List<LocationEvidence>>
    suspend fun isLocationTrackingActive(userId: String): Boolean
}