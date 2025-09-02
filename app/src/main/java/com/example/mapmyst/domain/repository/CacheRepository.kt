package com.example.mapmyst.domain.repository

import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.CacheCategory
import com.example.mapmyst.data.model.CacheFind
import com.example.mapmyst.data.model.Location
import kotlinx.coroutines.flow.Flow

interface CacheRepository {
    suspend fun createCache(cache: Cache): Result<Cache>
    suspend fun getCacheById(cacheId: String): Result<Cache>
    suspend fun updateCache(cache: Cache): Result<Cache>
    suspend fun deleteCache(cacheId: String): Result<Unit>
    suspend fun markCacheAsFound(cacheId: String, userId: String): Result<Unit>
    suspend fun updateCacheStatus(cacheId: String, newStatus: String): Result<Unit>
    suspend fun saveCacheFind(cacheFind: CacheFind): Result<Unit>
    suspend fun getCacheFinds(userId: String): Flow<List<CacheFind>>
    suspend fun hasUserFoundCache(cacheId: String, userId: String): Result<Boolean>
    suspend fun getDiscoverableCachesNear(location: Location, radiusInMeters: Double): Flow<List<Cache>>
    fun getNearbyActiveCaches(location: Location, radiusInMeters: Double): Flow<List<Cache>>
    fun getAllCachesByUser(userId: String): Flow<List<Cache>>
    fun getFoundCachesByUser(userId: String): Flow<List<Cache>>
    fun getCreatedCachesByUser(userId: String): Flow<List<Cache>>
    fun getCachesByCategory(category: CacheCategory): Flow<List<Cache>>
    fun getCachesByDifficulty(difficulty: Int): Flow<List<Cache>>
    fun getCachesByTerrain(terrain: Int): Flow<List<Cache>>
    fun getExpiredCaches(): Flow<List<Cache>>
    fun searchCachesByTags(tags: List<String>): Flow<List<Cache>>
}