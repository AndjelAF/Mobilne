package com.example.mapmyst.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mapmyst.data.model.CacheCategory
import com.example.mapmyst.presentation.viewmodel.CacheDiscoveryState

@Composable
fun CacheDiscoveryOverlay(
    discoveryState: CacheDiscoveryState,
    onConfirmDiscovery: (String?) -> Unit,
    onCancelDiscovery: () -> Unit,
    showDebugInfo: Boolean = false, // debug
    modifier: Modifier = Modifier
) {
    // Prikazi samo ako imamo relevantno stanje
    if (!shouldShowOverlay(discoveryState) && !showDebugInfo) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // DEBUG CARD samo kad trebalo
        if (showDebugInfo) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("🔍 DEBUG INFO:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Scanning: ${discoveryState.isScanning}", fontSize = 10.sp)
                    Text("Nearby cache: ${discoveryState.nearbyCache?.id ?: "None"}", fontSize = 10.sp)
                    Text("Distance: ${discoveryState.distanceToCache?.toInt() ?: "N/A"}m", fontSize = 10.sp)
                    Text("Can discover: ${discoveryState.canDiscover}", fontSize = 10.sp)
                    Text("Discovery in progress: ${discoveryState.discoveryInProgress}", fontSize = 10.sp)
                    Text("Message: ${discoveryState.message ?: "No message"}", fontSize = 10.sp)
                    Text("Error: ${discoveryState.error ?: "No error"}", fontSize = 10.sp)
                }
            }
        }

        // Error message
        if (discoveryState.error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "⚠️ ${discoveryState.error}",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp
                )
            }
        }

        // Discovery GLAVNA FUNKCIONALNOST
        if (discoveryState.discoveryInProgress && discoveryState.nearbyCache != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = getCacheIcon(discoveryState.nearbyCache.category),
                        fontSize = 48.sp
                    )

                    Text(
                        text = "Cache Pronađen! 🎉",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = getCategoryText(discoveryState.nearbyCache.category),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "${discoveryState.nearbyCache.value} poena",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = discoveryState.nearbyCache.description.take(100) +
                                if (discoveryState.nearbyCache.description.length > 100) "..." else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    var userNote by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = userNote,
                        onValueChange = { userNote = it },
                        label = { Text("Napomena (opciono)") },
                        placeholder = { Text("Vaš komentar...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelDiscovery,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Otkaži")
                        }

                        Button(
                            onClick = {
                                onConfirmDiscovery(userNote.ifBlank { null })
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Potvrdi")
                        }
                    }
                }
            }
        }

        // Success message
        else if (discoveryState.discoveredCache != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🏆 Uspešno! 🏆",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Osvojili ste ${discoveryState.pointsEarned} poena!",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Proximity indicator - samo kada je cache blizu (20m)
        else if (shouldShowProximityIndicator(discoveryState)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = getProximityColor(discoveryState.distanceToCache),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getCacheIcon(discoveryState.nearbyCache!!.category),
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = discoveryState.message ?: "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "${discoveryState.nearbyCache.value} poena • " +
                                    getCategoryText(discoveryState.nearbyCache.category),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// HELPER FUNKCIJE ZA KONTROLU PRIKAZA

private fun shouldShowOverlay(discoveryState: CacheDiscoveryState): Boolean {
    return discoveryState.error != null ||
            discoveryState.discoveryInProgress ||
            discoveryState.discoveredCache != null ||
            shouldShowProximityIndicator(discoveryState)
}

private fun shouldShowProximityIndicator(discoveryState: CacheDiscoveryState): Boolean {
    return discoveryState.nearbyCache != null &&
            discoveryState.distanceToCache != null &&
            discoveryState.distanceToCache <= 30.0 && // prikaži samo kada je blizu
            discoveryState.message != null &&
            !discoveryState.discoveryInProgress
}

@Composable
private fun getProximityColor(distance: Double?): androidx.compose.ui.graphics.Color {
    return when {
        distance == null -> MaterialTheme.colorScheme.secondaryContainer
        distance <= 15.0 -> MaterialTheme.colorScheme.primaryContainer // vrlo blizu - zelena
        distance <= 25.0 -> MaterialTheme.colorScheme.tertiaryContainer // blizu - žuta
        else -> MaterialTheme.colorScheme.secondaryContainer // dalje - siva
    }
}

private fun getCacheIcon(category: CacheCategory): String {
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

private fun getCategoryText(category: CacheCategory): String {
    return when (category) {
        CacheCategory.EASY -> "Lako"
        CacheCategory.ADVENTURE -> "Avantura"
        CacheCategory.PUZZLE -> "Slagalica"
        CacheCategory.HISTORICAL -> "Istorijski"
        CacheCategory.NATURE -> "Priroda"
        CacheCategory.URBAN -> "Gradski"
        CacheCategory.MYSTERY -> "Misterija"
    }
}