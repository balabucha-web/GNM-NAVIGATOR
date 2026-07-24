package de.balabucha.reisepilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class HomeMode55 { PRE_TRIP, DRIVE, ARRIVAL, STAY }

private data class HomePreview55(
    val route: RouteResult? = null,
    val loading: Boolean = false,
    val error: String = ""
)

private data class HomeRecommendation55(
    val title: String,
    val text: String,
    val actionLabel: String,
    val action: HomeAction55
)

private enum class HomeAction55 { ROAD_AHEAD, PACKING, DESTINATIONS, MAP, ASSISTANT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard55(
    activity: MainActivity,
    snapshot: TripSnapshot,
    modifier: Modifier,
    onOpenTab: (AppTab) -> Unit
) {
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    var assistantIntent by rememberSaveable { mutableStateOf(AssistantIntent52.WHAT_TODAY) }
    var roadAheadOpen by rememberSaveable { mutableStateOf(false) }
    var classicOpen by rememberSaveable { mutableStateOf(false) }
    var selectedStage by rememberSaveable(snapshot.stage) { mutableStateOf(snapshot.stage) }
    val context = activity.applicationContext
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val packingRepository = remember { PackingRepository(context) }
    val packingProgress = PackingLogic.progress(packingRepository.state.items)
    val openPacking = (packingProgress.total - packingProgress.done).coerceAtLeast(0)
    val roadState = RoadAheadStore54.state
    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
    val mode = homeMode55(snapshot, location)
    var manualNight by rememberSaveable { mutableStateOf(false) }
    val automaticNight = LocalTime.now().hour !in 6..20
    val night = manualNight || automaticNight
    val background = if (night) Color(0xFF07111E) else Bg
    val surface = if (night) Color(0xFF102033) else Color.White
    val primaryText = if (night) Color(0xFFF3F7FA) else Navy
    val secondaryText = if (night) Color(0xFFB8C7D5) else Muted

    if (assistantOpen) {
        HomeAssistant55(
            activity = activity,
            snapshot = snapshot,
            weather = remember { RouteWeather55() },
            modifier = modifier,
            initialIntent = assistantIntent,
            onBack = { assistantOpen = false }
        )
        return
    }
    if (classicOpen) {
        BackHandler { classicOpen = false }
        Box(modifier.fillMaxSize()) {
            VisualLiveScreen54(activity, snapshot, Modifier.fillMaxSize())
            SmallFloatingActionButton(
                onClick = { classicOpen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
                containerColor = Navy,
                contentColor = Color.White
            ) { Text("×", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        return
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) activity.startTrip(selectedStage)
    }
    fun startTrip() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            activity.startTrip(selectedStage)
        } else {
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }
    }

    val displayStage = if (snapshot.active) snapshot.stage else selectedStage
    val token = normalizeMapboxToken(settings.getString("mapbox_token", "").orEmpty())
    val tokenValid = mapboxTokenLooksValid(token)
    val useLiveRoute = snapshot.active && snapshot.routeGeoJson.isNotBlank()
    val preview by produceState(
        initialValue = HomePreview55(loading = tokenValid && !useLiveRoute),
        token,
        displayStage,
        useLiveRoute
    ) {
        if (!tokenValid || useLiveRoute) {
            value = HomePreview55()
        } else {
            value = HomePreview55(loading = true)
            val result = withContext(Dispatchers.IO) {
                runCatching { MapboxClient.route(token, TripConfig.origin(displayStage), TripConfig.destination(displayStage)) }
            }
            value = result.fold(
                onSuccess = { HomePreview55(route = it) },
                onFailure = { HomePreview55(error = it.message.orEmpty().take(120)) }
            )
        }
    }
    val routePoints = remember(snapshot.routeGeoJson, preview.route) {
        if (snapshot.routeGeoJson.isNotBlank()) RouteAheadTools54.parseRoute(snapshot.routeGeoJson)
        else preview.route?.geometry.orEmpty()
    }
    val weatherTargets = remember(location?.lat, location?.lon, displayStage, routePoints.size, snapshot.etaEpochMs) {
        weatherTargets55(location, routePoints, displayStage, snapshot.etaEpochMs)
    }
    val weatherKey = weatherTargets.joinToString("|") { "${it.point.lat}:${it.point.lon}:${it.hourOffset}" }
    val weather by produceState(initialValue = RouteWeather55(), weatherKey) {
        while (true) {
            value = withContext(Dispatchers.IO) { WeatherClient55.query(context, weatherTargets) }
            delay(15L * 60L * 1_000L)
        }
    }
    val recommendedRegion = remember(location?.lat, location?.lon) {
        DestinationCatalog.nearestRegion(location) ?: if (LocalDate.now().isAfter(LocalDate.of(2026, 8, 2))) TravelRegion.PARIS else TravelRegion.CANET
    }
    val recommendedPlaces = remember(recommendedRegion, weather.current?.weatherCode) {
        val rainy = (weather.current?.rainProbability ?: 0) >= 55 || weatherLabel55(weather.current?.weatherCode) in setOf("Regen", "Gewitter")
        DestinationCatalog.places
            .filter { it.region == recommendedRegion }
            .sortedWith(
                compareByDescending<TravelPlace> { if (rainy && it.kind == PlaceKind.RAIN) 1 else 0 }
                    .thenByDescending { it.priority }
            )
            .take(3)
    }
    val heroPlace = recommendedPlaces.firstOrNull()
    val savedParkings = remember { ParkingSelectionStore.all(context) }
    val displaySnapshot = if (displayStage == snapshot.stage) snapshot else snapshot.copy(
        stage = displayStage,
        lat = null,
        lon = null,
        routeGeoJson = "",
        congestionJson = "[]",
        tolls = emptyList()
    )
    val recommendation = homeRecommendation55(snapshot, roadState, weather, openPacking, mode)

    Box(modifier.fillMaxSize().background(background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("page-list:Start"),
            contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = if (snapshot.active) 190.dp else 108.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Start", color = primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Dein Reise-Cockpit", color = secondaryText, fontSize = 13.sp)
                    }
                    FilterChip(
                        selected = night,
                        onClick = { manualNight = !manualNight },
                        label = { Text(if (night) "Nachtansicht" else "Tagansicht") }
                    )
                }
            }
            item {
                HomeHero55(
                    activity = activity,
                    snapshot = displaySnapshot,
                    mode = mode,
                    preview = preview.route,
                    savedParkings = savedParkings,
                    roadState = roadState,
                    weather = weather,
                    heroPlace = heroPlace,
                    night = night,
                    onOpenMap = { onOpenTab(AppTab.ROUTE) }
                )
            }
            if (!snapshot.active && mode == HomeMode55.PRE_TRIP) {
                item {
                    TripStart55(
                        selectedStage = selectedStage,
                        onStage = { selectedStage = it },
                        onStart = ::startTrip,
                        night = night
                    )
                }
            }
            item {
                HomeSectionTitle55("Fahrt auf einen Blick", if (snapshot.active) dataAge55(roadState.dataUpdatedAt) else "Reisevorbereitung", primaryText, secondaryText)
            }
            item {
                HomeMetrics55(snapshot, roadState, openPacking, night)
            }
            item {
                HomeSectionTitle55("Was kommt als Nächstes?", "in Fahrtrichtung", primaryText, secondaryText)
            }
            item {
                NextThings55(
                    roadState = roadState,
                    snapshot = snapshot,
                    night = night,
                    onOpen = { roadAheadOpen = true },
                    onPacking = { onOpenTab(AppTab.PACKING) }
                )
            }
            item {
                RecommendationCard55(
                    recommendation = recommendation,
                    night = night,
                    onAction = { action ->
                        when (action) {
                            HomeAction55.ROAD_AHEAD -> roadAheadOpen = true
                            HomeAction55.PACKING -> onOpenTab(AppTab.PACKING)
                            HomeAction55.DESTINATIONS -> onOpenTab(AppTab.DISCOVER)
                            HomeAction55.MAP -> onOpenTab(AppTab.ROUTE)
                            HomeAction55.ASSISTANT -> {
                                assistantIntent = AssistantIntent52.WHAT_TODAY
                                assistantOpen = true
                            }
                        }
                    }
                )
            }
            item {
                QuickActions55(
                    mode = mode,
                    night = night,
                    onNavigation = { activity.openMaps(displayStage) },
                    onAssistant = {
                        assistantIntent = if (mode == HomeMode55.STAY) AssistantIntent52.DAY_PLAN else AssistantIntent52.DRIVE_REVIEW
                        assistantOpen = true
                    },
                    onFuel = { roadAheadOpen = true },
                    onPause = { roadAheadOpen = true },
                    onDestinations = { onOpenTab(AppTab.DISCOVER) },
                    onPacking = { onOpenTab(AppTab.PACKING) }
                )
            }
            item {
                TankGaugeCard55(snapshot, settings.getFloat("start_litres", 60f).toDouble(), night)
            }
            item {
                HomeSectionTitle55("Wetter entlang der Route", if (weather.fromCache) "Offline-Stand" else "Open-Meteo", primaryText, secondaryText)
            }
            item { WeatherStrip55(weather, night) }
            item {
                JourneyTimeline55(snapshot, night)
            }
            if (mode == HomeMode55.ARRIVAL || mode == HomeMode55.STAY) {
                item { ArrivalCard55(activity, snapshot, weather, recommendedRegion, night) }
            }
            item {
                HomeSectionTitle55(
                    if (mode == HomeMode55.STAY) "Heute in ${recommendedRegion.label}" else "Vorfreude und Tagesideen",
                    weather.current?.temperatureC?.let { "$it °C · ${weatherLabel55(weather.current?.weatherCode)}" }.orEmpty(),
                    primaryText,
                    secondaryText
                )
            }
            item {
                DestinationCarousel55(activity, location, recommendedPlaces, night)
            }
            item {
                OutlinedButton(
                    onClick = { classicOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("Klassisches Cockpit und alle Live-Details") }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                assistantIntent = AssistantIntent52.WHAT_TODAY
                assistantOpen = true
            },
            icon = { Text("AI", fontWeight = FontWeight.Black) },
            text = { Text("Reise-Assistent", fontWeight = FontWeight.Bold) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).testTag("open-reise-assistant"),
            containerColor = if (night) Color(0xFF1F6F8B) else Navy,
            contentColor = Color.White
        )
    }

    if (roadAheadOpen) {
        ModalBottomSheet(onDismissRequest = { roadAheadOpen = false }) {
            RoadAheadHomeSheet55(activity, roadState)
        }
    }
}

@Composable
private fun HomeHero55(
    activity: MainActivity,
    snapshot: TripSnapshot,
    mode: HomeMode55,
    preview: RouteResult?,
    savedParkings: List<SavedParking>,
    roadState: RoadAheadState54,
    weather: RouteWeather55,
    heroPlace: TravelPlace?,
    night: Boolean,
    onOpenMap: () -> Unit
) {
    val progress = routeProgress55(snapshot)
    val arrival = snapshot.etaEpochMs?.let { formatTime(it) } ?: "– – : – –"
    Card(
        Modifier.fillMaxWidth().height(310.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color(0xFFE7EFF3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (snapshot.active || preview != null) {
                NativeTripMap54(
                    snapshot = snapshot,
                    previewRoute = preview,
                    savedParkings = savedParkings,
                    roadState = roadState,
                    layers = MapLayers54(),
                    modifier = Modifier.fillMaxSize()
                )
            } else if (heroPlace != null) {
                DestinationOfflinePhoto(heroPlace, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(if (night) Color(0xFF173047) else SoftBlue))
            }

            Surface(
                color = Color(0xCC0C1D30),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (snapshot.active) Green else Color(0xFFFFD166), CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        when (mode) {
                            HomeMode55.PRE_TRIP -> "REISEVORBEREITUNG"
                            HomeMode55.DRIVE -> "LIVE-FAHRT"
                            HomeMode55.ARRIVAL -> "ANKUNFTSMODUS"
                            HomeMode55.STAY -> "URLAUB VOR ORT"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
            weather.current?.temperatureC?.let { temperature ->
                Surface(
                    color = Color(0xCCFFFFFF),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Text(
                        "$temperature °C · ${weatherLabel55(weather.current?.weatherCode)}",
                        color = Navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(11.dp).clickable(onClick = onOpenMap),
                colors = CardDefaults.cardColors(containerColor = Color(0xE60C1D30)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(if (snapshot.active) "ANKUNFT" else "NÄCHSTE ETAPPE", color = Color.White.copy(alpha = .64f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (snapshot.active) arrival else "${TripConfig.origin(snapshot.stage).name} → ${TripConfig.destination(snapshot.stage).name}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = if (snapshot.active) 27.sp else 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("REST", color = Color.White.copy(alpha = .64f), fontSize = 10.sp)
                            Text(snapshot.remainingKm?.let { "$it km" } ?: "bereit", color = Color(0xFFFFD166), fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFFFFD166),
                        trackColor = Color.White.copy(alpha = .18f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (snapshot.active) "${snapshot.distanceTravelledKm.roundToInt()} km gefahren" else "Karte antippen", color = Color.White.copy(alpha = .70f), fontSize = 10.sp)
                        Text("${(progress * 100).roundToInt()} %", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripStart55(selectedStage: Stage, onStage: (Stage) -> Unit, onStart: () -> Unit, night: Boolean) {
    HomeCard55(night) {
        Text("Etappe wählen", color = homeText55(night), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Stage.entries.forEachIndexed { index, stage ->
                SegmentedButton(
                    selected = selectedStage == stage,
                    onClick = { onStage(stage) },
                    shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size),
                    label = { Text(if (stage == Stage.SATURDAY) "Schwerin → Hotel" else "Hotel → Canet") }
                )
            }
        }
        val countdown = departureCountdown(Instant.now())
        Spacer(Modifier.height(10.dp))
        Text(
            if (countdown.started) "Die Reise kann gestartet werden." else "Noch ${countdown.days} Tage, ${countdown.hours} Std. und ${countdown.minutes} Min. bis zur Abfahrt.",
            color = homeMuted55(night),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(if (tripModeAt(Instant.now()) == TripMode.TEST) "Testfahrt starten" else "Reise starten")
        }
    }
}

@Composable
private fun HomeMetrics55(snapshot: TripSnapshot, road: RoadAheadState54, openPacking: Int, night: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeMetric55("Tempo", road.speedKmh?.let { "$it km/h" } ?: snapshot.speedKmh?.let { "$it km/h" } ?: "–", night, Modifier.weight(1f))
        HomeMetric55("Seit Pause", minuteText(snapshot.driveMinutes), night, Modifier.weight(1f))
        HomeMetric55("Verkehr", snapshot.trafficDelayMin?.let { if (it <= 0) "normal" else "+$it Min." } ?: "–", night, Modifier.weight(1f))
        HomeMetric55("Packen", if (openPacking > 0) "$openPacking offen" else "fertig", night, Modifier.weight(1f))
    }
}

@Composable
private fun HomeMetric55(label: String, value: String, night: Boolean, modifier: Modifier) {
    Surface(modifier, color = if (night) Color(0xFF102033) else Color.White, shape = RoundedCornerShape(17.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = homeMuted55(night), fontSize = 9.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, color = homeText55(night), fontSize = if (value.length > 9) 11.sp else 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NextThings55(
    roadState: RoadAheadState54,
    snapshot: TripSnapshot,
    night: Boolean,
    onOpen: () -> Unit,
    onPacking: () -> Unit
) {
    val service = roadState.nextService
    val parking = roadState.nextParking
    val fuel = roadState.nextFuel
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
        if (snapshot.active) {
            item {
                NextCard55(
                    badge = service?.kind?.label ?: "Raststätte",
                    title = service?.name ?: if (roadState.loadingRoadside) "Wird gesucht" else "Noch kein Treffer",
                    main = service?.distanceAheadKm?.let(::distanceKm54) ?: "–",
                    detail = service?.let { travelTime55(it.distanceAheadKm, roadState.speedKmh) + featureText55(it) }.orEmpty(),
                    accent = Color(0xFF6A4BBC),
                    night = night,
                    onClick = onOpen
                )
            }
            item {
                NextCard55(
                    badge = "Parkplatz",
                    title = parking?.name ?: if (roadState.loadingRoadside) "Wird gesucht" else "Noch kein Treffer",
                    main = parking?.distanceAheadKm?.let(::distanceKm54) ?: "–",
                    detail = parking?.let { travelTime55(it.distanceAheadKm, roadState.speedKmh) + featureText55(it) }.orEmpty(),
                    accent = Teal,
                    night = night,
                    onClick = onOpen
                )
            }
            item {
                NextCard55(
                    badge = "Diesel abseits Autobahn",
                    title = fuel?.name ?: if (roadState.loadingFuel) "Preise werden geladen" else "Noch kein Treffer",
                    main = fuel?.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €/l", it) } ?: fuel?.distanceAheadKm?.let(::distanceKm54) ?: "–",
                    detail = fuel?.let { "${distanceKm54(it.distanceAheadKm)} · ${String.format(Locale.GERMANY, "%.1f km Umweg", it.detourKm)}" }.orEmpty(),
                    accent = Green,
                    night = night,
                    onClick = onOpen
                )
            }
        } else {
            item { NextCard55("Vor Abfahrt", "Packliste prüfen", "${PackingRepository(LocalContextHolder55.context ?: return@item).state.items.count { !it.checked }} offen", "Alles Wichtige vor der Fahrt abhaken.", Blue, night, onPacking) }
            item { NextCard55("Nächste Etappe", TripConfig.destination(snapshot.stage).name, "bereit", "Route, Buchung und Ankunftsdaten liegen vor.", Teal, night, onOpen) }
            item { NextCard55("Live-Daten", "Rastplätze und Diesel", "bei Fahrt", "Punkte erscheinen automatisch in Fahrtrichtung.", Green, night, onOpen) }
        }
    }
}

private object LocalContextHolder55 {
    var context: Context? = null
}

@Composable
private fun NextCard55(
    badge: String,
    title: String,
    main: String,
    detail: String,
    accent: Color,
    night: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.width(230.dp).heightIn(min = 145.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
        shape = RoundedCornerShape(21.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Surface(color = accent.copy(alpha = .13f), shape = RoundedCornerShape(9.dp)) {
                Text(badge, color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(9.dp))
            Text(title, color = homeText55(night), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(main, color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
            if (detail.isNotBlank()) Text(detail, color = homeMuted55(night), fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun RecommendationCard55(recommendation: HomeRecommendation55, night: Boolean, onAction: (HomeAction55) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF153047) else Color(0xFFE3F3F0)),
        border = BorderStroke(1.dp, Teal.copy(alpha = .25f)),
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Text("REISEPILOT EMPFIEHLT", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(recommendation.title, color = homeText55(night), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(recommendation.text, color = homeMuted55(night))
            Spacer(Modifier.height(9.dp))
            TextButton(onClick = { onAction(recommendation.action) }, contentPadding = PaddingValues(0.dp)) {
                Text(recommendation.actionLabel)
            }
        }
    }
}

@Composable
private fun QuickActions55(
    mode: HomeMode55,
    night: Boolean,
    onNavigation: () -> Unit,
    onAssistant: () -> Unit,
    onFuel: () -> Unit,
    onPause: () -> Unit,
    onDestinations: () -> Unit,
    onPacking: () -> Unit
) {
    val stay = mode == HomeMode55.STAY
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction55(if (stay) "Tagesplan" else "Navigation", if (stay) "Drei passende Stopps" else "Google Maps öffnen", Blue, night, Modifier.weight(1f), if (stay) onAssistant else onNavigation)
            QuickAction55("Reise-AI", "Fragen oder sprechen", Teal, night, Modifier.weight(1f), onAssistant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction55(if (stay) "Ziele in der Nähe" else "Tanken", if (stay) "Bilder und Entfernungen" else "Preis und Umweg", Green, night, Modifier.weight(1f), if (stay) onDestinations else onFuel)
            QuickAction55(if (stay) "Packliste" else "Pause suchen", if (stay) "Offene Punkte" else "WC, Essen, Parkplatz", Color(0xFF6A4BBC), night, Modifier.weight(1f), if (stay) onPacking else onPause)
        }
    }
}

@Composable
private fun QuickAction55(title: String, detail: String, accent: Color, night: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = .20f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            Spacer(Modifier.height(9.dp))
            Text(title, color = homeText55(night), fontWeight = FontWeight.Black)
            Text(detail, color = homeMuted55(night), fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun TankGaugeCard55(snapshot: TripSnapshot, capacity: Double, night: Boolean) {
    val fraction = (snapshot.fuelLitres / capacity.coerceAtLeast(20.0)).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, label = "fuel-gauge-55")
    val consumption = 7.4
    val range = (snapshot.fuelLitres / consumption * 100.0).roundToInt()
    HomeCard55(night) {
        HomeSectionTitle55("Tank und Reichweite", "${(animated * 100).roundToInt()} %", homeText55(night), homeMuted55(night))
        Box(Modifier.fillMaxWidth().height(155.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 20.dp.toPx()
                val diameter = size.width.coerceAtMost(size.height * 2f) - stroke
                val topLeft = Offset((size.width - diameter) / 2f, size.height - diameter / 2f - stroke / 2f)
                drawArc(
                    color = if (night) Color(0xFF2A4359) else Line,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = when {
                        animated < .18f -> Red
                        animated < .35f -> Yellow
                        else -> Green
                    },
                    startAngle = 180f,
                    sweepAngle = 180f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val center = Offset(size.width / 2f, size.height - stroke)
                val angle = Math.toRadians((180f + 180f * animated).toDouble())
                val needleLength = diameter * .33f
                drawLine(
                    color = homeText55(night),
                    start = center,
                    end = Offset(center.x + kotlin.math.cos(angle).toFloat() * needleLength, center.y + kotlin.math.sin(angle).toFloat() * needleLength),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(homeText55(night), radius = 7.dp.toPx(), center = center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("ca. $range km", color = homeText55(night), fontWeight = FontWeight.Black, fontSize = 25.sp)
                Text("${String.format(Locale.GERMANY, "%.1f", snapshot.fuelLitres)} l geschätzt", color = homeMuted55(night), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WeatherStrip55(weather: RouteWeather55, night: Boolean) {
    HomeCard55(night) {
        if (weather.points.isEmpty()) {
            Text(weather.message.ifBlank { "Wetterdaten werden geladen." }, color = homeMuted55(night))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weather.points.forEach { point ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(point.label, color = homeMuted55(night), fontSize = 9.sp)
                        Text(point.temperatureC?.let { "$it°" } ?: "–", color = homeText55(night), fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Text(weatherLabel55(point.weatherCode), color = homeMuted55(night), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        point.rainProbability?.let { Text("Regen $it %", color = if (it >= 60) Blue else homeMuted55(night), fontSize = 9.sp) }
                        Text(point.place, color = homeMuted55(night), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(weather.message.ifBlank { "Aktualisiert ${dataAge55(weather.updatedAt)}" }, color = homeMuted55(night), fontSize = 10.sp)
                if (weather.sunset.isNotBlank()) Text("Sonnenuntergang ${weather.sunset}", color = homeMuted55(night), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun JourneyTimeline55(snapshot: TripSnapshot, night: Boolean) {
    val current = journeyIndex55(snapshot)
    HomeCard55(night) {
        HomeSectionTitle55("Deine Reise", "Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))
        Spacer(Modifier.height(14.dp))
        val labels = listOf("Schwerin", "Montbéliard", "Canet", "Paris", "Schwerin")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            labels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) Box(Modifier.weight(1f).height(3.dp).background(if (index <= current) Green else Line))
                        Box(
                            Modifier.size(if (index == current) 18.dp else 14.dp)
                                .background(if (index <= current) Green else if (night) Color(0xFF40556A) else Line, CircleShape)
                        )
                        if (index < labels.lastIndex) Box(Modifier.weight(1f).height(3.dp).background(if (index < current) Green else Line))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(label, color = if (index == current) Green else homeMuted55(night), fontSize = 9.sp, fontWeight = if (index == current) FontWeight.Black else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            when (current) {
                0 -> "Schwerin → Montbéliard"
                1 -> "Montbéliard → Canet"
                2 -> "Canet → Paris"
                else -> "Paris → Schwerin"
            },
            color = homeText55(night),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ArrivalCard55(activity: MainActivity, snapshot: TripSnapshot, weather: RouteWeather55, region: TravelRegion, night: Boolean) {
    val hotel = snapshot.stage == Stage.SATURDAY
    val title = if (hotel) "greet Hôtel Montbéliard" else if (region == TravelRegion.CANET) "Malibu Village" else "Nächstes Reiseziel"
    val rows = if (hotel) listOf(
        "Check-in ab 15:00 Uhr",
        "Frühstück ab 07:00 Uhr",
        "Parkplatz am Hotel"
    ) else listOf(
        "Check-in 16:00–19:00 Uhr",
        "nach vorherigem Anruf bis 23:00 Uhr",
        "100 € Kaution per Kreditkarte",
        "Bettwäsche und Handtücher nicht inklusive"
    )
    HomeCard55(night) {
        Text("ANKUNFTSMODUS", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(title, color = homeText55(night), style = MaterialTheme.typography.titleLarge)
        snapshot.etaEpochMs?.let { Text("Voraussichtliche Ankunft ${formatTime(it)} Uhr", color = Blue, fontWeight = FontWeight.Bold) }
        weather.points.lastOrNull()?.let { Text("Bei Ankunft etwa ${it.temperatureC ?: "–"} °C · ${weatherLabel55(it.weatherCode)}", color = homeMuted55(night)) }
        Spacer(Modifier.height(8.dp))
        rows.forEach { Text("• $it", color = homeText55(night), modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { activity.openMapSearch(title) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Navigation") }
            OutlinedButton(
                onClick = { activity.dial(if (hotel) "+33381901069" else "+33468732779") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Anrufen") }
        }
    }
}

@Composable
private fun DestinationCarousel55(activity: MainActivity, location: GeoPoint?, places: List<TravelPlace>, night: Boolean) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(places, key = { "home55:${it.title}" }) { place ->
            Card(
                Modifier.width(245.dp).height(190.dp).clickable { activity.openPointRoute(place.point, place.title) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color.White)
            ) {
                Box(Modifier.fillMaxSize()) {
                    DestinationOfflinePhoto(place, Modifier.fillMaxSize())
                    Card(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xD90C1D30)),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(place.title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append(place.duration)
                                    DestinationCatalog.distanceKm(location, place)?.let { append(" · ").append(String.format(Locale.GERMANY, "%.1f km", it)) }
                                },
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadAheadHomeSheet55(activity: MainActivity, state: RoadAheadState54) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 720.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Nächste Punkte in Fahrtrichtung", style = MaterialTheme.typography.headlineSmall)
            Text("Preise, Entfernung, Fahrzeit und Umweg", color = Muted)
        }
        item {
            Button(onClick = activity::refreshRoadAhead54, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.loadingFuel || state.loadingRoadside) "Live-Daten werden geladen" else "Jetzt aktualisieren")
            }
        }
        item { SectionTitle("Tankstellen", "abseits Autobahn") }
        if (state.fuelOptions.isEmpty()) {
            item { StatusLine("Noch keine Preise", state.message.ifBlank { "Während der Fahrt werden passende Stationen geladen." }, Light.GREY) }
        } else {
            items(state.fuelOptions.take(6), key = { "homefuel:${it.point.lat}:${it.point.lon}" }) { fuel ->
                AppCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(fuel.name, fontWeight = FontWeight.Bold)
                            Text("${distanceKm54(fuel.distanceAheadKm)} · ${travelTime55(fuel.distanceAheadKm, state.speedKmh)}", color = Muted)
                            Text(String.format(Locale.GERMANY, "%.1f km Umweg", fuel.detourKm), color = Muted, fontSize = 11.sp)
                        }
                        Text(fuel.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €/l", it) } ?: "Preis offen", color = Green, fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(onClick = { activity.openPointRoute(fuel.point, fuel.name) }, modifier = Modifier.fillMaxWidth()) { Text("Navigieren") }
                }
            }
        }
        item { SectionTitle("Raststätten und Parkplätze", "bis 120 km") }
        if (state.roadsideStops.isEmpty()) {
            item { StatusLine("Noch keine Punkte", "OpenStreetMap-Punkte erscheinen bei aktiver Route.", Light.GREY) }
        } else {
            items(state.roadsideStops.take(14), key = { "homestop:${it.id}" }) { stop ->
                AppCard {
                    Text("${stop.kind.label} · ${stop.name}", fontWeight = FontWeight.Bold)
                    Text("${distanceKm54(stop.distanceAheadKm)} · ${travelTime55(stop.distanceAheadKm, state.speedKmh)}${featureText55(stop)}", color = Muted)
                    OutlinedButton(onClick = { activity.openPointRoute(stop.point, stop.name) }, modifier = Modifier.fillMaxWidth()) { Text("Navigieren") }
                }
            }
        }
    }
}

@Composable
private fun HomeCard55(night: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color.White),
        shape = RoundedCornerShape(23.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}

@Composable
private fun HomeSectionTitle55(title: String, detail: String, text: Color, muted: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (detail.isNotBlank()) Text(detail, color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun homeMode55(snapshot: TripSnapshot, location: GeoPoint?): HomeMode55 {
    if (snapshot.active && (snapshot.remainingKm ?: Int.MAX_VALUE) <= 25) return HomeMode55.ARRIVAL
    if (snapshot.active) return HomeMode55.DRIVE
    val region = DestinationCatalog.nearestRegion(location)
    if (region != null && location != null && Geo.distanceM(location, region.center) <= region.radiusKm * 1_000.0) return HomeMode55.STAY
    return HomeMode55.PRE_TRIP
}

private fun routeProgress55(snapshot: TripSnapshot): Float {
    val remaining = snapshot.remainingKm?.toDouble() ?: return if (snapshot.active) 0.02f else 0f
    val total = snapshot.distanceTravelledKm.coerceAtLeast(0.0) + remaining
    return if (total <= 0.0) 0f else (snapshot.distanceTravelledKm / total).toFloat().coerceIn(0f, 1f)
}

private fun homeText55(night: Boolean): Color = if (night) Color(0xFFF3F7FA) else Navy
private fun homeMuted55(night: Boolean): Color = if (night) Color(0xFFB8C7D5) else Muted

private fun travelTime55(distanceKm: Double, speedKmh: Int?): String {
    val speed = (speedKmh ?: 90).coerceIn(35, 130)
    val minutes = ceil(distanceKm / speed * 60.0).toInt().coerceAtLeast(1)
    return if (minutes < 60) "ca. $minutes Min." else "ca. ${minutes / 60} Std. ${minutes % 60} Min."
}

private fun featureText55(stop: RoadsideStop54): String = buildList {
    if (stop.hasToilets) add("WC")
    if (stop.hasFood) add("Essen")
    if (stop.hasFuel) add("Tanken")
}.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ", separator = " · ").orEmpty()

private fun dataAge55(epoch: Long): String {
    if (epoch <= 0L) return "noch keine Live-Daten"
    val minutes = Duration.between(Instant.ofEpochMilli(epoch), Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "gerade aktualisiert"
        minutes < 60 -> "vor $minutes Min."
        else -> "vor ${minutes / 60} Std."
    }
}

private fun homeRecommendation55(
    snapshot: TripSnapshot,
    road: RoadAheadState54,
    weather: RouteWeather55,
    openPacking: Int,
    mode: HomeMode55
): HomeRecommendation55 {
    val rainAhead = weather.points.drop(1).firstOrNull { (it.rainProbability ?: 0) >= 65 }
    if (rainAhead != null) {
        return HomeRecommendation55(
            "Regen auf dem weiteren Weg",
            "Bei ${rainAhead.place} liegt die Regenwahrscheinlichkeit bei ${rainAhead.rainProbability} %. Eine Pause davor kann sinnvoll sein.",
            "Stopps prüfen",
            HomeAction55.ROAD_AHEAD
        )
    }
    if (snapshot.active && snapshot.driveMinutes >= 120) {
        val service = road.nextService
        return HomeRecommendation55(
            "Zeit für eine entspannte Pause",
            service?.let { "${it.name} liegt ${distanceKm54(it.distanceAheadKm)} voraus und bietet ${featureText55(it).removePrefix(" · ").ifBlank { "eine Pause" }}." }
                ?: "Die bisherige Fahrzeit ist lang genug für eine Pause. ReisePilot sucht bereits Rastplätze voraus.",
            "Pause auswählen",
            HomeAction55.ROAD_AHEAD
        )
    }
    road.nextFuel?.takeIf { snapshot.active && it.pricePerLitre != null && it.detourKm <= 5.0 }?.let { fuel ->
        return HomeRecommendation55(
            "Günstiger Diesel mit kleinem Umweg",
            "${fuel.name}: ${String.format(Locale.GERMANY, "%.3f €/l", fuel.pricePerLitre)} in ${distanceKm54(fuel.distanceAheadKm)}, etwa ${String.format(Locale.GERMANY, "%.1f km", fuel.detourKm)} Umweg.",
            "Tankstellen vergleichen",
            HomeAction55.ROAD_AHEAD
        )
    }
    if (mode == HomeMode55.STAY) {
        return HomeRecommendation55(
            "Heute bewusst nur wenige Ziele",
            "ReisePilot verbindet Wetter, Entfernung und Familiennutzen zu einem entspannten Tagesplan mit höchstens drei Stopps.",
            "Tagesplan öffnen",
            HomeAction55.ASSISTANT
        )
    }
    if (openPacking > 0) {
        return HomeRecommendation55(
            "Noch $openPacking Packlistenpunkte offen",
            "Vor der Abfahrt die offenen Einträge kurz prüfen. Bereits erledigte Dinge bleiben gespeichert.",
            "Packliste öffnen",
            HomeAction55.PACKING
        )
    }
    return HomeRecommendation55(
        "ReisePilot ist bereit",
        "Route, Tankanzeige, Stopps, Ziele und Reise-Assistent stehen für die nächste Etappe bereit.",
        "Route ansehen",
        HomeAction55.MAP
    )
}

private fun weatherTargets55(
    current: GeoPoint?,
    route: List<GeoPoint>,
    stage: Stage,
    etaEpochMs: Long?
): List<WeatherTarget55> {
    val origin = current ?: TripConfig.origin(stage)
    val destination = TripConfig.destination(stage)
    val usableRoute = if (route.size >= 4) route else listOf(origin, interpolate55(origin, destination, .35), interpolate55(origin, destination, .70), destination)
    val startIndex = current?.let { Geo.projectOnPolyline(it, usableRoute)?.segmentIndex } ?: 0
    val remainingCount = (usableRoute.lastIndex - startIndex).coerceAtLeast(1)
    val first = usableRoute[(startIndex + remainingCount / 3).coerceIn(0, usableRoute.lastIndex)]
    val second = usableRoute[(startIndex + remainingCount * 2 / 3).coerceIn(0, usableRoute.lastIndex)]
    val etaHours = etaEpochMs?.let { Duration.between(Instant.now(), Instant.ofEpochMilli(it)).toHours().toInt().coerceIn(1, 15) } ?: 4
    return listOf(
        WeatherTarget55("Jetzt", origin.name.ifBlank { "Standort" }, origin, 0),
        WeatherTarget55("+1 Std.", first.name.ifBlank { "Route" }, first, 1),
        WeatherTarget55("+2 Std.", second.name.ifBlank { "Route" }, second, 2),
        WeatherTarget55("Ziel", destination.name, destination, etaHours)
    )
}

private fun interpolate55(a: GeoPoint, b: GeoPoint, ratio: Double): GeoPoint = GeoPoint(
    lat = a.lat + (b.lat - a.lat) * ratio,
    lon = a.lon + (b.lon - a.lon) * ratio,
    name = "Route"
)

private fun journeyIndex55(snapshot: TripSnapshot): Int {
    if (snapshot.active) return if (snapshot.stage == Stage.SATURDAY) 0 else 1
    val today = LocalDate.now()
    return when {
        today.isBefore(LocalDate.of(2026, 7, 26)) -> 0
        today.isBefore(LocalDate.of(2026, 8, 3)) -> 1
        today.isBefore(LocalDate.of(2026, 8, 6)) -> 2
        else -> 3
    }
}
