package de.balabucha.reisepilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun VisualLiveScreen51(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var detailed by rememberSaveable { mutableStateOf(false) }
    if (detailed) {
        BackHandler { detailed = false }
        Box(modifier.fillMaxSize()) {
            LiveScreen(activity, snapshot, Modifier.fillMaxSize())
            SmallFloatingActionButton(
                onClick = { detailed = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
                containerColor = Navy,
                contentColor = Color.White
            ) { Text("×", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        return
    }

    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val tankCapacity = settings.getFloat("start_litres", 60f).toDouble().coerceAtLeast(20.0)
    var chosen by rememberSaveable(snapshot.stage) { mutableStateOf(snapshot.stage) }
    val mode = if (snapshot.active) snapshot.tripMode else tripModeAt(Instant.now())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) activity.startTrip(chosen)
    }

    val startTrip = {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) activity.startTrip(chosen) else permissionLauncher.launch(permissions)
    }

    Page("Start", "Dein Reise-Cockpit", modifier) {
        item { RoadTripCockpit51(snapshot, chosen, mode) }
        if (snapshot.active) {
            item { ActiveDriveCard51(activity, snapshot) }
        } else {
            item { TripSetupCard51(chosen, { chosen = it }, startTrip) }
        }
        item { VisualMetricPanel51(snapshot) }
        item { VisualTankPanel51(snapshot, tankCapacity) }
        snapshot.fuelSuggestion?.let { fuel ->
            item {
                AppCard {
                    SectionTitle(
                        "Nächster sinnvoller Tankstopp",
                        fuel.pricePerLitre?.let { "${"%.3f".format(it)} €/l" } ?: "Preis wird geladen"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(fuel.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${"%.1f".format(fuel.distanceAheadKm)} km voraus · ${"%.1f".format(fuel.detourKm)} km Umweg",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { activity.openPointRoute(fuel.point, fuel.name) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Zur Tankstelle navigieren") }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { detailed = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Alle Live-Details und Verkehrsdaten") }
        }
    }
}

@Composable
private fun RoadTripCockpit51(snapshot: TripSnapshot, chosen: Stage, mode: TripMode) {
    val origin = TripConfig.origin(chosen)
    val destination = TripConfig.destination(chosen)
    val travelled = if (snapshot.stage == chosen) snapshot.distanceTravelledKm.coerceAtLeast(0.0) else 0.0
    val remaining = if (snapshot.stage == chosen) snapshot.remainingKm?.toDouble() else null
    val total = remaining?.let { travelled + it }
    val progress = if (total != null && total > 1.0) (travelled / total).toFloat().coerceIn(0f, 1f) else 0f
    val arrival = arrivalPresentation51(snapshot.etaEpochMs)
    val remainingTime = snapshot.etaEpochMs?.let {
        Duration.between(Instant.now(), Instant.ofEpochMilli(it)).toMinutes().coerceAtLeast(0)
    }

    Card(
        shape = RoundedCornerShape(29.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Navy, Color(0xFF17516B), Teal)))
                .padding(19.dp)
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = .07f), size.minDimension * .58f, Offset(size.width * .97f, size.height * .08f))
                drawCircle(Color(0xFFFFD166).copy(alpha = .16f), size.minDimension * .32f, Offset(size.width * .06f, size.height * 1.02f))
            }
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(11.dp)) {
                        Text(
                            if (mode == TripMode.TEST) "TESTMODUS" else "REISE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            snapshot.active && snapshot.paused -> "PAUSIERT"
                            snapshot.active -> "TRACKING AKTIV"
                            else -> "BEREIT"
                        },
                        color = if (snapshot.active && !snapshot.paused) Color(0xFF9CF2C5) else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(origin.name, color = Color.White.copy(alpha = .70f), fontSize = 13.sp)
                Text(
                    "→  ${destination.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("ANKUNFT", color = Color.White.copy(alpha = .66f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(arrival.time, color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp)
                        if (arrival.day.isNotBlank()) {
                            Text(
                                buildString {
                                    append(arrival.day)
                                    remainingTime?.let { append(" · noch ").append(durationText51(it)) }
                                },
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("RESTSTRECKE", color = Color.White.copy(alpha = .66f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(snapshot.remainingKm?.let { "$it km" } ?: "–", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Color(0xFFFFD166),
                    trackColor = Color.White.copy(alpha = .18f),
                    strokeCap = StrokeCap.Round
                )
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${"%.0f".format(travelled)} km gefahren", color = Color.White.copy(alpha = .74f), fontSize = 12.sp)
                    Text("${(progress * 100).roundToInt()} %", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

private data class ArrivalPresentation51(val time: String, val day: String)

private fun arrivalPresentation51(epoch: Long?): ArrivalPresentation51 {
    if (epoch == null) return ArrivalPresentation51("– – : – –", "")
    val zone = ZoneId.systemDefault()
    val eta = Instant.ofEpochMilli(epoch).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (eta.toLocalDate()) {
        today -> "heute"
        today.plusDays(1) -> "morgen"
        else -> eta.format(DateTimeFormatter.ofPattern("dd.MM."))
    }
    return ArrivalPresentation51(eta.format(DateTimeFormatter.ofPattern("HH:mm 'Uhr'")), day)
}

private fun durationText51(minutes: Long): String = when {
    minutes < 60 -> "$minutes Min."
    else -> "${minutes / 60} Std. ${(minutes % 60).toString().padStart(2, '0')} Min."
}

@Composable
private fun ActiveDriveCard51(activity: MainActivity, snapshot: TripSnapshot) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1D30))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(if (snapshot.paused) Yellow else Green, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (snapshot.paused) "FAHRMODUS · PAUSE" else "FAHRMODUS",
                    color = Color.White.copy(alpha = .70f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                snapshot.nextTitle,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (snapshot.paused) "Aufzeichnung pausiert" else snapshot.nextDetail,
                color = Color.White.copy(alpha = .68f),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            snapshot.nextDistanceM?.let {
                Spacer(Modifier.height(7.dp))
                Text("in ${distanceText(it)}", color = Color(0xFFFFD166), fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = { activity.serviceAction(TripTrackingService.ACTION_PAUSE) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(if (snapshot.paused) "Fortsetzen" else "Pause") }
                OutlinedButton(
                    onClick = { activity.serviceAction(TripTrackingService.ACTION_STOP) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = .45f))),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Beenden") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSetupCard51(chosen: Stage, onChosen: (Stage) -> Unit, onStart: () -> Unit) {
    AppCard {
        SectionTitle("Etappe wählen")
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Stage.entries.forEachIndexed { index, stage ->
                SegmentedButton(
                    selected = chosen == stage,
                    onClick = { onChosen(stage) },
                    shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size),
                    label = { Text(if (stage == Stage.SATURDAY) "Schwerin → Hotel" else "Hotel → Canet") }
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) {
            Text(if (tripModeAt(Instant.now()) == TripMode.TEST) "Testfahrt starten" else "Reise starten")
        }
    }
}

@Composable
private fun VisualMetricPanel51(snapshot: TripSnapshot) {
    val speedText = when (val speed = snapshot.speedKmh) {
        null -> "–"
        in 0..9 -> "steht / langsam"
        else -> "$speed km/h"
    }
    AppCard {
        SectionTitle("Fahrt auf einen Blick")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric51("Fahrzeit", minuteText(snapshot.driveMinutes), snapshot.pauseLight, Modifier.weight(1f))
            MiniMetric51(
                "Verkehr",
                snapshot.trafficDelayMin?.let { if (it <= 0) "normal" else "+$it Min." } ?: "–",
                when {
                    snapshot.trafficDelayMin == null -> Light.GREY
                    snapshot.trafficDelayMin <= 5 -> Light.GREEN
                    snapshot.trafficDelayMin <= 20 -> Light.YELLOW
                    else -> Light.RED
                },
                Modifier.weight(1f)
            )
            MiniMetric51("Tempo", speedText, Light.GREY, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMetric51(label: String, value: String, light: Light, modifier: Modifier) {
    Surface(modifier, color = statusColor(light).copy(alpha = .08f), shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                color = Navy,
                fontWeight = FontWeight.Black,
                fontSize = if (value.length > 9) 12.sp else 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VisualTankPanel51(snapshot: TripSnapshot, capacity: Double) {
    val fraction = (snapshot.fuelLitres / capacity).toFloat().coerceIn(0f, 1f)
    AppCard {
        SectionTitle("Geschätzter Tankinhalt", "${(fraction * 100).roundToInt()} %")
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(18.dp),
            color = statusColor(snapshot.fuelLight),
            trackColor = Line,
            strokeCap = StrokeCap.Round
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("E", color = Muted, fontWeight = FontWeight.Bold)
            Text("${"%.1f".format(snapshot.fuelLitres)} l geschätzt", color = Navy, fontWeight = FontWeight.Bold)
            Text("F", color = Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualDiscoverScreen51(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var detailed by rememberSaveable { mutableStateOf(false) }
    if (detailed) {
        BackHandler { detailed = false }
        Box(modifier.fillMaxSize()) {
            DiscoverScreen(activity, snapshot, Modifier.fillMaxSize())
            SmallFloatingActionButton(
                onClick = { detailed = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
                containerColor = Navy,
                contentColor = Color.White
            ) { Text("×", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        return
    }

    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
    val automaticRegion = remember(location?.lat, location?.lon) { DestinationCatalog.nearestRegion(location) }
    var region by rememberSaveable { mutableStateOf(automaticRegion ?: TravelRegion.CANET) }
    var kind by rememberSaveable { mutableStateOf<PlaceKind?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<TravelPlace?>(null) }
    var showRegionTips by rememberSaveable(region) { mutableStateOf(false) }

    val places = remember(region, kind, query, location?.lat, location?.lon) {
        DiscoverLogic.filter(region, kind, query, location, false)
    }

    Page("Ziele", "Große Bilder, kurze Entscheidung", modifier) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RegionChip51(TravelRegion.CANET, region, Modifier.weight(1f)) { region = it; kind = null; query = "" }
                    RegionChip51(TravelRegion.BARCELONA, region, Modifier.weight(1f)) { region = it; kind = null; query = "" }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RegionChip51(TravelRegion.ANDORRA, region, Modifier.weight(1f)) { region = it; kind = null; query = "" }
                    RegionChip51(TravelRegion.PARIS, region, Modifier.weight(1f)) { region = it; kind = null; query = "" }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = visualRegionColor51(region).copy(alpha = .13f))
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text(region.label, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(5.dp))
                    Text(regionSummary51(region), color = Navy, lineHeight = 20.sp)
                    if (showRegionTips) {
                        Spacer(Modifier.height(8.dp))
                        Text(region.shortPlan, color = Navy, fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(region.logistics, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    TextButton(onClick = { showRegionTips = !showRegionTips }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showRegionTips) "Weniger anzeigen" else "Reisetipps anzeigen")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Ziel suchen") },
                placeholder = { Text("Strand, Aquarium, Einkauf …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("Beste zuerst") }) }
                items(PlaceKind.entries, key = { it.name }) { item ->
                    FilterChip(selected = kind == item, onClick = { kind = item }, label = { Text(item.label) })
                }
            }
        }
        item {
            SectionTitle(
                "${places.size} passende Ziele",
                if (location != null) "ab aktuellem Standort" else region.label
            )
        }
        items(places, key = { "visual51:${it.region.name}:${it.title}" }) { place ->
            VisualPlaceCard51(place, DestinationCatalog.distanceKm(location, place)) { selected = place }
        }
        item {
            OutlinedButton(
                onClick = { detailed = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Erweiterte Ansicht mit Galerie und Parkplätzen") }
        }
    }

    selected?.let { place ->
        VisualPlaceSheet51(activity, place, DestinationCatalog.distanceKm(location, place)) { selected = null }
    }
}

@Composable
private fun RegionChip51(
    item: TravelRegion,
    selected: TravelRegion,
    modifier: Modifier,
    onSelect: (TravelRegion) -> Unit
) {
    FilterChip(
        selected = selected == item,
        onClick = { onSelect(item) },
        label = {
            Text(
                when (item) {
                    TravelRegion.CANET -> "Canet"
                    TravelRegion.BARCELONA -> "Barcelona"
                    TravelRegion.ANDORRA -> "Andorra"
                    TravelRegion.PARIS -> "Paris"
                },
                maxLines = 1
            )
        },
        modifier = modifier
    )
}

private fun regionSummary51(region: TravelRegion) = when (region) {
    TravelRegion.CANET -> "Strand, Collioure, Perpignan und starke Familienziele in der Umgebung."
    TravelRegion.BARCELONA -> "Stadt-Highlights mit festem Parkplatz und möglichst wenigen Ortswechseln."
    TravelRegion.ANDORRA -> "Aussichtspunkte, kurze Wanderungen und Einkauf ohne überladenen Tagesplan."
    TravelRegion.PARIS -> "Eiffelturm, Seine und ausgewählte Viertel – das Auto bleibt am Hotel."
}

@Composable
private fun VisualPlaceCard51(place: TravelPlace, distanceKm: Double?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(destinationCardTag(place)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(190.dp).background(visualRegionColor51(place.region))) {
                DestinationArtwork(place.region, Modifier.fillMaxSize())
                DestinationOfflinePhoto(place, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    VisualBadge51(place.kind.label, Navy.copy(alpha = .88f), Color.White)
                    distanceKm?.let { VisualBadge51("${"%.1f".format(it)} km", Color.White.copy(alpha = .92f), Navy) }
                }
            }
            Column(Modifier.padding(15.dp)) {
                Text(place.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(place.description, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VisualBadge51(place.duration, SoftBlue, Blue)
                    Spacer(Modifier.width(7.dp))
                    VisualBadge51("Details & Bilder", Green.copy(alpha = .10f), Green)
                    Spacer(Modifier.weight(1f))
                    Text("Öffnen ›", color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VisualBadge51(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(10.dp)) {
        Text(
            text,
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualPlaceSheet51(activity: MainActivity, place: TravelPlace, distanceKm: Double?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Bg) {
        Column(Modifier.fillMaxWidth().padding(bottom = 34.dp)) {
            Box(Modifier.fillMaxWidth().height(290.dp).background(visualRegionColor51(place.region))) {
                DestinationArtwork(place.region, Modifier.fillMaxSize())
                DestinationOfflinePhoto(place, Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Navy.copy(alpha = .92f))))
                        .padding(18.dp)
                ) {
                    Column {
                        Text(place.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                        Text(
                            buildString {
                                append(place.duration)
                                distanceKm?.let { append(" · ").append("%.1f".format(it)).append(" km ab hier") }
                            },
                            color = Color.White.copy(alpha = .76f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Column(Modifier.padding(18.dp)) {
                Text(place.description, color = Navy, lineHeight = 21.sp)
                if (place.tip.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    WarningCard("Praktischer Tipp", place.tip, Light.GREEN)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { activity.openPointRoute(place.point, place.title); onDismiss() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("Route in Google Maps") }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                    Text("Zurück zur Liste")
                }
            }
        }
    }
}

private fun visualRegionColor51(region: TravelRegion) = when (region) {
    TravelRegion.CANET -> Color(0xFFBFE7EA)
    TravelRegion.BARCELONA -> Color(0xFFF4D7B4)
    TravelRegion.ANDORRA -> Color(0xFFD4E3C4)
    TravelRegion.PARIS -> Color(0xFFD9D5EA)
}
