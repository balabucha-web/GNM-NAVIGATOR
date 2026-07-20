package de.balabucha.reisepilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LiveScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val context = LocalContext.current
    val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val tokenValid = settings.getBoolean("mapbox_token_valid", false)
    val tankCapacity = settings.getFloat("start_litres", 60f).toDouble().coerceAtLeast(20.0)
    val consumption = settings.getFloat("consumption", 7.4f).toDouble().coerceAtLeast(3.0)
    var chosen by rememberSaveable(snapshot.stage) { mutableStateOf(snapshot.stage) }

    val bisonSummary by produceState(
        initialValue = BisonTrafficSummary(),
        chosen
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) { BisonTrafficClient.load(chosen) }
            delay(5L * 60L * 1000L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (locationGranted) activity.startTrip(chosen)
    }

    Page("ReisePilot", "Live-Dashboard für Route, Ankunft, Verkehr und Tank", modifier) {
        item { ClockCard(snapshot) }
        item {
            JourneyDashboardCard(
                activity = activity,
                snapshot = snapshot,
                chosen = chosen,
                onChosen = { if (!snapshot.active) chosen = it },
                onStart = {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    val locationGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (locationGranted) activity.startTrip(chosen)
                    else permissionLauncher.launch(permissions)
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(
                    "ETA",
                    snapshot.etaEpochMs?.let(::formatTime) ?: "–",
                    snapshot.scheduleLight,
                    Modifier.weight(1f)
                )
                Metric(
                    "Reststrecke",
                    snapshot.remainingKm?.let { "$it km" } ?: "–",
                    Light.GREY,
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(
                    "Fahrzeit",
                    minuteText(snapshot.driveMinutes),
                    snapshot.pauseLight,
                    Modifier.weight(1f)
                )
                Metric(
                    "Verzögerung",
                    snapshot.trafficDelayMin?.let { if (it <= 0) "normal" else "+$it Min." } ?: "–",
                    trafficLight(snapshot.trafficDelayMin),
                    Modifier.weight(1f)
                )
            }
        }

        item { FuelGaugeCard(snapshot, tankCapacity, consumption) }
        item { NextAction(activity, snapshot) }
        snapshot.fuelSuggestion?.let { suggestion ->
            item { FuelSuggestionCard(activity, suggestion) }
        }
        item { BisonTrafficCard(bisonSummary, chosen) }
        item { AutomaticStatus(snapshot, tokenValid) }

        if (!snapshot.apiOk && !tokenValid) {
            item {
                WarningCard(
                    "Live-Verkehr noch aus",
                    "Unter Mehr den vollständigen Mapbox-Token speichern und direkt prüfen.",
                    Light.YELLOW
                )
            }
        } else if (snapshot.active && tokenValid && !snapshot.apiOk) {
            item {
                WarningCard(
                    "Live-Route wird aufgebaut",
                    snapshot.apiMessage,
                    Light.YELLOW
                )
            }
        }
    }
}

@Composable
private fun JourneyDashboardCard(
    activity: MainActivity,
    snapshot: TripSnapshot,
    chosen: Stage,
    onChosen: (Stage) -> Unit,
    onStart: () -> Unit
) {
    val origin = TripConfig.origin(chosen)
    val destination = TripConfig.destination(chosen)
    val isCurrent = snapshot.active && snapshot.stage == chosen
    val travelled = if (isCurrent) snapshot.distanceTravelledKm.coerceAtLeast(0.0) else 0.0
    val remaining = if (isCurrent) snapshot.remainingKm?.toDouble() else null
    val total = remaining?.let { travelled + it }
    val progress = if (total != null && total > 1.0) (travelled / total).toFloat().coerceIn(0f, 1f) else 0f

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (chosen == Stage.SATURDAY) "SAMSTAG · HINREISE" else "SONNTAG · WEITERREISE",
                    color = Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        snapshot.active && snapshot.paused -> "Pause aktiv"
                        snapshot.active -> "Tracking aktiv"
                        else -> "Reise bereit"
                    },
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Lamp(
                when {
                    !snapshot.active -> Light.GREY
                    snapshot.paused -> Light.YELLOW
                    else -> Light.GREEN
                },
                30.dp
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(origin.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("→", color = Blue, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Text(destination.name, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = Green,
            trackColor = Color(0xFFE4EAF0),
            strokeCap = StrokeCap.Round
        )
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (isCurrent) "${"%.0f".format(travelled)} km gefahren" else "Start noch nicht erfasst",
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                if (isCurrent && remaining != null) "${(progress * 100).roundToInt()} % · ${remaining.roundToInt()} km offen" else "0 %",
                color = Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Stage.entries.forEachIndexed { index, stage ->
                SegmentedButton(
                    selected = chosen == stage,
                    onClick = { onChosen(stage) },
                    shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size),
                    label = { Text(if (stage == Stage.SATURDAY) "Samstag" else "Sonntag") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                shape = RoundedCornerShape(13.dp)
            ) { Text(if (snapshot.active) "Neu starten" else "Fahrt starten") }
            OutlinedButton(
                onClick = { activity.openMaps(chosen) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Google Maps") }
        }
        if (snapshot.active) {
            TextButton(
                onClick = { activity.serviceAction(TripTrackingService.ACTION_STOP) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Tracking stoppen", color = Red) }
        }
    }
}

@Composable
private fun FuelGaugeCard(snapshot: TripSnapshot, capacity: Double, consumption: Double) {
    val litres = snapshot.fuelLitres.coerceIn(0.0, capacity)
    val fraction = (litres / capacity).toFloat().coerceIn(0f, 1f)
    val rangeKm = (litres / consumption * 100.0).roundToInt().coerceAtLeast(0)
    val gaugeColor = statusColor(snapshot.fuelLight)

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Geschätzter Tankinhalt", style = MaterialTheme.typography.titleLarge)
                Text("Berechnet aus Starttank, GPS-Strecke und Verbrauch", color = Muted, fontSize = 12.sp)
            }
            Text("ca. $rangeKm km", color = Blue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 5.dp)) {
                val center = Offset(size.width / 2f, size.height * 0.72f)
                val radius = min(size.width * 0.42f, size.height * 0.60f)
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                val start = 150f
                val sweep = 240f

                drawArc(
                    color = Color(0xFFD9E0E7),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 19f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Red.copy(alpha = .75f),
                    startAngle = start,
                    sweepAngle = sweep * .13f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 19f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = gaugeColor,
                    startAngle = start,
                    sweepAngle = sweep * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 13f, cap = StrokeCap.Round)
                )

                repeat(9) { index ->
                    val angle = start + sweep * index / 8f
                    val rad = angle / 180f * PI.toFloat()
                    val outer = Offset(
                        center.x + cos(rad) * (radius + 2f),
                        center.y + sin(rad) * (radius + 2f)
                    )
                    val inner = Offset(
                        center.x + cos(rad) * (radius - if (index % 4 == 0) 22f else 14f),
                        center.y + sin(rad) * (radius - if (index % 4 == 0) 22f else 14f)
                    )
                    drawLine(Navy.copy(alpha = .72f), inner, outer, strokeWidth = if (index % 4 == 0) 4f else 2f)
                }

                val needleAngle = start + sweep * fraction
                val needleRad = needleAngle / 180f * PI.toFloat()
                val needleEnd = Offset(
                    center.x + cos(needleRad) * radius * .72f,
                    center.y + sin(needleRad) * radius * .72f
                )
                drawLine(Navy, center, needleEnd, strokeWidth = 7f, cap = StrokeCap.Round)
                drawCircle(Navy, 13f, center)
                drawCircle(Color.White, 5f, center)
            }
            Text("E", modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 16.dp), fontWeight = FontWeight.Bold, color = Red)
            Text("½", modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp), fontWeight = FontWeight.Bold, color = Muted)
            Text("F", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp), fontWeight = FontWeight.Bold, color = Green)
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${"%.1f".format(litres)} L", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Navy)
                Text("von ${"%.0f".format(capacity)} L · ${(fraction * 100).roundToInt()} %", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BisonTrafficCard(summary: BisonTrafficSummary, stage: Stage) {
    val roads = if (stage == Stage.SATURDAY) "A36" else "A36 · A6 · A7 · A9"
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Frankreich-Verkehr live", style = MaterialTheme.typography.titleLarge)
                Text("Bison Futé automatisch auf Deutsch · Route $roads", color = Muted, fontSize = 12.sp)
            }
            AssistChip(onClick = {}, label = { Text("LIVE") })
        }
        if (summary.updated.isNotBlank()) {
            Text("Stand: ${summary.updated}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        when {
            summary.error != null -> WarningCard("Bison Futé nicht erreichbar", summary.error, Light.YELLOW)
            summary.events.isEmpty() -> StatusLine("Route", "Keine relevante aktuelle Meldung auf den geplanten französischen Autobahnen", Light.GREEN)
            else -> summary.events.take(4).forEach { event ->
                val light = when (event.severity) {
                    3 -> Light.RED
                    2 -> Light.YELLOW
                    else -> Light.GREY
                }
                StatusLine("${event.road} · ${event.title}", event.detail, light)
            }
        }
        Text("Automatische Aktualisierung alle fünf Minuten. Mapbox bleibt zusätzlich für ETA und Verkehr auf der konkreten Route aktiv.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ClockCard(snapshot: TripSnapshot) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }
    val overall = when {
        !snapshot.active -> Light.GREY
        snapshot.scheduleLight == Light.RED || snapshot.pauseLight == Light.RED || snapshot.fuelLight == Light.RED -> Light.RED
        snapshot.scheduleLight == Light.YELLOW || snapshot.pauseLight == Light.YELLOW || snapshot.fuelLight == Light.YELLOW -> Light.YELLOW
        else -> Light.GREEN
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)),
                    color = Color(0xFFCFD8E4)
                )
            }
            Lamp(overall, 48.dp)
        }
    }
}

@Composable
private fun NextAction(activity: MainActivity, s: TripSnapshot) {
    AppCard {
        Text("NÄCHSTER SCHRITT", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(s.nextTitle, style = MaterialTheme.typography.titleLarge)
        Text(s.nextDetail, color = Muted)
        s.nextDistanceM?.let {
            Spacer(Modifier.height(7.dp))
            Text(distanceText(it), color = Blue, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))

        val fuelAction = s.fuelSuggestion != null && s.nextTitle.contains("Tank", true)
        val action = when {
            s.nextTitle.contains("anrufen", true) -> "Anrufen"
            s.nextTitle.contains("Pause", true) -> "Pause erledigt"
            fuelAction -> "Zur Tankstelle"
            else -> "Navigation öffnen"
        }

        Button(
            onClick = {
                when (action) {
                    "Anrufen" -> activity.dial(
                        if (s.stage == Stage.SUNDAY) "+33468732779" else "+33381901069"
                    )
                    "Pause erledigt" -> activity.serviceAction(TripTrackingService.ACTION_BREAK_DONE)
                    "Zur Tankstelle" -> s.fuelSuggestion?.let {
                        activity.openPointRoute(it.point, it.name)
                    }
                    else -> activity.openMaps(s.stage)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp)
        ) { Text(action) }
    }
}

@Composable
private fun FuelSuggestionCard(activity: MainActivity, fuel: FuelSuggestion) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Lamp(if (fuel.pricePerLitre != null) Light.GREEN else Light.YELLOW, 16.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Automatischer Tankvorschlag", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(fuel.name, style = MaterialTheme.typography.titleLarge)
                if (fuel.address.isNotBlank()) Text(fuel.address, color = Muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        StatusLine(
            "Preis",
            fuel.pricePerLitre?.let { "${"%.3f".format(it)} €/l Diesel" } ?: "Live-Preis für dieses Land nicht verfügbar",
            if (fuel.pricePerLitre != null) Light.GREEN else Light.GREY
        )
        StatusLine("Entfernung", "${"%.1f".format(fuel.distanceAheadKm)} km voraus", Light.GREEN)
        StatusLine("Umweg", "ca. ${"%.1f".format(fuel.detourKm)} km gesamt", if (fuel.detourKm <= 4) Light.GREEN else Light.YELLOW)
        StatusLine("Quelle", fuel.source, Light.GREY)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = { activity.openPointRoute(fuel.point, fuel.name) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Route") }
            OutlinedButton(
                onClick = { activity.serviceAction(TripTrackingService.ACTION_REFUEL_FULL) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Vollgetankt") }
        }
    }
}

@Composable
private fun AutomaticStatus(s: TripSnapshot, tokenValid: Boolean) {
    AppCard {
        Text("Systemstatus", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        StatusLine("Ankunft", lightText(s.scheduleLight), s.scheduleLight)
        StatusLine(
            "Verkehr",
            s.trafficDelayMin?.let { if (it == 0) "normal" else "+$it Min." } ?: "unbekannt",
            trafficLight(s.trafficDelayMin)
        )
        StatusLine(
            "Pause",
            if (s.driveMinutes < 135) "im Rahmen" else minuteText(s.driveMinutes),
            s.pauseLight
        )
        StatusLine(
            "Tankplanung",
            s.fuelSuggestion?.let {
                val price = it.pricePerLitre?.let { p -> "${"%.3f".format(p)} €/l · " }.orEmpty()
                "$price${it.name} · ${"%.1f".format(it.distanceAheadKm)} km voraus"
            } ?: if (s.active) "wird automatisch entlang der Route geprüft" else "startet mit dem Tracking",
            if (s.fuelSuggestion != null) Light.GREEN else Light.GREY
        )
        StatusLine(
            "Live-API",
            when {
                s.apiOk -> "Mapbox-Verkehr aktiv"
                tokenValid -> "Token geprüft · Fahrt starten"
                else -> s.apiMessage
            },
            when {
                s.apiOk -> Light.GREEN
                tokenValid -> Light.GREEN
                else -> Light.YELLOW
            }
        )
    }
}

private fun trafficLight(delay: Int?): Light = when {
    delay == null -> Light.GREY
    delay < 15 -> Light.GREEN
    delay < 45 -> Light.YELLOW
    else -> Light.RED
}
