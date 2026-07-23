package de.balabucha.reisepilot

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreviewState51(
    val route: RouteResult? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen51(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val token = normalizeMapboxToken(prefs.getString("mapbox_token", "").orEmpty())
    val tokenValid = mapboxTokenLooksValid(token)
    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }
    var mapInstance by rememberSaveable { mutableIntStateOf(0) }
    var parkingRevision by rememberSaveable { mutableIntStateOf(0) }
    var showTraffic by rememberSaveable { mutableStateOf(true) }
    var showFuel by rememberSaveable { mutableStateOf(true) }
    var showParking by rememberSaveable { mutableStateOf(true) }
    var showTolls by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val savedParkings = remember(parkingRevision) { ParkingSelectionStore.all(activity.applicationContext) }

    val useLiveRoute = snapshot.active && stage == snapshot.stage && snapshot.routeGeoJson.isNotBlank()
    val preview by produceState(
        initialValue = PreviewState51(loading = tokenValid && !useLiveRoute),
        token,
        stage,
        useLiveRoute
    ) {
        if (!tokenValid || useLiveRoute) {
            value = PreviewState51()
            return@produceState
        }
        value = PreviewState51(loading = true)
        val result = withContext(Dispatchers.IO) {
            runCatching { MapboxClient.route(token, TripConfig.origin(stage), TripConfig.destination(stage)) }
        }
        value = result.fold(
            onSuccess = { PreviewState51(route = it) },
            onFailure = { PreviewState51(error = it.message?.take(160) ?: "Route konnte nicht geladen werden") }
        )
    }

    val displaySnapshot = if (stage == snapshot.stage) snapshot else snapshot.copy(
        stage = stage,
        lat = null,
        lon = null,
        routeGeoJson = "",
        congestionJson = "[]",
        tolls = emptyList()
    )
    val layers = MapLayers51(showTraffic, showFuel, showParking, showTolls)

    Page("Route & Karte", "Route, Verkehr und wichtige Punkte", modifier) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Stage.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = stage == item,
                        onClick = { stage = item },
                        shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size),
                        label = { Text(if (item == Stage.SATURDAY) "Samstag" else "Sonntag") }
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = showTraffic,
                        onClick = { showTraffic = !showTraffic },
                        label = { Text("Verkehr") }
                    )
                }
                item {
                    FilterChip(
                        selected = showFuel,
                        onClick = { showFuel = !showFuel },
                        label = { Text("Tanken") }
                    )
                }
                item {
                    FilterChip(
                        selected = showParking,
                        onClick = { showParking = !showParking },
                        label = { Text("Parken") }
                    )
                }
                item {
                    FilterChip(
                        selected = showTolls,
                        onClick = { showTolls = !showTolls },
                        label = { Text("Maut") }
                    )
                }
            }
        }

        item {
            StatusLine(
                "Karte",
                when {
                    useLiveRoute -> "Live-Route aus dem Hintergrundtracking"
                    preview.route != null -> "Vorschau mit aktuellem Mapbox-Verkehr"
                    preview.loading -> "Route und Verkehr werden geladen"
                    preview.error != null -> preview.error ?: "Route konnte nicht geladen werden"
                    tokenValid -> "Basiskarte aktiv · Route wird beim Tracking ergänzt"
                    else -> "Basiskarte aktiv · für Live-Verkehr Mapbox-Token prüfen"
                },
                when {
                    useLiveRoute || preview.route != null -> Light.GREEN
                    preview.loading -> Light.YELLOW
                    preview.error != null -> Light.RED
                    else -> Light.GREY
                }
            )
        }

        item {
            Card(
                Modifier.fillMaxWidth().height(510.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Line),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0F5))
            ) {
                key(mapInstance) {
                    NativeTripMap51(
                        snapshot = displaySnapshot,
                        previewRoute = preview.route,
                        parkings = savedParkings,
                        layers = layers,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { mapInstance++ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Gesamte Route") }
                OutlinedButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)
                ) { Text(if (showDetails) "Details schließen" else "Punkte & Legende") }
            }
        }

        item {
            AppCard {
                SectionTitle("Kartenzeichen")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendPill51("Start", Navy, Modifier.weight(1f))
                    LegendPill51("Ziel", Red, Modifier.weight(1f))
                    LegendPill51("Standort", Blue, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendPill51("Tank", Color(0xFF7E57C2), Modifier.weight(1f))
                    LegendPill51("Maut", Yellow, Modifier.weight(1f))
                    LegendPill51("Parken", Teal, Modifier.weight(1f))
                }

                if (showDetails) {
                    val tolls = displaySnapshot.tolls.ifEmpty { preview.route?.tolls.orEmpty() }
                    if (showTolls && tolls.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Mautpunkte", fontWeight = FontWeight.Bold)
                        tolls.forEach { Text("• ${it.name}", color = Muted, fontSize = 12.sp) }
                    }
                    if (showFuel) {
                        Spacer(Modifier.height(12.dp))
                        Text("Geplante Tankpunkte", fontWeight = FontWeight.Bold)
                        TripConfig.fuelStops(stage).forEach { Text("• ${it.name}", color = Muted, fontSize = 12.sp) }
                        displaySnapshot.fuelSuggestion?.let {
                            Text("• Empfohlen: ${it.name}", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showParking && savedParkings.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Gemerkte Parkplätze", fontWeight = FontWeight.Bold)
                        savedParkings.forEach { saved ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(saved.spot.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(saved.placeTitle, color = Muted, fontSize = 11.sp)
                                }
                                TextButton(onClick = { activity.openPointRoute(saved.spot.point, saved.spot.name) }) {
                                    Text("Navigieren")
                                }
                                TextButton(onClick = {
                                    val place = DestinationCatalog.places.firstOrNull {
                                        ParkingSelectionStore.placeKey(it) == saved.placeKey
                                    }
                                    if (place != null) ParkingSelectionStore.remove(activity.applicationContext, place)
                                    parkingRevision++
                                    mapInstance++
                                }) { Text("Entfernen") }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { activity.openMaps(stage) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Navigation mit Google Maps") }
        }
    }
}

@Composable
private fun LegendPill51(label: String, color: Color, modifier: Modifier) {
    Surface(modifier, color = color.copy(alpha = .10f), shape = RoundedCornerShape(13.dp)) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
