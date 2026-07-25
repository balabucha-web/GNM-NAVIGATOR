package de.balabucha.reisepilot

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreviewState(
    val route: RouteResult? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val token = normalizeMapboxToken(prefs.getString("mapbox_token", "").orEmpty())
    val tokenValid = mapboxTokenLooksValid(token)
    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }
    var mapInstance by rememberSaveable { mutableIntStateOf(0) }
    var parkingRevision by rememberSaveable { mutableIntStateOf(0) }
    val savedParkings = remember(parkingRevision) { ParkingSelectionStore.all(activity.applicationContext) }

    val useLiveRoute = snapshot.active && stage == snapshot.stage && snapshot.routeGeoJson.isNotBlank()
    val preview by produceState(
        initialValue = PreviewState(loading = tokenValid && !useLiveRoute),
        token,
        stage,
        useLiveRoute
    ) {
        if (!tokenValid || useLiveRoute) {
            value = PreviewState()
            return@produceState
        }
        value = PreviewState(loading = true)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                MapboxClient.route(token, TripConfig.origin(stage), TripConfig.destination(stage))
            }
        }
        value = result.fold(
            onSuccess = { PreviewState(route = it) },
            onFailure = { PreviewState(error = it.message?.take(160) ?: "Route konnte nicht geladen werden") }
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

    Page("Route & Karte", "", modifier) {
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
                Modifier.fillMaxWidth().height(520.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEAF0F5))
            ) {
                key(mapInstance) {
                    NativeTripMap(
                        snapshot = displaySnapshot,
                        previewRoute = preview.route,
                        parkings = savedParkings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { mapInstance++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Gesamte Route anzeigen") }
        }

        item {
            AppCard {
                Text("Punkte auf der Karte", style = MaterialTheme.typography.titleMedium)
                StatusLine("Dunkelblau", "Startpunkt", Light.GREY)
                StatusLine("Rot", "Ziel", Light.RED)
                StatusLine("Blau", "dein aktueller Standort", Light.GREY)
                StatusLine("Gelb", "Mautstelle", Light.YELLOW)
                StatusLine("Lila", "geplanter Tankstopp", Light.GREY)
                StatusLine("Großes Grün", "aktuell empfohlene Tankstelle", Light.GREEN)
                StatusLine("Türkis P", "gemerkter Parkplatz", Light.GREY)
                val tolls = displaySnapshot.tolls.ifEmpty { preview.route?.tolls.orEmpty() }
                if (tolls.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mautpunkte", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    tolls.forEach { Text("• ${it.name}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Geplante Tankpunkte", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                TripConfig.fuelStops(stage).forEach { Text("• ${it.name}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                displaySnapshot.fuelSuggestion?.let { Text("• Empfohlen: ${it.name}", color = Green, style = MaterialTheme.typography.bodySmall) }
                if (savedParkings.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Gemerkte Parkplätze", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    savedParkings.forEach { saved ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(saved.spot.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(saved.placeTitle, color = Muted, style = MaterialTheme.typography.bodySmall)
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
                            }) { Text("Von Karte entfernen") }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { activity.openMaps(stage) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Navigation mit Google Maps") }
        }
    }
}
