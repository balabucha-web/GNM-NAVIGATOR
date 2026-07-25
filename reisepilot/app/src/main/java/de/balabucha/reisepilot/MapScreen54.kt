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
import java.util.Locale

private data class PreviewState54(
    val route: RouteResult? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen54(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val token = normalizeMapboxToken(prefs.getString("mapbox_token", "").orEmpty())
    val tokenValid = mapboxTokenLooksValid(token)
    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }
    var mapInstance by rememberSaveable { mutableIntStateOf(0) }
    var parkingRevision by rememberSaveable { mutableIntStateOf(0) }
    var showTraffic by rememberSaveable { mutableStateOf(true) }
    var showFuel by rememberSaveable { mutableStateOf(true) }
    var showRoadside by rememberSaveable { mutableStateOf(true) }
    var showParking by rememberSaveable { mutableStateOf(true) }
    var showTolls by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val savedParkings = remember(parkingRevision) { ParkingSelectionStore.all(activity.applicationContext) }
    val roadState = RoadAheadStore54.state

    val useLiveRoute = snapshot.active && stage == snapshot.stage && snapshot.routeGeoJson.isNotBlank()
    val preview by produceState(
        initialValue = PreviewState54(loading = tokenValid && !useLiveRoute),
        token,
        stage,
        useLiveRoute
    ) {
        if (!tokenValid || useLiveRoute) {
            value = PreviewState54()
            return@produceState
        }
        value = PreviewState54(loading = true)
        val result = withContext(Dispatchers.IO) {
            runCatching { MapboxClient.route(token, TripConfig.origin(stage), TripConfig.destination(stage)) }
        }
        value = result.fold(
            onSuccess = { PreviewState54(route = it) },
            onFailure = { PreviewState54(error = it.message?.take(160) ?: "Route konnte nicht geladen werden") }
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
    val displayRoadState = if (useLiveRoute) roadState else RoadAheadState54()
    val layers = MapLayers54(showTraffic, showFuel, showRoadside, showParking, showTolls)

    Page("Route & Karte", "Verkehr, Preise und nächste Stopps", modifier) {
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
                item { FilterChip(selected = showTraffic, onClick = { showTraffic = !showTraffic }, label = { Text("Verkehr") }) }
                item { FilterChip(selected = showFuel, onClick = { showFuel = !showFuel }, label = { Text("Tanken") }) }
                item { FilterChip(selected = showRoadside, onClick = { showRoadside = !showRoadside }, label = { Text("Rastplätze") }) }
                item { FilterChip(selected = showParking, onClick = { showParking = !showParking }, label = { Text("Zielparken") }) }
                item { FilterChip(selected = showTolls, onClick = { showTolls = !showTolls }, label = { Text("Maut") }) }
            }
        }
        if (useLiveRoute) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1D30)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MapQuick54("Tempo", roadState.speedKmh?.let { "$it km/h" } ?: "–", Modifier.weight(1f))
                        MapQuick54("Raststätte", roadState.nextService?.distanceAheadKm?.let(::distanceKm54) ?: "–", Modifier.weight(1f))
                        MapQuick54("Parkplatz", roadState.nextParking?.distanceAheadKm?.let(::distanceKm54) ?: "–", Modifier.weight(1f))
                        MapQuick54(
                            "Diesel",
                            roadState.nextFuel?.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €", it) }
                                ?: roadState.nextFuel?.distanceAheadKm?.let(::distanceKm54)
                                ?: "–",
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            StatusLine(
                "Karte",
                when {
                    useLiveRoute -> "Live-Route mit Stopps in Fahrtrichtung"
                    preview.route != null -> "Vorschau mit aktuellem Mapbox-Verkehr"
                    preview.loading -> "Route und Verkehr werden geladen"
                    preview.error != null -> preview.error ?: "Route konnte nicht geladen werden"
                    tokenValid -> "Basiskarte aktiv · Live-Punkte erscheinen während der Fahrt"
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
                    NativeTripMap54(
                        snapshot = displaySnapshot,
                        previewRoute = preview.route,
                        savedParkings = savedParkings,
                        roadState = displayRoadState,
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
                ) { Text(if (showDetails) "Details schließen" else "Nächste Punkte") }
            }
        }
        item {
            AppCard {
                SectionTitle("Kartenzeichen")
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MapLegend54("Tank", Color(0xFF7E57C2), Modifier.weight(1f))
                    MapLegend54("Rast", Green, Modifier.weight(1f))
                    MapLegend54("Parken", Teal, Modifier.weight(1f))
                    MapLegend54("Maut", Yellow, Modifier.weight(1f))
                }
                if (showDetails && useLiveRoute) {
                    Spacer(Modifier.height(14.dp))
                    Text("Tankstellen abseits der Autobahn", fontWeight = FontWeight.Bold)
                    if (roadState.fuelOptions.isEmpty()) Text("Preise werden geladen oder API-Key fehlt.", color = Muted, fontSize = 12.sp)
                    roadState.fuelOptions.take(4).forEach { fuel ->
                        MapActionLine54(
                            title = fuel.name,
                            detail = buildString {
                                append(distanceKm54(fuel.distanceAheadKm))
                                fuel.pricePerLitre?.let { append(" · ").append(String.format(Locale.GERMANY, "%.3f €/l", it)) }
                                append(" · ").append(String.format(Locale.GERMANY, "%.1f km Umweg", fuel.detourKm))
                            },
                            onClick = { activity.openPointRoute(fuel.point, fuel.name) }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Raststätten und Parkplätze", fontWeight = FontWeight.Bold)
                    roadState.roadsideStops.take(8).forEach { stop ->
                        MapActionLine54(
                            title = "${stop.kind.label} · ${stop.name}",
                            detail = distanceKm54(stop.distanceAheadKm) + if (stop.directionChecked) " · Fahrtrichtung geprüft" else "",
                            onClick = { activity.openPointRoute(stop.point, stop.name) }
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { activity.openMaps(stage) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Google Maps") }
                OutlinedButton(
                    onClick = activity::refreshRoadAhead54,
                    enabled = useLiveRoute,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Live aktualisieren") }
            }
        }
    }
}

@Composable
private fun MapQuick54(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = .62f), fontSize = 9.sp, maxLines = 1)
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun MapLegend54(label: String, color: Color, modifier: Modifier) {
    Surface(modifier, color = color.copy(alpha = .10f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun MapActionLine54(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
            Text(detail, color = Muted, fontSize = 11.sp, maxLines = 2)
        }
        TextButton(onClick = onClick) { Text("Route") }
    }
}
