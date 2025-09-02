package com.example.mapmyst.data.model

import com.google.android.gms.maps.model.LatLng

data class MapState(
    val currentLocation: LatLng? = null,
    val locationEvidences: List<LocationEvidence> = emptyList(),
    val caches: List<Cache> = emptyList(),
    val isLocationPermissionGranted: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    val zoomLevel: Float = 15f
)