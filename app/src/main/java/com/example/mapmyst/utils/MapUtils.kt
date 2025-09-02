package com.example.mapmyst.util

import com.example.mapmyst.data.model.Location
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.util.GeoPoint

object MapUtils {

    fun locationToGeoPoint(location: Location): GeoPoint {
        return GeoPoint(location.latitude, location.longitude)
    }

    fun geoPointToLocation(geoPoint: GeoPoint, timestamp: Long = System.currentTimeMillis()): Location {
        return Location(
            latitude = geoPoint.latitude,
            longitude = geoPoint.longitude,
            timestamp = timestamp
        )
    }

    fun latLngToGeoPoint(latLng: LatLng): GeoPoint {
        return GeoPoint(latLng.latitude, latLng.longitude)
    }

    fun geoPointToLatLng(geoPoint: GeoPoint): LatLng {
        return LatLng(geoPoint.latitude, geoPoint.longitude)
    }
}