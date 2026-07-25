package de.balabucha.reisepilot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

internal fun preferredStage561(snapshot: TripSnapshot): Stage {
    if (snapshot.active) return snapshot.stage
    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
    if (location != null) {
        val toSchwerin = Geo.distanceM(location, TripConfig.schwerin)
        val toHotel = Geo.distanceM(location, TripConfig.hotel)
        val toCanet = Geo.distanceM(location, TripConfig.canet)
        if (toHotel <= 120_000.0 || toCanet <= 500_000.0) return Stage.SUNDAY
        if (toSchwerin <= 700_000.0 || location.lat >= 48.2) return Stage.SATURDAY
    }
    return if (LocalDate.now().isAfter(LocalDate.of(2026, 7, 25))) Stage.SUNDAY else Stage.SATURDAY
}

@Composable
internal fun CurrentLegCard561(
    snapshot: TripSnapshot,
    stage: Stage,
    preview: RouteResult?,
    night: Boolean
) {
    val origin = TripConfig.origin(stage)
    val destination = TripConfig.destination(stage)
    val activeLeg = snapshot.active && snapshot.stage == stage
    val remainingKm = if (activeLeg) snapshot.remainingKm else preview?.distanceM?.div(1_000)
    val durationMinutes = when {
        activeLeg && snapshot.etaEpochMs != null -> Duration.between(Instant.now(), Instant.ofEpochMilli(snapshot.etaEpochMs)).toMinutes().coerceAtLeast(0)
        preview != null -> preview.durationSec / 60L
        else -> null
    }
    val targetLabel = if (stage == Stage.SATURDAY) "Etappe 1 von 2 · Zwischenhotel" else "Etappe 2 von 2 · Canet"
    val surface = if (night) Color(0xFF102033) else Color.White
    val text = if (night) Color(0xFFF3F7FA) else Navy
    val muted = if (night) Color(0xFFB8C7D5) else Muted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        border = BorderStroke(1.dp, Blue.copy(alpha = .20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AKTUELLE TEILSTRECKE", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${origin.name} → ${destination.name}",
                        color = text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(color = Blue.copy(alpha = .11f), shape = RoundedCornerShape(11.dp)) {
                    Text(targetLabel, color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                LegValue561("Noch", remainingKm?.let { "$it km" } ?: if (activeLeg) "wird berechnet" else "Route laden", text, muted, Modifier.weight(1f))
                LegValue561("Fahrzeit", durationMinutes?.let(::minuteText561) ?: "–", text, muted, Modifier.weight(1f))
                LegValue561("Ankunft", if (activeLeg) snapshot.etaEpochMs?.let(::formatTime) ?: "–" else "nach Start", text, muted, Modifier.weight(1f))
            }
            if (activeLeg) {
                val total = snapshot.distanceTravelledKm + (snapshot.remainingKm ?: 0)
                val progress = if (total > 1.0) (snapshot.distanceTravelledKm / total).toFloat().coerceIn(0f, 1f) else 0f
                Spacer(Modifier.height(13.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = Blue,
                    trackColor = Blue.copy(alpha = .12f)
                )
                Spacer(Modifier.height(5.dp))
                Text("${(progress * 100).roundToInt()} % dieser Teilstrecke geschafft", color = muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(11.dp))
            Text(
                "Die komplette Reiseübersicht folgt weiter unten getrennt von dieser aktuellen Etappe.",
                color = muted,
                fontSize = 10.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun LegValue561(label: String, value: String, text: Color, muted: Color, modifier: Modifier) {
    Surface(modifier, color = Blue.copy(alpha = .07f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(label, color = muted, fontSize = 9.sp)
            Text(value, color = text, fontWeight = FontWeight.Black, fontSize = if (value.length > 12) 11.sp else 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun HomeMetrics561(
    snapshot: TripSnapshot,
    road: RoadAheadState54,
    openPacking: Int,
    stage: Stage,
    preview: RouteResult?,
    night: Boolean
) {
    val remaining = if (snapshot.active) snapshot.remainingKm else preview?.distanceM?.div(1_000)
    val liveCount = road.fuelOptions.size + road.roadsideStops.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard561("Tempo", road.speedKmh?.let { "$it km/h" } ?: snapshot.speedKmh?.let { "$it km/h" } ?: "0 km/h", night, Modifier.weight(1f))
            MetricCard561(if (stage == Stage.SATURDAY) "Bis Hotel" else "Bis Canet", remaining?.let { "$it km" } ?: "wird berechnet", night, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard561("Ankunft", snapshot.etaEpochMs?.let(::formatTime) ?: if (snapshot.active) "wird berechnet" else "nach Start", night, Modifier.weight(1f))
            MetricCard561(
                "Live-Daten",
                when {
                    road.loadingFuel || road.loadingRoadside -> "werden geladen"
                    liveCount > 0 -> "$liveCount Punkte"
                    else -> "noch keine"
                },
                night,
                Modifier.weight(1f)
            )
        }
        if (!snapshot.active && openPacking > 0) {
            Text("Packliste: noch $openPacking Punkte offen", color = if (night) Color(0xFFB8C7D5) else Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MetricCard561(label: String, value: String, night: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 78.dp),
        color = if (night) Color(0xFF102033) else Color.White,
        shape = RoundedCornerShape(17.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = if (night) Color(0xFFB8C7D5) else Muted, fontSize = 10.sp)
            Text(value, color = if (night) Color(0xFFF3F7FA) else Navy, fontSize = if (value.length > 14) 13.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun NextThings561(
    roadState: RoadAheadState54,
    snapshot: TripSnapshot,
    night: Boolean,
    onOpen: () -> Unit,
    onPacking: () -> Unit
) {
    val service = roadState.nextService
    val parking = roadState.nextParking
    val fuel = roadState.nextFuel
    val nearby = roadState.nearbyMode && !snapshot.active

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        CompactNext561(
            label = if (nearby) "Raststätte / Rastplatz in der Nähe" else "Nächste Raststätte",
            title = service?.name ?: if (roadState.loadingRoadside) "Suche läuft" else "Noch kein Treffer",
            value = service?.distanceAheadKm?.let(::distanceKm54) ?: "–",
            detail = service?.let { if (nearby) "Entfernung vom Standort${featureText55(it)}" else "${travelTime55(it.distanceAheadKm, roadState.speedKmh)}${featureText55(it)}" }
                ?: roadState.roadsideMessage.ifBlank { "OpenStreetMap wird geprüft." },
            accent = Color(0xFF6A4BBC),
            night = night,
            onClick = onOpen
        )
        CompactNext561(
            label = if (nearby) "Parkplatz in der Nähe" else "Nächster Parkplatz",
            title = parking?.name ?: if (roadState.loadingRoadside) "Suche läuft" else "Noch kein Treffer",
            value = parking?.distanceAheadKm?.let(::distanceKm54) ?: "–",
            detail = parking?.let { if (nearby) "Entfernung vom Standort${featureText55(it)}" else "${travelTime55(it.distanceAheadKm, roadState.speedKmh)}${featureText55(it)}" }
                ?: roadState.roadsideMessage.ifBlank { "Parkplatzdaten werden geprüft." },
            accent = Teal,
            night = night,
            onClick = onOpen
        )
        CompactNext561(
            label = if (nearby) "Diesel in der Nähe" else "Diesel in Fahrtrichtung",
            title = fuel?.name ?: if (roadState.loadingFuel) "Preise werden geladen" else "Noch kein Treffer",
            value = fuel?.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €/l", it) }
                ?: fuel?.distanceAheadKm?.let(::distanceKm54) ?: "–",
            detail = fuel?.let {
                if (nearby) "${distanceKm54(it.distanceAheadKm)} vom Standort"
                else "${distanceKm54(it.distanceAheadKm)} · ${String.format(Locale.GERMANY, "%.1f km Umweg", it.detourKm)}"
            } ?: roadState.fuelMessage.ifBlank { "Tankstellenquelle wird geprüft." },
            accent = Green,
            night = night,
            onClick = onOpen
        )
        if (!snapshot.active && roadState.fuelOptions.isEmpty() && roadState.roadsideStops.isEmpty() && !roadState.loadingFuel && !roadState.loadingRoadside) {
            TextButton(onClick = onPacking) { Text("Vor der Abfahrt Packliste öffnen") }
        }
        if (roadState.message.isNotBlank()) {
            Text(roadState.message, color = if (night) Color(0xFFB8C7D5) else Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CompactNext561(
    label: String,
    title: String,
    value: String,
    detail: String,
    accent: Color,
    night: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = .20f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(Locale.GERMANY), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(title, color = if (night) Color(0xFFF3F7FA) else Navy, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = if (night) Color(0xFFB8C7D5) else Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = if (value.length > 10) 14.sp else 19.sp, maxLines = 2)
        }
    }
}

private fun minuteText561(minutes: Long): String = when {
    minutes < 60 -> "$minutes Min."
    else -> "${minutes / 60} Std. ${minutes % 60} Min."
}
