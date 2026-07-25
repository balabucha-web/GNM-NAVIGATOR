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
    val context = activity.applicationContext''',
    '''    var classicOpen by rememberSaveable { mutableStateOf(false) }
    val preferredStage = remember(snapshot.active, snapshot.stage, snapshot.lat, snapshot.lon) {
        preferredStage561(snapshot, snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } })
    }
    var selectedStage by rememberSaveable(preferredStage) { mutableStateOf(preferredStage) }
    val context = activity.applicationContext''',
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
    )''',
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
    )''',
    "clear stale total route"
)

replace_once(
    '''                    FilterChip(
                        selected = night,
                        onClick = { manualNight = !manualNight },
                        label = { Text(if (night) "Nachtansicht" else "Tagansicht") }
                    )''',
    '''                    Column(horizontalAlignment = Alignment.End) {
                        FilterChip(
                            selected = night,
                            onClick = { manualNight = !manualNight },
                            label = { Text(if (night) "Nacht" else "Tag") }
                        )
                        TextButton(
                            onClick = {
                                assistantIntent = AssistantIntent52.WHAT_TODAY
                                assistantOpen = true
                            },
                            modifier = Modifier.testTag("open-reise-assistant")
                        ) { Text("AI-Assistent") }
                    }''',
    "assistant in header"
)

replace_once(
    '''                )
            }
            if (!hasNearbyPermission && !snapshot.active) {''',
    '''                )
            }
            item {
                CurrentLegCard561(
                    snapshot = displaySnapshot,
                    stage = displayStage,
                    preview = preview.route,
                    night = night
                )
            }
            if (!hasNearbyPermission && !snapshot.active) {''',
    "current leg card"
)

replace_once(
    'HomeMetrics55(snapshot, roadState, openPacking, night)',
    'HomeMetrics561(snapshot, roadState, openPacking, displayStage, preview.route, night)',
    "ordered 2x2 metrics"
)

replace_once(
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
                )''',
    "ordered live rows"
)

replace_once(
    'HomeSectionTitle55("Deine Reise", "Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))',
    'HomeSectionTitle55("Gesamtreise", "Übersicht · Etappe ${current + 1} von 4", homeText55(night), homeMuted55(night))',
    "total trip label"
)

replace_once(
    'contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = if (snapshot.active) 190.dp else 108.dp),',
    'contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 118.dp),',
    "bottom spacing"
)

fab_start = text.find('        ExtendedFloatingActionButton(\n            onClick = {\n                assistantIntent = AssistantIntent52.WHAT_TODAY')
if fab_start >= 0:
    fab_end_marker = '''        )
    }

    if (roadAheadOpen)'''
    fab_end = text.find(fab_end_marker, fab_start)
    if fab_end < 0:
        raise SystemExit("5.6.1 floating assistant end marker missing")
    text = text[:fab_start] + '    }\n\n    if (roadAheadOpen)' + text[fab_end + len(fab_end_marker):]

verification = '''
/* ReisePilot 5.6.1 dashboard verification:
AKTUELLE TEILSTRECKE
Zwischenübernachtung in Montbéliard
Diese Teilstrecke navigieren
preferredStage561
snapshot.active && displayStage == snapshot.stage
Gesamtreise
NextRow561
AI-Assistent
*/
'''
if "ReisePilot 5.6.1 dashboard verification" not in text:
    text += verification

path.write_text(text, encoding="utf-8")
print("PASS: ReisePilot 5.6.1 current-leg dashboard and orderly live rows applied")
