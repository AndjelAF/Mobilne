package com.example.mapmyst.utils

import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.Location

object LocationValidator {
    private const val MIN_DISTANCE_BETWEEN_CACHES = 50 // metri

    fun isValidLocation(newLocation: Location, existingCaches: List<Cache>): Boolean {
        return existingCaches.none { existingCache ->
            Location.calculateDistance(newLocation, existingCache.location) < MIN_DISTANCE_BETWEEN_CACHES
        }
    }
}