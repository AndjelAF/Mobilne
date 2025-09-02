package com.example.mapmyst.domain.repository

import android.content.Context
import com.example.mapmyst.data.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getCurrentLocation(context: Context): Flow<Location>
    suspend fun requestLocationUpdates(context: Context): Result<Unit>
    suspend fun stopLocationUpdates(): Result<Unit>
    fun isLocationPermissionGranted(context: Context): Boolean
    suspend fun calculateDistanceToCache(currentLocation: Location, cacheLocation: Location): Double
    suspend fun isCacheNearby(currentLocation: Location, cacheLocation: Location, radiusInMeters: Double): Boolean
}