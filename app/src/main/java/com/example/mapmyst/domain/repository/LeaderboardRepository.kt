package com.example.mapmyst.domain.repository

import com.example.mapmyst.data.model.User
import kotlinx.coroutines.flow.Flow

interface LeaderboardRepository {
    fun getTopUsers(limit: Int = 50): Flow<List<User>>
    fun getUserRank(userId: String): Flow<Int?>
    suspend fun refreshLeaderboard(): Result<Unit>
    fun searchUsers(query: String): Flow<List<User>>
    fun getTopUsersByTimeFrame(
        timeFrame: LeaderboardTimeFrame,
        limit: Int = 50
    ): Flow<List<User>>
}

enum class LeaderboardTimeFrame {
    ALL_TIME,
    THIS_MONTH,
    THIS_WEEK,
    TODAY
}