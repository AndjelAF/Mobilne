package com.example.mapmyst.presentation.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.presentation.ui.components.CacheDiscoveryOverlay
import com.example.mapmyst.presentation.ui.components.OSMMapMystMap
import com.example.mapmyst.presentation.viewmodel.AuthViewModel
import com.example.mapmyst.presentation.viewmodel.CacheDiscoveryViewModel
import com.example.mapmyst.presentation.viewmodel.CacheViewModel
import com.example.mapmyst.presentation.viewmodel.LocationEvidenceViewModel
import com.example.mapmyst.presentation.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onCreateCache: () -> Unit,
    authViewModel: AuthViewModel,
    locationEvidenceViewModel: LocationEvidenceViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel(),
    cacheViewModel: CacheViewModel = hiltViewModel(),
    discoveryViewModel: CacheDiscoveryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val isTrackingActive by locationEvidenceViewModel.isTrackingActive.collectAsState()
    val currentSession by locationEvidenceViewModel.currentSession.collectAsState()
    val mapState by mapViewModel.mapState.collectAsState()
    val cacheUiState by cacheViewModel.uiState.collectAsState()
    val discoveryState by discoveryViewModel.discoveryState.collectAsState()

    // Provera aktivne sesije kada se učita ekran
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            locationEvidenceViewModel.checkActiveSession(user.id)
            // Učitaj nearby cache-ove ako imamo lokaciju
            mapState.currentLocation?.let { latLng ->
                val location = Location(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                cacheViewModel.loadNearbyCaches(location)
            }
        }
    }

    // Učitaj cache-ove kada se promeni lokacija
    LaunchedEffect(mapState.currentLocation) {
        mapState.currentLocation?.let { latLng ->
            currentUser?.let { user ->
                val location = Location(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                cacheViewModel.loadNearbyCaches(location)
            }
        }
    }

    // 1. POKRETANJE DISCOVERY MONITORING-A
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            android.util.Log.d("HomeScreen", "🚀 Starting discovery for user: ${user.id}")
            // Pokreni monitoring bez čekanja lokacije
            discoveryViewModel.startCacheDiscoveryMonitoring(user.id, Location()) // Prazan Location
        }
    }

    // 2. AŽURIRANJE LOKACIJE KADA SE PROMENI
    LaunchedEffect(mapState.currentLocation) {
        mapState.currentLocation?.let { latLng ->
            val location = Location(
                latitude = latLng.latitude,
                longitude = latLng.longitude
            )
            android.util.Log.d("HomeScreen", "📍 Location updated: lat=${latLng.latitude}, lng=${latLng.longitude}")
            discoveryViewModel.updateUserLocation(location)

            //  Restartaj monitoring sa novom lokacijom
            currentUser?.let { user ->
                android.util.Log.d("HomeScreen", "🔄 Restarting discovery with new location")
                discoveryViewModel.stopCacheDiscoveryMonitoring()
                discoveryViewModel.startCacheDiscoveryMonitoring(user.id, location)
            }
        }
    }

    // 3. DEBUG:  da li se discovery state menja
    LaunchedEffect(discoveryState) {
        android.util.Log.d("HomeScreen", "🎯 Discovery state changed: isScanning=${discoveryState.isScanning}, message=${discoveryState.message}, nearbyCache=${discoveryState.nearbyCache?.id}")
    }

    // Koristimo Box umesto Scaffold da izbegnemo konflikt sa FAB
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Kompaktni header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MapMyst",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = {
                onLogout()
            }) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "Odjavi se",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Kompaktni tracking status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isTrackingActive) Icons.Default.LocationOn else Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isTrackingActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isTrackingActive) "Praćenje aktivno" else "Praćenje neaktivno",
                        fontSize = 14.sp,
                        color = if (isTrackingActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentSession != null && isTrackingActive) {
                        Text(
                            text = "Sesija: ${currentSession!!.id.take(8)}...",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    currentUser?.let { user ->
                        if (isTrackingActive) {
                            locationEvidenceViewModel.stopLocationTracking()
                        } else {
                            locationEvidenceViewModel.startLocationTracking(context, user.id)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTrackingActive)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (isTrackingActive) "Zaustavi" else "Pokreni",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mapa sa relativnim pozicioniranjem FAB dugmeta
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            // Box za relativno pozicioniranje mape i FAB dugmeta
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Mapa
                if (currentUser != null) {
                    OSMMapMystMap(
                        mapViewModel = mapViewModel,
                        cacheViewModel = cacheViewModel,
                        userId = currentUser!!.id,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Učitava se mapa...",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Discovery overlay preko mape
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CacheDiscoveryOverlay(
                        discoveryState = discoveryState,
                        showDebugInfo = false, // POSTAVITI NA true za debug
                        onConfirmDiscovery = { note ->
                            currentUser?.let { user ->
                                discoveryViewModel.confirmCacheDiscovery(
                                    userId = user.id,
                                    username = user.username,
                                    note = note
                                )
                            }
                        },
                        onCancelDiscovery = {
                            discoveryViewModel.cancelDiscovery()
                        }
                    )
                }

                // FAB dugme za kreiranje cache-a - pozicionirano preko mape
                FloatingActionButton(
                    onClick = {
                        // Debug log
                        android.util.Log.d("HomeScreen", "🎯 FAB Create Cache clicked!")
                        onCreateCache()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .zIndex(10f), // Osiguraj da bude iznad mape
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Kreiraj Cache"
                    )
                }

            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats u jednom redu
        Text(
            text = buildString {
                append("Location Points: ${mapState.locationEvidences.size}")
                append(" • ")
                append("Nearby Caches: ${cacheUiState.nearbyCaches.size}")
                append(" • ")
                append("Active Tracking: ${if (isTrackingActive) "ON" else "OFF"}")
            },
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}