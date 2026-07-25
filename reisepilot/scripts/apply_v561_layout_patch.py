#!/usr/bin/env python3
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/de/balabucha/reisepilot/HomeDashboard55.kt"
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"5.6.1 patch marker missing: {label}")
    text = text.replace(old, new, 1)


replace_once(
    '''    var classicOpen by rememberSaveable { mutableStateOf(false) }
    var selectedStage by rememberSaveable(snapshot.stage) { mutableStateOf(snapshot.stage) }
    val context = activity.applicationContext
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val packingRepository = remember { PackingRepository(context) }
    val packingProgress = PackingLogic.progress(packingRepository.state.items)
    val openPacking = (packingProgress.total - packingProgress.done).coerceAtLeast(0)
    val roadState = RoadAheadStore54.state
    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
''',
    '''    var classicOpen by rememberSaveable { mutableStateOf(false) }
    val context = activity.applicationContext
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val packingRepository = remember { PackingRepository(context) }
    val packingProgress = PackingLogic.progress(packingRepository.state.items)
    val openPacking = (packingProgress.total - packingProgress.done).coerceAtLeast(0)
    val roadState = RoadAheadStore54.state
    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
    val preferredStage = remember(snapshot.active, snapshot.stage, location?.lat, location?.lon) {
        preferredStage561(snapshot, location)
    }
    var selectedStage by rememberSaveable(preferredStage) { mutableStateOf(preferredStage) }
''',
    "preferred current stage"
)

replace_once(
    '''    val displaySnapshot = if (displayStage == snapshot.stage) snapshot else snapshot.copy(
        stage = displayStage,
        lat = null,
        lon = null,
        routeGeoJson = "",
        congestionJson = "[]",
        tolls = emptyList()
    )
''',
    '''    val displaySnapshot = if (snapshot.active && displayStage == snapshot.stage) snapshot else snapshot.copy(
        active = false,
        paused = false,
        stage = displayStage,
        lat = location?.lat,
        lon = location?.lon,
        speedKmh = null,
        remainingKm = null,
        etaEpochMs = null,
        routeGeoJson = "",
        congestionJson = "[]",
        tolls = emptyList()
    )
''',
    "clear stale route when trip inactive"
)

replace_once(
    '''                    FilterChip(
                        selected = night,
                        onClick = { manualNight = !manualNight },
                        label = { Text(if (night) "Nachtansicht" else "Tagansicht") }
                    )
''',
    '''                    Column(horizontalAlignment = Alignment.End) {
                        FilterChip(
                            selected = night,
                            onClick = { manualNight = !manualNight },
                            label = { Text(if (night) "Nachtansicht" else "Tagansicht") }
                        )
                        TextButton(
                            onClick = {
                                assistantIntent = AssistantIntent52.WHAT_TODAY
                                assistantOpen = true
                            },
                            modifier = Modifier.testTag("open-reise-assistant")
                        ) { Text("AI-Assistent") }
                    }
''',
    "assistant in header"
)

replace_once(
    '''                )
            }
            if (!snapshot.active && mode == HomeMode55.PRE_TRIP) {
''',
    '''                )
            }
            item {
                CurrentLegCard561(
                    activity = activity,
                    stage = displayStage,
                    snapshot = snapshot,
                    preview = preview.route,
                    night = night
                )
            }
            if (!snapshot.active && mode == HomeMode55.PRE_TRIP) {
''',
    "current leg card insertion"
)

replace_once(
    'contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = if (snapshot.active) 190.dp else 108.dp),',
    'contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 118.dp),',
    "dashboard bottom spacing"
)

# Remove the floating assistant button that covered dashboard content. The tagged
# header action above keeps the assistant directly accessible and testable.
fab_start = text.find('        ExtendedFloatingActionButton(\n            onClick = {\n                assistantIntent = AssistantIntent52.WHAT_TODAY')
if fab_start >= 0:
    fab_end_marker = '''        )
    }

    if (roadAheadOpen)'''
    fab_end = text.find(fab_end_marker, fab_start)
    if fab_end < 0:
        raise SystemExit("5.6.1 floating assistant end marker missing")
    text = text[:fab_start] + '    }\n\n    if (roadAheadOpen)' + text[fab_end + len(fab_end_marker):]

# Replace the cramped four-column metrics with a clear 2x2 dashboard.
metrics_start = text.find('@Composable\nprivate fun HomeMetrics55(')
metrics_end = text.find('@Composable\nprivate fun HomeMetric55(', metrics_start)
if metrics_start < 0 or metrics_end < 0:
    raise SystemExit("5.6.1 HomeMetrics markers missing")
new_metrics = '''@Composable
private fun HomeMetrics55(snapshot: TripSnapshot, road: RoadAheadState54, openPacking: Int, night: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetric55("Tempo", road.speedKmh?.let { "$it km/h" } ?: snapshot.speedKmh?.let { "$it km/h" } ?: "–", night, Modifier.weight(1f))
            HomeMetric55("Seit letzter Pause", minuteText(snapshot.driveMinutes), night, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetric55("Verkehr", snapshot.trafficDelayMin?.let { if (it <= 0) "normal" else "+$it Min." } ?: "noch offen", night, Modifier.weight(1f))
            HomeMetric55("Packliste", if (openPacking > 0) "$openPacking offen" else "fertig", night, Modifier.weight(1f))
        }
    }
}

'''
text = text[:metrics_start] + new_metrics + text[metrics_end:]

# Replace partially clipped horizontal cards with full-width rows. This also
# preserves nearby results when no route is active and shows per-source messages.
next_start = text.find('@Composable\nprivate fun NextThings55(')
next_end = text.find('private object LocalContextHolder55', next_start)
if next_start < 0 or next_end < 0:
    raise SystemExit("5.6.1 NextThings markers missing")
new_next = '''@Composable
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
    val nearby = roadState.nearbyMode && !snapshot.active
    val showLive = snapshot.active || roadState.nearbyMode || roadState.loadingFuel || roadState.loadingRoadside ||
        roadState.fuelOptions.isNotEmpty() || roadState.roadsideStops.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (showLive) {
            NextRow561(
                badge = if (nearby) "Rastplatz in der Nähe" else service?.kind?.label ?: "Raststätte voraus",
                title = service?.name ?: if (roadState.loadingRoadside) "Raststätten werden gesucht" else "Keine Raststätte gefunden",
                main = service?.distanceAheadKm?.let(::distanceKm54) ?: "–",
                detail = service?.let {
                    if (nearby) "${distanceKm54(it.distanceAheadKm)} entfernt${featureText55(it)}"
                    else "${travelTime55(it.distanceAheadKm, roadState.speedKmh)}${featureText55(it)}"
                } ?: roadState.roadsideMessage.ifBlank { roadState.message },
                accent = Color(0xFF6A4BBC),
                night = night,
                onClick = onOpen
            )
            NextRow561(
                badge = if (nearby) "Parkplatz in der Nähe" else "Parkplatz voraus",
                title = parking?.name ?: if (roadState.loadingRoadside) "Parkplätze werden gesucht" else "Kein Parkplatz gefunden",
                main = parking?.distanceAheadKm?.let(::distanceKm54) ?: "–",
                detail = parking?.let {
                    if (nearby) "${distanceKm54(it.distanceAheadKm)} entfernt${featureText55(it)}"
                    else "${travelTime55(it.distanceAheadKm, roadState.speedKmh)}${featureText55(it)}"
                } ?: roadState.roadsideMessage.ifBlank { roadState.message },
                accent = Teal,
                night = night,
                onClick = onOpen
            )
            NextRow561(
                badge = if (nearby) "Diesel in der Nähe" else "Diesel abseits Autobahn",
                title = fuel?.name ?: if (roadState.loadingFuel) "Tankstellen werden geladen" else "Keine Tankstelle gefunden",
                main = fuel?.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €/l", it) }
                    ?: fuel?.distanceAheadKm?.let(::distanceKm54) ?: "–",
                detail = fuel?.let {
                    if (nearby) "${distanceKm54(it.distanceAheadKm)} entfernt"
                    else "${distanceKm54(it.distanceAheadKm)} · ${String.format(Locale.GERMANY, "%.1f km Umweg", it.detourKm)}"
                } ?: roadState.fuelMessage.ifBlank { roadState.message },
                accent = Green,
                night = night,
                onClick = onOpen
            )
        } else {
            NextRow561("Vor Abfahrt", "Packliste kurz prüfen", "öffnen", "Alles Wichtige vor der Fahrt abhaken.", Blue, night, onPacking)
            NextRow561("Aktuelle Teilstrecke", TripConfig.destination(snapshot.stage).name, "bereit", "Route und Ankunftsdaten der nächsten Etappe.", Teal, night, onOpen)
            NextRow561("Live-Daten", "Standortfreigabe erforderlich", "prüfen", roadState.message.ifBlank { "Tankstellen, Rastplätze und Parkplätze werden danach sofort geladen." }, Green, night, onOpen)
        }
    }
}

@Composable
private fun NextRow561(
    badge: String,
    title: String,
    main: String,
    detail: String,
    accent: Color,
    night: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (night) Color(0xFF102033) else Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
        shape = RoundedCornerShape(19.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(11.dp).background(accent, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(badge, color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text(title, color = homeText55(night), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, color = homeMuted55(night), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(main, color = accent, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
        }
    }
}

'''
text = text[:next_start] + new_next + text[next_end:]

replace_once(
    'HomeSectionTitle55("Deine Reise", "Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))',
    'HomeSectionTitle55("Gesamtreise", "Übersicht · Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))',
    "rename total journey overview"
)

# Insert the explicit current-leg card before the trip chooser.
trip_start_marker = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun TripStart55'
trip_start_index = text.find(trip_start_marker)
if trip_start_index < 0:
    raise SystemExit("5.6.1 TripStart marker missing")
if 'private fun CurrentLegCard561(' not in text:
    current_leg = '''@Composable
private fun CurrentLegCard561(
    activity: MainActivity,
    stage: Stage,
    snapshot: TripSnapshot,
    preview: RouteResult?,
    night: Boolean
) {
    val activeLeg = snapshot.active && snapshot.stage == stage
    val distanceKm = if (activeLeg) snapshot.remainingKm else preview?.distanceM?.div(1_000)
    val durationMin = if (activeLeg) {
        snapshot.etaEpochMs?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L).toInt() }
    } else preview?.durationSec?.div(60)
    val origin = TripConfig.origin(stage)
    val destination = TripConfig.destination(stage)

    HomeCard55(night) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AKTUELLE TEILSTRECKE", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("${origin.name} → ${destination.name}", color = homeText55(night), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    if (stage == Stage.SATURDAY) "Etappe 1 von 2 · Zwischenübernachtung in Montbéliard"
                    else "Etappe 2 von 2 · Hauptunterkunft in Canet-en-Roussillon",
                    color = homeMuted55(night),
                    fontSize = 11.sp
                )
            }
            Surface(color = Teal.copy(alpha = .13f), shape = RoundedCornerShape(12.dp)) {
                Text(if (activeLeg) "LIVE" else "BEREIT", color = Teal, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrentLegValue561("Bis zum Ziel", distanceKm?.let { "$it km" } ?: "Route wird geladen", night, Modifier.weight(1f))
            CurrentLegValue561("Fahrzeit", durationMin?.let(::minuteText) ?: "noch offen", night, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { activity.openMaps(stage) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Diese Teilstrecke navigieren") }
    }
}

@Composable
private fun CurrentLegValue561(label: String, value: String, night: Boolean, modifier: Modifier) {
    Surface(modifier, color = if (night) Color(0xFF173047) else Color(0xFFF1F6F8), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = homeMuted55(night), fontSize = 9.sp)
            Text(value, color = homeText55(night), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

'''
    text = text[:trip_start_index] + current_leg + text[trip_start_index:]

home_mode_marker = 'private fun homeMode55(snapshot: TripSnapshot, location: GeoPoint?): HomeMode55 {'
mode_index = text.find(home_mode_marker)
if mode_index < 0:
    raise SystemExit("5.6.1 homeMode marker missing")
if 'private fun preferredStage561(' not in text:
    preferred = '''private fun preferredStage561(snapshot: TripSnapshot, location: GeoPoint?): Stage {
    if (snapshot.active) return snapshot.stage
    if (location != null) {
        val toSchwerin = Geo.distanceM(location, TripConfig.schwerin)
        val toHotel = Geo.distanceM(location, TripConfig.hotel)
        val toCanet = Geo.distanceM(location, TripConfig.canet)
        if (toHotel <= 120_000.0 || toCanet <= 500_000.0) return Stage.SUNDAY
        if (toSchwerin <= 700_000.0 || location.lat >= 48.2) return Stage.SATURDAY
    }
    return if (LocalDate.now().isAfter(LocalDate.of(2026, 7, 25))) Stage.SUNDAY else Stage.SATURDAY
}

'''
    text = text[:mode_index] + preferred + text[mode_index:]

path.write_text(text, encoding="utf-8")
print("PASS: ReisePilot 5.6.1 current-leg dashboard and orderly live rows applied")
