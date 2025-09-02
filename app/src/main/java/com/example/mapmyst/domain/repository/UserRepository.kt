package com.example.mapmyst.domain.repository

import com.example.mapmyst.data.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun registerUser(user: User): Result<User>
    suspend fun loginUser(email: String, password: String): Result<User>
    suspend fun loginUserWithUsername(username: String, password: String): Result<User>
    suspend fun getUserById(userId: String): Result<User>
    suspend fun updateUser(user: User): Result<User>
    suspend fun updateUserScore(userId: String, newScore: Int): Result<Unit>
    suspend fun addFoundCache(userId: String, cacheId: String): Result<Unit>
    suspend fun addCreatedCache(userId: String, cacheId: String): Result<Unit>
    suspend fun removeCreatedCache(userId: String, cacheId: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    fun getCurrentUser(): Flow<User?>
    fun getUsersWithTopScores(limit: Int): Flow<List<User>>
}