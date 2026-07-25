#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/de/balabucha/reisepilot/HomeDashboard55.kt"
text = path.read_text(encoding="utf-8")

replacements = [
    (
        '    var selectedStage by rememberSaveable(snapshot.stage) { mutableStateOf(snapshot.stage) }',
        '''    val preferredStage = remember(snapshot.active, snapshot.stage, snapshot.lat, snapshot.lon) {
        preferredStage561(snapshot)
    }
    var selectedStage by rememberSaveable(preferredStage) { mutableStateOf(preferredStage) }'''
    ),
    (
        '''    val context = activity.applicationContext
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }''',
        '''    val context = activity.applicationContext
    val hasNearbyPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }'''
    ),
    (
        '''    val displaySnapshot = if (displayStage == snapshot.stage) snapshot else snapshot.copy(
        stage = displayStage,
        lat = null,
        lon = null,
        routeGeoJson = "",
        congestionJson = "[]",
        tolls = emptyList()
    )''',
        '''    val displaySnapshot = if (snapshot.active && displayStage == snapshot.stage) snapshot else snapshot.copy(
        active = false,
        paused = false,
        stage = displayStage,
        speedKmh = null,
        remainingKm = null,
        etaEpochMs = null,
        routeGeoJson = "",
        congestionJson = "[]",
        tolls = emptyList()
    )'''
    ),
    (
        '''                    FilterChip(
                        selected = night,
                        onClick = { manualNight = !manualNight },
                        label = { Text(if (night) "Nachtansicht" else "Tagansicht") }
                    )''',
        '''                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                assistantIntent = AssistantIntent52.WHAT_TODAY
                                assistantOpen = true
                            },
                            modifier = Modifier.heightIn(min = 42.dp).testTag("open-reise-assistant"),
                            contentPadding = PaddingValues(horizontal = 13.dp)
                        ) { Text("AI", fontWeight = FontWeight.Black) }
                        FilterChip(
                            selected = night,
                            onClick = { manualNight = !manualNight },
                            label = { Text(if (night) "Nacht" else "Tag") }
                        )
                    }'''
    ),
    (
        '''            item {
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
            if (!snapshot.active && mode == HomeMode55.PRE_TRIP) {''',
        '''            item {
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
            item {
                CurrentLegCard561(
                    snapshot = displaySnapshot,
                    stage = displayStage,
                    preview = preview.route,
                    night = night
                )
            }
            if (!hasNearbyPermission && !snapshot.active) {
                item {
                    HomeCard55(night) {
                        Text("Live-Daten am aktuellen Standort", color = homeText55(night), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Standort einmal freigeben. Danach lädt ReisePilot Tankstellen, Preise, Raststätten und Parkplätze direkt nach dem Öffnen – auch ohne gestartete Route.",
                            color = homeMuted55(night),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = activity::requestNearbyLocationPermission56,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Standort für Live-Daten freigeben") }
                    }
                }
            }
            if (!snapshot.active && mode == HomeMode55.PRE_TRIP) {'''
    ),
    (
        'HomeSectionTitle55("Fahrt auf einen Blick", if (snapshot.active) dataAge55(roadState.dataUpdatedAt) else "Reisevorbereitung", primaryText, secondaryText)',
        'HomeSectionTitle55("Aktuelle Etappe", if (snapshot.active) dataAge55(roadState.dataUpdatedAt) else "bis zum nächsten Ziel", primaryText, secondaryText)'
    ),
    (
        'HomeMetrics55(snapshot, roadState, openPacking, night)',
        'HomeMetrics561(snapshot, roadState, openPacking, displayStage, preview.route, night)'
    ),
    (
        'HomeSectionTitle55("Was kommt als Nächstes?", "in Fahrtrichtung", primaryText, secondaryText)',
        'HomeSectionTitle55("Live in deiner Umgebung", roadState.scopeLabel, primaryText, secondaryText)'
    ),
    (
        '''                NextThings55(
                    roadState = roadState,
                    snapshot = snapshot,
                    night = night,
                    onOpen = { roadAheadOpen = true },
                    onPacking = { onOpenTab(AppTab.PACKING) }
                )''',
        '''                NextThings561(
                    roadState = roadState,
                    snapshot = snapshot,
                    night = night,
                    onOpen = { roadAheadOpen = true },
                    onPacking = { onOpenTab(AppTab.PACKING) }
                )'''
    ),
    (
        'HomeSectionTitle55("Deine Reise", "Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))',
        'HomeSectionTitle55("Gesamtreise", "Übersicht · Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))'
    ),
    (
        'contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = if (snapshot.active) 190.dp else 108.dp),',
        'contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = if (snapshot.active) 150.dp else 102.dp),'
    ),
    (
        '''
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
        )''',
        ''
    ),
    (
        'Text("Nächste Punkte in Fahrtrichtung", style = MaterialTheme.typography.headlineSmall)',
        'Text(if (state.nearbyMode && !state.active) "Live-Daten in deiner Nähe" else "Nächste Punkte in Fahrtrichtung", style = MaterialTheme.typography.headlineSmall)'
    ),
    (
        'Text("Preise, Entfernung, Fahrzeit und Umweg", color = Muted)',
        'Text(if (state.nearbyMode && !state.active) "Entfernung und Preise rund um deinen Standort" else "Preise, Entfernung, Fahrzeit und Umweg", color = Muted)'
    ),
    (
        'item { SectionTitle("Tankstellen", "abseits Autobahn") }',
        'item { SectionTitle("Tankstellen", if (state.nearbyMode && !state.active) "in deiner Nähe" else "abseits Autobahn") }'
    ),
    (
        'Text("${distanceKm54(fuel.distanceAheadKm)} · ${travelTime55(fuel.distanceAheadKm, state.speedKmh)}", color = Muted)',
        'Text(if (state.nearbyMode && !state.active) "${distanceKm54(fuel.distanceAheadKm)} entfernt" else "${distanceKm54(fuel.distanceAheadKm)} · ${travelTime55(fuel.distanceAheadKm, state.speedKmh)}", color = Muted)'
    ),
    (
        'Text(String.format(Locale.GERMANY, "%.1f km Umweg", fuel.detourKm), color = Muted, fontSize = 11.sp)',
        'if (!(state.nearbyMode && !state.active)) Text(String.format(Locale.GERMANY, "%.1f km Umweg", fuel.detourKm), color = Muted, fontSize = 11.sp)'
    ),
    (
        'item { SectionTitle("Raststätten und Parkplätze", "bis 120 km") }',
        'item { SectionTitle("Raststätten und Parkplätze", if (state.nearbyMode && !state.active) "im Umkreis" else "bis 120 km") }'
    ),
    (
        'Text("${distanceKm54(stop.distanceAheadKm)} · ${travelTime55(stop.distanceAheadKm, state.speedKmh)}${featureText55(stop)}", color = Muted)',
        'Text(if (state.nearbyMode && !state.active) "${distanceKm54(stop.distanceAheadKm)} entfernt${featureText55(stop)}" else "${distanceKm54(stop.distanceAheadKm)} · ${travelTime55(stop.distanceAheadKm, state.speedKmh)}${featureText55(stop)}", color = Muted)'
    )
]

changed = False
for old, new in replacements:
    if new and new in text:
        continue
    if not new and old not in text:
        continue
    if old not in text:
        raise SystemExit(f"HomeDashboard55 patch marker missing: {old[:120]}")
    text = text.replace(old, new, 1)
    changed = True

# Some long-lived PR merge refs can contain a second copy of these legacy
# helpers. Keep exactly one definition so the generated Kotlin source remains
# deterministic and overload resolution cannot become ambiguous.
legacy_helper_blocks = [
    '''private fun travelTime55(distanceKm: Double, speedKmh: Int?): String {
    val speed = (speedKmh ?: 90).coerceIn(35, 130)
    val minutes = ceil(distanceKm / speed * 60.0).toInt().coerceAtLeast(1)
    return if (minutes < 60) "ca. $minutes Min." else "ca. ${minutes / 60} Std. ${minutes % 60} Min."
}''',
    '''private fun featureText55(stop: RoadsideStop54): String = buildList {
    if (stop.hasToilets) add("WC")
    if (stop.hasFood) add("Essen")
    if (stop.hasFuel) add("Tanken")
}.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ", separator = " · ").orEmpty()'''
]
for block in legacy_helper_blocks:
    while text.count(block) > 1:
        duplicate_at = text.rfind(block)
        text = text[:duplicate_at] + text[duplicate_at + len(block):]
        changed = True

if changed:
    path.write_text(text, encoding="utf-8")
    print("PASS: HomeDashboard55 patched for 5.6.1 current-leg and ordered live layout")
else:
    print("PASS: HomeDashboard55 already patched")

main_path = root / "app/src/main/java/de/balabucha/reisepilot/MainActivity.kt"
main_text = main_path.read_text(encoding="utf-8")
old_signature = "override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray)"
new_signature = "override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)"
if old_signature in main_text:
    main_path.write_text(main_text.replace(old_signature, new_signature, 1), encoding="utf-8")
    print("PASS: MainActivity permission callback signature patched")
elif new_signature in main_text:
    print("PASS: MainActivity permission callback signature already correct")
else:
    raise SystemExit("MainActivity permission callback marker missing")
