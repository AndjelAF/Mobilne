package com.example.mapmyst.data.model

data class LocationSession(
    val id: String = "",
    val userId: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val isActive: Boolean = true,
    val totalDistance: Double = 0.0, // ukupna pređena distanca u metrima
    val averageSpeed: Double = 0.0, // prosečna brzina u m/s
    val maxSpeed: Double = 0.0, // maksimalna brzina u m/s
    val locationCount: Int = 0 // broj zabeleženih lokacija
)