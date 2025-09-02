package com.example.mapmyst.presentation.ui.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapmyst.data.model.CacheCategory
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.presentation.viewmodel.AuthViewModel
import com.example.mapmyst.presentation.viewmodel.CacheViewModel
import com.example.mapmyst.presentation.viewmodel.MapViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCreationScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    cacheViewModel: CacheViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val creationState by cacheViewModel.creationState.collectAsState()
    val uiState by cacheViewModel.uiState.collectAsState()
    val mapState by mapViewModel.mapState.collectAsState()
    val currentLocation by mapViewModel.currentLocation.collectAsState()

    // Lokalne varijable
    val cacheLocation = creationState.location
    var locationSetAutomatically by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Ekspiranje opcije (u satima)
    val expirationOptions = listOf(
        1L to "1 sat",
        6L to "6 sati",
        12L to "12 sati",
        24L to "1 dan",
        48L to "2 dana",
        168L to "1 sedmica"
    )

    // Automatski postavi trenutnu lokaciju kada se ucita screen
    LaunchedEffect(currentLocation) {
        currentLocation?.let { latLng ->
            // Postavi lokaciju samo ako nije vec postavljena ili ako je korisnik kliknuo refresh
            if (cacheLocation == null || !locationSetAutomatically) {
                val location = Location(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                cacheViewModel.updateLocation(location)
                locationSetAutomatically = true

                // Debug log
                android.util.Log.d("CacheCreation", "ðŸŽ¯ Location auto-set: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    // Ako nema trenutne lokacije, pokusaj da je dobijes
    LaunchedEffect(Unit) {
        currentUser?.let { user ->
            // Pokreni location updates ako veÄ‡ nije pokrenuto
            mapViewModel.startLocationUpdates(context, user.id)
        }
    }

    // Navigacija kada je cache kreiran
    LaunchedEffect(uiState.cacheCreated) {
        if (uiState.cacheCreated) {
            snackbarHostState.showSnackbar(
                message = "✅ Cache je uspešno kreiran!",
                duration = SnackbarDuration.Short
            )
            // Kratka pauza da korisnik vidi poruku
            delay(1500)
            cacheViewModel.clearCacheCreated()
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kreiraj Cache",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Nazad"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Error message
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Informativna poruka o lokaciji
            if (cacheLocation != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cache ce biti kreiran na vasoj trenutnoj lokaciji",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Opis cache-a
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Opis Cache-a",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = creationState.description,
                        onValueChange = { cacheViewModel.updateDescription(it) },
                        label = { Text("Detaljni opis cache-a") },
                        placeholder = { Text("Opisite sta se moze pronaci na ovoj lokaciji...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        supportingText = {
                            Text("${creationState.description.length}/500 karaktera (minimum 10)")
                        },
                        isError = creationState.description.isNotEmpty() && creationState.description.length < 10
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kategorija
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kategorija",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = "${cacheViewModel.getCacheIcon(creationState.category)} ${cacheViewModel.getCategoryText(creationState.category)}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Izaberi kategoriju") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            CacheCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${cacheViewModel.getCacheIcon(category)} ${cacheViewModel.getCategoryText(category)}")
                                    },
                                    onClick = {
                                        cacheViewModel.updateCategory(category)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tezina i teren
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tezina i teren",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Tezina
                    Text(
                        text = "Tezina: ${creationState.difficulty}/5 - ${cacheViewModel.getDifficultyText(creationState.difficulty)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = creationState.difficulty.toFloat(),
                        onValueChange = { cacheViewModel.updateDifficulty(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Teren
                    Text(
                        text = "Teren: ${creationState.terrain}/5 - ${cacheViewModel.getTerrainText(creationState.terrain)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = creationState.terrain.toFloat(),
                        onValueChange = { cacheViewModel.updateTerrain(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vrednost i opcije
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vrednost i opcije",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Vrednost
                    Text(
                        text = "Vrednost: ${creationState.value} poena",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = creationState.value.toFloat(),
                        onValueChange = { cacheViewModel.updateValue(it.toInt()) },
                        valueRange = 1f..100f,
                        steps = 98
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Single cache opcija
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = creationState.single,
                            onCheckedChange = { cacheViewModel.updateSingle(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pojedinacni cache",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Moze ga pronaci samo jedan korisnik",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vreme ekspiriranja
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vreme vazenja cache-a",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Izaberi koliko dugo cache ostaje aktivan",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    expirationOptions.forEach { (hours, label) ->
                        val milliseconds = hours * 60 * 60 * 1000L
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = creationState.expiresIn == milliseconds,
                                    onClick = { cacheViewModel.updateExpiresIn(milliseconds) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = creationState.expiresIn == milliseconds,
                                onClick = { cacheViewModel.updateExpiresIn(milliseconds) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lokacija sekcija -  boljim prikaz
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lokacija",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Refresh dugme za lokaciju
                        IconButton(
                            onClick = {
                                currentUser?.let { user ->
                                    locationSetAutomatically = false
                                    mapViewModel.startLocationUpdates(context, user.id)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Osvezi lokaciju",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (cacheLocation != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            if (cacheLocation != null) {
                                Text(
                                    text = "Å irina: ${String.format("%.6f", cacheLocation.latitude)}",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "DuÅ¾ina: ${String.format("%.6f", cacheLocation.longitude)}",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = " Lokacija postavljena",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "Cekamo GPS lokaciju...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Molimo sacekajte",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // INFORMATIVNA PORUKA
                    if (cacheLocation == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cache ce biti kreiran na vasoj trenutnoj GPS poziciji",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.ui.text.TextStyle(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Kreiranje cache-a
            Button(
                onClick = {
                    currentUser?.let { user ->
                        cacheViewModel.createCache(user.id)
                    }
                },
                enabled = creationState.isValid && !uiState.isCreatingCache && cacheLocation != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCreatingCache) {
                    Text("Kreira se...")
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kreiraj Cache", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            //  poruka o validaciji
            if (!creationState.isValid || cacheLocation == null) {
                Spacer(modifier = Modifier.height(8.dp))
                val missingFields = mutableListOf<String>()

                if (creationState.description.length < 10) {
                    missingFields.add("opis (min. 10 karaktera)")
                }
                if (cacheLocation == null) {
                    missingFields.add("GPS lokacija")
                }

                Text(
                    text = "Potrebno: ${missingFields.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}