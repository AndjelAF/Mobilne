package com.example.mapmyst.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.domain.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Implementacija LocationRepository interfejsa koja koristi Google Play Services za dobavljanje lokacije uređaja.
 * Ova klasa upravlja lokacijskim podacima, zahtevima za ažuriranje lokacije i kalkulacijama razdaljina.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor() : LocationRepository {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    override fun getCurrentLocation(context: Context): Flow<Location> = callbackFlow {
        if (!isLocationPermissionGranted(context)) {
            close(Exception("Location permission not granted"))
            return@callbackFlow
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val appLocation = Location(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        accuracy = location.accuracy.toDouble(),
                        timestamp = location.time
                    )
                    trySend(appLocation)
                }
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10 sekundi
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000) // 5 sekundi
            .setMaxUpdateDelayMillis(15000) // 15 sekundi
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )

            // Dobavljamo trenutnu lokaciju odmah, ako je dostupna
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                location?.let {
                    val appLocation = Location(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        altitude = it.altitude,
                        accuracy = it.accuracy.toDouble(),
                        timestamp = it.time
                    )
                    trySend(appLocation)
                }
            }
        } else {
            close(Exception("Location permission not granted"))
        }

        awaitClose {
            locationCallback?.let { callback ->
                fusedLocationClient?.removeLocationUpdates(callback)
            }
        }
    }

    override suspend fun requestLocationUpdates(context: Context): Result<Unit> {
        return try {
            if (!isLocationPermissionGranted(context)) {
                return Result.failure(Exception("Location permission not granted"))
            }

            fusedLocationClient = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context)

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(15000)
                .build()

            locationCallback = locationCallback ?: object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Callback je već postavljen u getCurrentLocation
                }
            }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Koristimo suspendCoroutine da pretvorimo callback u korutinu
                suspendCoroutine<Unit> { continuation ->
                    fusedLocationClient?.requestLocationUpdates(
                        locationRequest,
                        locationCallback as LocationCallback,
                        Looper.getMainLooper()
                    )?.addOnSuccessListener {
                        continuation.resume(Unit)
                    }?.addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
                }

                Result.success(Unit)
            } else {
                Result.failure(Exception("Location permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to request location updates: ${e.message}"))
        }
    }

    override suspend fun stopLocationUpdates(): Result<Unit> {
        return try {
            locationCallback?.let { callback ->
                fusedLocationClient?.removeLocationUpdates(callback)
            }
            fusedLocationClient = null
            locationCallback = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to stop location updates: ${e.message}"))
        }
    }

    override fun isLocationPermissionGranted(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun calculateDistanceToCache(currentLocation: Location, cacheLocation: Location): Double {
        return Location.calculateDistance(currentLocation, cacheLocation)
    }

    override suspend fun isCacheNearby(currentLocation: Location, cacheLocation: Location, radiusInMeters: Double): Boolean {
        val distance = calculateDistanceToCache(currentLocation, cacheLocation)
        return distance <= radiusInMeters
    }
}