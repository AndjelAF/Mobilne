package com.example.mapmyst.data.repository

import android.util.Log
import com.example.mapmyst.data.firebase.FirebaseModule
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.data.model.LocationEvidence
import com.example.mapmyst.data.model.LocationSession
import com.example.mapmyst.domain.repository.LocationEvidenceRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.channels.awaitClose

@Singleton
class LocationEvidenceRepositoryImpl @Inject constructor() : LocationEvidenceRepository {

    private val firestore = FirebaseModule.firestore
    private val evidenceCollection = firestore.collection("location_evidence")
    private val sessionsCollection = firestore.collection("location_sessions")

    companion object {
        private const val TAG = "LocationEvidenceRepo"
    }

    override suspend fun saveLocationEvidence(evidence: LocationEvidence): Result<Unit> {
        return try {
            val evidenceId = if (evidence.id.isEmpty()) {
                evidenceCollection.document().id
            } else {
                evidence.id
            }

            val evidenceWithId = evidence.copy(id = evidenceId)
            evidenceCollection.document(evidenceId).set(evidenceWithId).await()

            Log.d(TAG, "Location evidence saved: $evidenceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location evidence", e)
            Result.failure(e)
        }
    }

    override suspend fun getLocationEvidenceForUser(userId: String): Result<List<LocationEvidence>> {
        return try {
            val snapshot = evidenceCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val evidences = snapshot.documents.mapNotNull { doc ->
                doc.toObject(LocationEvidence::class.java)
            }

            Result.success(evidences)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user location evidence", e)
            Result.failure(e)
        }
    }

    override suspend fun getLocationEvidenceForSession(sessionId: String): Result<List<LocationEvidence>> {
        return try {
            val snapshot = evidenceCollection
                .whereEqualTo("sessionId", sessionId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()

            val evidences = snapshot.documents.mapNotNull { doc ->
                doc.toObject(LocationEvidence::class.java)
            }

            Result.success(evidences)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching session location evidence", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteLocationEvidence(evidenceId: String): Result<Unit> {
        return try {
            evidenceCollection.document(evidenceId).delete().await()
            Log.d(TAG, "Location evidence deleted: $evidenceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting location evidence", e)
            Result.failure(e)
        }
    }

    override suspend fun startLocationSession(userId: String): Result<LocationSession> {
        return try {
            // Proverava da li već postoji aktivna sesija
            val activeSession = getActiveSession(userId).getOrNull()
            if (activeSession != null) {
                return Result.success(activeSession)
            }

            val sessionId = sessionsCollection.document().id
            val session = LocationSession(
                id = sessionId,
                userId = userId,
                startTime = System.currentTimeMillis(),
                isActive = true
            )

            sessionsCollection.document(sessionId).set(session).await()
            Log.d(TAG, "Location session started: $sessionId")
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location session", e)
            Result.failure(e)
        }
    }

    override suspend fun endLocationSession(sessionId: String): Result<LocationSession> {
        return try {
            val sessionDoc = sessionsCollection.document(sessionId)
            val sessionSnapshot = sessionDoc.get().await()
            val session = sessionSnapshot.toObject(LocationSession::class.java)
                ?: return Result.failure(Exception("Session not found"))

            // Kalkuliše statistike sesije
            val evidenceResult = getLocationEvidenceForSession(sessionId)
            val evidences = evidenceResult.getOrElse { emptyList() }

            val totalDistance = calculateTotalDistance(evidences)
            val averageSpeed = calculateAverageSpeed(evidences)
            val maxSpeed = evidences.maxByOrNull { it.speed }?.speed ?: 0.0

            val updatedSession = session.copy(
                endTime = System.currentTimeMillis(),
                isActive = false,
                totalDistance = totalDistance,
                averageSpeed = averageSpeed,
                maxSpeed = maxSpeed,
                locationCount = evidences.size
            )

            sessionDoc.set(updatedSession).await()
            Log.d(TAG, "Location session ended: $sessionId")
            Result.success(updatedSession)
        } catch (e: Exception) {
            Log.e(TAG, "Error ending location session", e)
            Result.failure(e)
        }
    }

    override suspend fun updateLocationSession(session: LocationSession): Result<Unit> {
        return try {
            sessionsCollection.document(session.id).set(session).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location session", e)
            Result.failure(e)
        }
    }

    override suspend fun getActiveSession(userId: String): Result<LocationSession?> {
        return try {
            val snapshot = sessionsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()

            val session = snapshot.documents.firstOrNull()?.toObject(LocationSession::class.java)
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active session", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserSessions(userId: String): Result<List<LocationSession>> {
        return try {
            val snapshot = sessionsCollection
                .whereEqualTo("userId", userId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .await()

            val sessions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(LocationSession::class.java)
            }

            Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user sessions", e)
            Result.failure(e)
        }
    }

    override fun getLocationEvidenceFlow(userId: String): Flow<List<LocationEvidence>> = callbackFlow {
        val listener = evidenceCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("isActive", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to location evidence changes", error)
                    return@addSnapshotListener
                }

                val evidences = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(LocationEvidence::class.java)
                } ?: emptyList()

                trySend(evidences)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun isLocationTrackingActive(userId: String): Boolean {
        return try {
            val activeSession = getActiveSession(userId).getOrNull()
            activeSession != null
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateTotalDistance(evidences: List<LocationEvidence>): Double {
        if (evidences.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 1 until evidences.size) {
            val distance = Location.calculateDistance(
                evidences[i-1].location,
                evidences[i].location
            )
            totalDistance += distance
        }
        return totalDistance
    }

    private fun calculateAverageSpeed(evidences: List<LocationEvidence>): Double {
        if (evidences.isEmpty()) return 0.0

        val speeds = evidences.mapNotNull { evidence ->
            if (evidence.speed > 0) evidence.speed else null
        }

        return if (speeds.isNotEmpty()) {
            speeds.average()
        } else {
            0.0
        }
    }
}

