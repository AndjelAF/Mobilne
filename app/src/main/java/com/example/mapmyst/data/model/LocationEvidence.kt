package com.example.mapmyst.data.model

data class LocationEvidence(
    val id: String = "",
    val userId: String = "",
    val location: Location,
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val sessionId: String = "", // ID sesije pokretanja
    val accuracy: Double = 0.0,
    val speed: Double = 0.0 // brzina kretanja u m/s
)