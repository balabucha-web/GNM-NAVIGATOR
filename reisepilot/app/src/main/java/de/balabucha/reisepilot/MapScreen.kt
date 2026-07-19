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

    Page("Live-Karte", "Native Karte mit Verkehr, Maut und Tankstopps", modifier) {
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
                Modifier.fillMaxWidth().height(480.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEAF0F5))
            ) {
                NativeTripMap(
                    snapshot = displaySnapshot,
                    previewRoute = preview.route,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        item {
            AppCard {
                Text("Legende", style = MaterialTheme.typography.titleMedium)
                StatusLine("Grün", "freie Strecke oder günstiger Tankpunkt", Light.GREEN)
                StatusLine("Gelb", "mäßiger Verkehr oder Mautpunkt", Light.YELLOW)
                StatusLine("Rot", "starker Verkehr oder sofort handeln", Light.RED)
                Text(
                    "Die Basiskarte läuft nativ. Google Maps bleibt für die eigentliche Navigation zuständig.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
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
