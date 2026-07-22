package de.balabucha.reisepilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
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
    var now by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1_000L)
        }
    }

    val countdown = remember(now) { departureCountdown(now) }
    val availableMode = if (snapshot.active) snapshot.tripMode else tripModeAt(now)

    val bisonSummary by produceState(
        initialValue = BisonTrafficSummary(),
        chosen
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) { BisonTrafficClient.load(chosen) }
            delay(5L * 60L * 1000L)
        }
    }


    val germanTraffic by produceState(
        initialValue = GermanTrafficSummary(),
        chosen
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) { GermanTrafficClient.load(chosen) }
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

    Page("Start", "", modifier) {
        if (!countdown.started) item { VacationCountdownCard(countdown) }
        item {
            JourneyDashboardCard(
                activity = activity,
                snapshot = snapshot,
                chosen = chosen,
                availableMode = availableMode,
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
                    "Ankunftszeit",
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
        if (snapshot.active) item { NextAction(activity, snapshot) }
        snapshot.fuelSuggestion?.let { suggestion ->
            item { FuelSuggestionCard(activity, suggestion) }
        }
        item { GermanyTrafficCard(germanTraffic, chosen) }
        item { BisonTrafficCard(bisonSummary, chosen) }

        if (!snapshot.apiOk && !tokenValid) {
            item {
                WarningCard(
                    "Live-Verkehr nicht eingerichtet",
                    "Einstellungen → Karte und Live-Verkehr",
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
private fun VacationCountdownCard(countdown: DepartureCountdown) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vacation-countdown"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF10264A),
                            Color(0xFF176B87),
                            Color(0xFF00A6A6)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = Color(0xFFFFD166).copy(alpha = .24f),
                    radius = size.minDimension * .31f,
                    center = Offset(size.width * .92f, size.height * .04f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = .07f),
                    radius = size.minDimension * .43f,
                    center = Offset(size.width * .05f, size.height * 1.08f)
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Text(
                    "ABFAHRT IN",
                    color = Color(0xFFE7FBFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .5.sp
                )
                Spacer(Modifier.height(13.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CountdownUnit(countdown.days, "TAGE", Modifier.weight(1f))
                    CountdownUnit(countdown.hours, "STD.", Modifier.weight(1f))
                    CountdownUnit(countdown.minutes, "MIN.", Modifier.weight(1f))
                    CountdownUnit(countdown.seconds, "SEK.", Modifier.weight(1f))
                }

                Spacer(Modifier.height(11.dp))
                Text(
                    "Samstag, 25. Juli 2026 · 09:00 Uhr",
                    color = Color(0xFFD9F4F5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = .14f), RoundedCornerShape(15.dp))
            .padding(horizontal = 5.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString().padStart(2, '0'),
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            label,
            color = Color(0xFFD9F4F5),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun JourneyDashboardCard(
    activity: MainActivity,
    snapshot: TripSnapshot,
    chosen: Stage,
    availableMode: TripMode,
    onChosen: (Stage) -> Unit,
    onStart: () -> Unit
) {
    val origin = TripConfig.origin(chosen)
    val destination = TripConfig.destination(chosen)
    val isCurrentStage = snapshot.stage == chosen
    val storedTestIsExpired = !snapshot.active && snapshot.tripMode == TripMode.TEST &&
        availableMode == TripMode.REAL
    val hasTripData = isCurrentStage && !storedTestIsExpired && (
        snapshot.active || snapshot.distanceTravelledKm > 0.01 ||
            snapshot.driveMinutes > 0 || snapshot.remainingKm != null
        )
    val travelled = if (hasTripData) snapshot.distanceTravelledKm.coerceAtLeast(0.0) else 0.0
    val remaining = if (hasTripData) snapshot.remainingKm?.toDouble() else null
    val total = remaining?.let { travelled + it }
    val progress = if (total != null && total > 1.0) (travelled / total).toFloat().coerceIn(0f, 1f) else 0f
    val mode = if (snapshot.active || hasTripData) snapshot.tripMode else availableMode

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(if (mode == TripMode.TEST) "TESTFAHRT" else "ECHTE REISE")
                        append(if (chosen == Stage.SATURDAY) " · SAMSTAG" else " · SONNTAG")
                    },
                    color = if (mode == TripMode.TEST) Yellow else Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        snapshot.active && snapshot.paused -> "Pause aktiv"
                        snapshot.active && mode == TripMode.TEST -> "Testaufzeichnung läuft"
                        snapshot.active -> "Reiseaufzeichnung läuft"
                        mode == TripMode.TEST -> "Testfahrt bereit"
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
                if (hasTripData) "${"%.0f".format(travelled)} km gefahren" else "Noch nicht gestartet",
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                if (hasTripData && remaining != null) "${(progress * 100).roundToInt()} % · ${remaining.roundToInt()} km offen" else "0 %",
                color = Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!snapshot.active) {
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
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (!snapshot.active) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (availableMode == TripMode.TEST) Yellow else Green
                    ),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (availableMode == TripMode.TEST) "Testfahrt starten" else "Reise starten")
                }
            }
            OutlinedButton(
                onClick = { activity.openMaps(chosen) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Route in Google Maps") }
        }
        if (snapshot.active) {
            OutlinedButton(
                onClick = { activity.serviceAction(TripTrackingService.ACTION_STOP) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Aufzeichnung beenden") }
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
private fun GermanyTrafficCard(summary: GermanTrafficSummary, stage: Stage) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Deutschland-Verkehr live", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (stage == Stage.SATURDAY) "Autobahn-App-Daten · A24, A10, A9, A6 und A5" else "Deutsche Etappe ist am Sonntag nicht aktiv",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            AssistChip(onClick = {}, label = { Text(if (stage == Stage.SATURDAY) "LIVE" else "–") })
        }
        if (summary.updated.isNotBlank()) {
            Text("Stand: ${summary.updated}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        when {
            stage != Stage.SATURDAY -> StatusLine("Deutschland", "Für die Sonntagsroute sind die französischen Meldungen relevant", Light.GREY)
            summary.error != null -> WarningCard("Deutschland-Verkehr nicht erreichbar", summary.error, Light.YELLOW)
            summary.events.isEmpty() -> StatusLine("Route", "Keine aktuelle Warnung, Sperrung oder Baustellenmeldung auf den vorgesehenen Autobahnen", Light.GREEN)
            else -> summary.events.take(5).forEach { event ->
                val light = when (event.severity) {
                    3 -> Light.RED
                    2 -> Light.YELLOW
                    else -> Light.GREY
                }
                StatusLine("${event.road} · ${event.title}", event.detail, light)
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
            s.nextTitle.contains("anrufen", true) -> "Unterkunft anrufen"
            s.nextTitle.contains("Pause", true) -> "Pause erledigt"
            fuelAction -> "Zur Tankstelle navigieren"
            else -> "Route in Google Maps"
        }

        Button(
            onClick = {
                when (action) {
                    "Unterkunft anrufen" -> activity.dial(
                        if (s.stage == Stage.SUNDAY) "+33468732779" else "+33381901069"
                    )
                    "Pause erledigt" -> activity.serviceAction(TripTrackingService.ACTION_BREAK_DONE)
                    "Zur Tankstelle navigieren" -> s.fuelSuggestion?.let {
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
            ) { Text("Navigieren") }
            OutlinedButton(
                onClick = { activity.serviceAction(TripTrackingService.ACTION_REFUEL_FULL) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Vollgetankt") }
        }
    }
}

private fun trafficLight(delay: Int?): Light = when {
    delay == null -> Light.GREY
    delay < 15 -> Light.GREEN
    delay < 45 -> Light.YELLOW
    else -> Light.RED
}
