package com.example.mapmyst.data.repository

import com.example.mapmyst.data.model.User
import com.example.mapmyst.domain.repository.LeaderboardRepository
import com.example.mapmyst.domain.repository.LeaderboardTimeFrame
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaderboardRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : LeaderboardRepository {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val CACHE_FINDS_COLLECTION = "cacheFinds"
    }

    override fun getTopUsers(limit: Int): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection(USERS_COLLECTION)
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val users = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(User::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        android.util.Log.e("LeaderboardRepo", "Error parsing user: ${e.message}")
                        null
                    }
                } ?: emptyList()

                trySend(users)
            }

        awaitClose { listener.remove() }
    }

    override fun getUserRank(userId: String): Flow<Int?> = callbackFlow {
        val listener = firestore.collection(USERS_COLLECTION)
            .orderBy("score", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                val rank = users.indexOfFirst { it.id == userId }
                trySend(if (rank == -1) null else rank + 1)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun refreshLeaderboard(): Result<Unit> {
        return try {
            // Force refresh by getting fresh data
            firestore.collection(USERS_COLLECTION)
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("LeaderboardRepo", "Error refreshing leaderboard", e)
            Result.failure(e)
        }
    }

    override fun searchUsers(query: String): Flow<List<User>> = callbackFlow {
        val searchQuery = query.lowercase()

        val listener = firestore.collection(USERS_COLLECTION)
            .orderBy("score", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val users = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(User::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }?.filter { user ->
                    user.username.lowercase().contains(searchQuery) ||
                            user.firstName.lowercase().contains(searchQuery) ||
                            user.lastName.lowercase().contains(searchQuery) ||
                            "${user.firstName} ${user.lastName}".lowercase().contains(searchQuery)
                } ?: emptyList()

                trySend(users)
            }

        awaitClose { listener.remove() }
    }

    override fun getTopUsersByTimeFrame(
        timeFrame: LeaderboardTimeFrame,
        limit: Int
    ): Flow<List<User>> = callbackFlow {
        when (timeFrame) {
            LeaderboardTimeFrame.ALL_TIME -> {
                // Use the regular getTopUsers for all-time
                val listener = firestore.collection(USERS_COLLECTION)
                    .orderBy("score", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        val users = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(User::class.java)?.copy(id = doc.id)
                        } ?: emptyList()

                        trySend(users)
                    }

                awaitClose { listener.remove() }
            }
            else -> {
                // For time-based leaderboards, we'd need to calculate based on cache finds
                // This is a simplified implementation - you might want to add timestamp fields
                val timeLimit = getTimeLimit(timeFrame)

                // For now, return all-time leaderboard
                // In a full implementation, you'd filter by timestamp
                val listener = firestore.collection(USERS_COLLECTION)
                    .orderBy("score", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        val users = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(User::class.java)?.copy(id = doc.id)
                        } ?: emptyList()

                        trySend(users)
                    }

                awaitClose { listener.remove() }
            }
        }
    }

    private fun getTimeLimit(timeFrame: LeaderboardTimeFrame): Long {
        val calendar = Calendar.getInstance()
        return when (timeFrame) {
            LeaderboardTimeFrame.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            LeaderboardTimeFrame.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            LeaderboardTimeFrame.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            LeaderboardTimeFrame.ALL_TIME -> 0L
        }
    }
}