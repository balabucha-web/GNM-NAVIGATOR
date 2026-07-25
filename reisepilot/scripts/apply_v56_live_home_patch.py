#!/usr/bin/env python3
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/de/balabucha/reisepilot/HomeDashboard55.kt"
text = path.read_text(encoding="utf-8")

replacements = [
    (
        'HomeSectionTitle55("Was kommt als Nächstes?", "in Fahrtrichtung", primaryText, secondaryText)',
        'HomeSectionTitle55("Was kommt als Nächstes?", roadState.scopeLabel, primaryText, secondaryText)'
    ),
    (
        '''    val fuel = roadState.nextFuel\n    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {\n        if (snapshot.active) {''',
        '''    val fuel = roadState.nextFuel\n    val showLive = snapshot.active || roadState.nearbyMode || roadState.loadingFuel || roadState.loadingRoadside ||\n        roadState.fuelOptions.isNotEmpty() || roadState.roadsideStops.isNotEmpty()\n    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {\n        if (showLive) {'''
    ),
    (
        'detail = service?.let { travelTime55(it.distanceAheadKm, roadState.speedKmh) + featureText55(it) }.orEmpty(),',
        'detail = service?.let { if (roadState.nearbyMode && !snapshot.active) "${distanceKm54(it.distanceAheadKm)} entfernt${featureText55(it)}" else travelTime55(it.distanceAheadKm, roadState.speedKmh) + featureText55(it) }.orEmpty(),'
    ),
    (
        'detail = parking?.let { travelTime55(it.distanceAheadKm, roadState.speedKmh) + featureText55(it) }.orEmpty(),',
        'detail = parking?.let { if (roadState.nearbyMode && !snapshot.active) "${distanceKm54(it.distanceAheadKm)} entfernt${featureText55(it)}" else travelTime55(it.distanceAheadKm, roadState.speedKmh) + featureText55(it) }.orEmpty(),'
    ),
    (
        'detail = fuel?.let { "${distanceKm54(it.distanceAheadKm)} · ${String.format(Locale.GERMANY, "%.1f km Umweg", it.detourKm)}" }.orEmpty(),',
        'detail = fuel?.let { if (roadState.nearbyMode && !snapshot.active) "${distanceKm54(it.distanceAheadKm)} entfernt" else "${distanceKm54(it.distanceAheadKm)} · ${String.format(Locale.GERMANY, "%.1f km Umweg", it.detourKm)}" }.orEmpty(),'
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
    if new in text:
        continue
    if old not in text:
        raise SystemExit(f"HomeDashboard55 patch marker missing: {old[:100]}")
    text = text.replace(old, new, 1)
    changed = True

if changed:
    path.write_text(text, encoding="utf-8")
    print("PASS: HomeDashboard55 patched for immediate nearby live data")
else:
    print("PASS: HomeDashboard55 already patched")
