package de.balabucha.reisepilot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualLiveScreen54(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        VisualLiveScreen53(activity, snapshot, Modifier.fillMaxSize())
        if (snapshot.active) {
            RoadAheadCompact54(
                state = RoadAheadStore54.state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 88.dp)
                    .testTag("road-ahead-compact"),
                onOpen = { expanded = true }
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            RoadAheadSheet54(activity, RoadAheadStore54.state)
        }
    }
}

@Composable
private fun RoadAheadCompact54(
    state: RoadAheadState54,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    val service = state.nextService
    val parking = state.nextParking
    val fuel = state.nextFuel
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1D30)),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Green, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("ECHTZEIT · WAS KOMMT ALS NÄCHSTES?", color = Color.White.copy(alpha = .72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("Antippen", color = Color(0xFFFFD166), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMetric54(
                    label = "Tempo",
                    value = state.speedKmh?.let { "$it km/h" } ?: "–",
                    modifier = Modifier.weight(1f)
                )
                CompactMetric54(
                    label = service?.kind?.label ?: "Raststätte",
                    value = service?.distanceAheadKm?.let(::distanceKm54) ?: loadingValue54(state.loadingRoadside),
                    modifier = Modifier.weight(1f)
                )
                CompactMetric54(
                    label = "Parkplatz",
                    value = parking?.distanceAheadKm?.let(::distanceKm54) ?: loadingValue54(state.loadingRoadside),
                    modifier = Modifier.weight(1f)
                )
                CompactMetric54(
                    label = "Diesel",
                    value = fuel?.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €", it) }
                        ?: fuel?.distanceAheadKm?.let(::distanceKm54)
                        ?: loadingValue54(state.loadingFuel),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactMetric54(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(alpha = .62f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoadAheadSheet54(activity: MainActivity, state: RoadAheadState54) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nächste Stopps in Fahrtrichtung", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Tempo wird ungefähr jede Sekunde aktualisiert. Entfernungen laufen mit der Position mit.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Surface(color = Green.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        state.speedKmh?.let { "$it km/h" } ?: "GPS",
                        color = Green,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
        item {
            Button(
                onClick = activity::refreshRoadAhead54,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.loadingFuel || state.loadingRoadside) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.loadingFuel || state.loadingRoadside) "Live-Daten werden geladen" else "Jetzt aktualisieren")
            }
        }
        if (state.message.isNotBlank()) {
            item { WarningCard("Live-Hinweis", state.message, Light.YELLOW) }
        }
        item { SectionTitle("Tankstellen abseits der Autobahn", "Preis + Umweg") }
        if (state.fuelOptions.isEmpty()) {
            item {
                StatusLine(
                    "Noch keine Tankstellen",
                    if (state.loadingFuel) "Preise und Fahrtrichtung werden geprüft." else "Bei Deutschland ist für Preise ein Tankerkönig-Key erforderlich.",
                    if (state.loadingFuel) Light.YELLOW else Light.GREY
                )
            }
        } else {
            items(state.fuelOptions, key = { "fuel54:${it.point.lat}:${it.point.lon}" }) { fuel ->
                FuelOptionCard54(activity, fuel)
            }
        }
        item { SectionTitle("Raststätten, Rastplätze und Parkplätze", "bis 120 km voraus") }
        if (state.roadsideStops.isEmpty()) {
            item {
                StatusLine(
                    "Noch keine Stopps",
                    if (state.loadingRoadside) "OpenStreetMap-Punkte werden entlang der Route geladen." else "Keine passenden Punkte in der aktuellen Route gefunden.",
                    if (state.loadingRoadside) Light.YELLOW else Light.GREY
                )
            }
        } else {
            items(state.roadsideStops.take(12), key = { "road54:${it.id}" }) { stop ->
                RoadsideCard54(activity, stop)
            }
        }
    }
}

@Composable
private fun FuelOptionCard54(activity: MainActivity, fuel: FuelSuggestion) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(fuel.name, fontWeight = FontWeight.Bold, maxLines = 2)
                    if (fuel.address.isNotBlank()) Text(fuel.address, color = Muted, fontSize = 11.sp, maxLines = 2)
                }
                Text(
                    fuel.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €/l", it) } ?: "Preis offen",
                    color = if (fuel.pricePerLitre != null) Green else Muted,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "${distanceKm54(fuel.distanceAheadKm)} voraus · ca. ${String.format(Locale.GERMANY, "%.1f", fuel.detourKm)} km Umweg",
                color = Navy,
                fontSize = 13.sp
            )
            Text(fuel.source, color = Muted, fontSize = 10.sp, maxLines = 2)
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = { activity.openPointRoute(fuel.point, fuel.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Zur Tankstelle navigieren") }
        }
    }
}

@Composable
private fun RoadsideCard54(activity: MainActivity, stop: RoadsideStop54) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = roadsideColor54(stop.kind).copy(alpha = .12f), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        stop.kind.label,
                        color = roadsideColor54(stop.kind),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(stop.name, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(
                        "${distanceKm54(stop.distanceAheadKm)} voraus" +
                            stop.detourKm?.let { " · ca. ${String.format(Locale.GERMANY, "%.1f", it)} km Umweg" }.orEmpty(),
                        color = Navy,
                        fontSize = 13.sp
                    )
                }
            }
            val features = buildList {
                if (stop.hasFuel) add("Tanken")
                if (stop.hasToilets) add("WC")
                if (stop.hasFood) add("Essen")
                if (stop.directionChecked) add("Fahrtrichtung geprüft")
            }
            if (features.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(features.joinToString(" · "), color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = { activity.openPointRoute(stop.point, stop.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Dorthin navigieren") }
        }
    }
}

internal fun distanceKm54(value: Double): String = when {
    value < 1.0 -> "${(value * 1_000).toInt().coerceAtLeast(0)} m"
    else -> String.format(Locale.GERMANY, "%.1f km", value)
}

private fun loadingValue54(loading: Boolean): String = if (loading) "lädt …" else "–"

internal fun roadsideColor54(kind: RoadsideKind54): Color = when (kind) {
    RoadsideKind54.SERVICE_AREA -> Color(0xFF6A4BBC)
    RoadsideKind54.REST_AREA -> Green
    RoadsideKind54.PARKING -> Teal
}
