package com.example.mapmyst.data.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        // Samo proverava ako nisu default vrednosti
        if (latitude != 0.0 || longitude != 0.0) {
            require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
            require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        }
    }

    companion object {
        private const val EARTH_RADIUS = 6371e3 // Earth's radius in meters

        fun calculateDistance(location1: Location, location2: Location): Double {
            val lat1 = location1.latitude.toRadians()
            val lat2 = location2.latitude.toRadians()
            val deltaLat = (location2.latitude - location1.latitude).toRadians()
            val deltaLon = (location2.longitude - location1.longitude).toRadians()

            val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                    cos(lat1) * cos(lat2) *
                    sin(deltaLon / 2) * sin(deltaLon / 2)

            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return EARTH_RADIUS * c
        }

        fun calculateBearing(from: Location, to: Location): Double {
            val startLat = from.latitude.toRadians()
            val startLng = from.longitude.toRadians()
            val endLat = to.latitude.toRadians()
            val endLng = to.longitude.toRadians()

            val dLng = endLng - startLng

            val y = sin(dLng) * cos(endLat)
            val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)

            var bearing = atan2(y, x)
            bearing = (bearing * 180 / PI + 360) % 360

            return bearing
        }

        private fun Double.toRadians(): Double = this * PI / 180
    }
}