package com.example.mapmyst.presentation.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.CacheCategory
import com.example.mapmyst.presentation.viewmodel.CacheViewModel
import com.example.mapmyst.presentation.viewmodel.MapViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun OSMMapMystMap(
    mapViewModel: MapViewModel,
    cacheViewModel: CacheViewModel,
    userId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapState by mapViewModel.mapState.collectAsState()
    val currentLocation by mapViewModel.currentLocation.collectAsState()
    val cacheUiState by cacheViewModel.uiState.collectAsState()

    // Inicijalizacija OSMDroid konfiguracije
    LaunchedEffect(Unit) {
        Configuration.getInstance()
            .load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "MapMyst"

        mapViewModel.startLocationUpdates(context, userId)
        mapViewModel.observeLocationEvidences(userId)
    }

    // Kreiranje MapView-a
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)

            // Postavke zoom-a
            controller.setZoom(15.0)

            // Default centar - Beograd
            val defaultCenter = GeoPoint(44.8176, 20.4633)
            controller.setCenter(defaultCenter)
        }
    }

    // Location overlay
    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
    }

    // Dodavanje overlay-a
    LaunchedEffect(mapView) {
        mapView.overlays.add(locationOverlay)
    }

    // Funkcije za cache markere
    fun getCacheIcon(category: CacheCategory): String {
        return when (category) {
            CacheCategory.EASY -> "🎯"
            CacheCategory.ADVENTURE -> "🏔️"
            CacheCategory.PUZZLE -> "🧩"
            CacheCategory.HISTORICAL -> "🏛️"
            CacheCategory.NATURE -> "🌿"
            CacheCategory.URBAN -> "🏙️"
            CacheCategory.MYSTERY -> "❓"
        }
    }

    fun getCacheTitle(cache: Cache): String {
        val icon = getCacheIcon(cache.category)
        return "$icon ${cache.category.name} (${cache.value}p)"
    }

    fun createTextCacheMarker(cache: Cache, context: Context, mapView: MapView): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(cache.location.latitude, cache.location.longitude)
        marker.title = getCacheTitle(cache)
        marker.snippet = cache.description.take(100) + if (cache.description.length > 100) "..." else ""

        // Kreiraj bitmap sa emoji ikonom
        val icon = getCacheIcon(cache.category)

        val paint = Paint().apply {
            textSize = 48f
            isAntiAlias = true
        }

        val bounds = Rect()
        paint.getTextBounds(icon, 0, icon.length, bounds)

        val bitmap = Bitmap.createBitmap(bounds.width() + 20, bounds.height() + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(icon, 10f, bounds.height() + 10f, paint)

        marker.icon = BitmapDrawable(context.resources, bitmap)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        marker.setOnMarkerClickListener { _, _ ->
            cacheViewModel.getCacheById(cache.id)
            true
        }

        return marker
    }

    // Update lokacije
    LaunchedEffect(currentLocation) {
        currentLocation?.let { latLng ->
            val geoPoint = GeoPoint(latLng.latitude, latLng.longitude)
            mapView.controller.animateTo(geoPoint)

            // Učitaj nearby cache-ove kada se lokacija promeni
            val location = com.example.mapmyst.data.model.Location(
                latitude = latLng.latitude,
                longitude = latLng.longitude
            )
            cacheViewModel.loadNearbyCaches(location)
        }
    }

    // Update cache markera
    LaunchedEffect(cacheUiState.nearbyCaches) {
        // Ukloni postojeće cache markere (zadrži location evidence markere)
        mapView.overlays.removeIf { overlay ->
            overlay is Marker && overlay.title?.let { title ->
                title.contains("🎯") || title.contains("🏔️") || title.contains("🧩") ||
                        title.contains("🏛️") || title.contains("🌿") || title.contains("🏙️") || title.contains("❓")
            } ?: false
        }

        // Dodaj nove cache markere
        cacheUiState.nearbyCaches.forEach { cache ->
            val marker = createTextCacheMarker(cache, context, mapView)
            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    // Update location evidence markers
    LaunchedEffect(mapState.locationEvidences) {
        // Ukloni postojeće evidence markere
        mapView.overlays.removeIf { overlay ->
            overlay is Marker && overlay.title == "Evidence Point"
        }

        // Dodaj nove markere za location evidence
        mapState.locationEvidences.forEach { evidence ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(evidence.location.latitude, evidence.location.longitude)
                title = "Evidence Point"
                snippet = "Session: ${evidence.sessionId.take(8)}..."
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
        }

        // Dodaj polyline za aktivnu sesiju
        val activeSessionEvidences = mapState.locationEvidences
            .filter { it.isActive }
            .sortedBy { it.timestamp }

        if (activeSessionEvidences.size > 1) {
            val polyline = Polyline().apply {
                color = android.graphics.Color.BLUE
                width = 8f
            }

            activeSessionEvidences.forEach { evidence ->
                polyline.addPoint(GeoPoint(evidence.location.latitude, evidence.location.longitude))
            }

            mapView.overlays.add(polyline)
        }

        mapView.invalidate() // Refresh mape
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        ) { view ->
            view.onResume()
        }

        //  FAB za centriranje lokacije - pozicioniran u donji levi ugao da ne koliduje
        FloatingActionButton(
            onClick = {
                currentLocation?.let { location ->
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    mapView.controller.animateTo(geoPoint)
                } ?: run {
                    // Ako nema trenutne lokacije, aktiviraj location overlay
                    locationOverlay.enableFollowLocation()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .zIndex(5f), // Manji zIndex od Create Cache FAB
            containerColor = MaterialTheme.colorScheme.secondary // Drugačija boja
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = "Centriraj na moju lokaciju"
            )
        }
    }
}