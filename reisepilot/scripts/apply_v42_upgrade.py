#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/de/balabucha/reisepilot"


def replace(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if old not in text:
        print(f"WARN {label}: block not found")
        return
    path.write_text(text.replace(old, new, 1))
    print(f"OK {label}")


def regex(path: Path, pattern: str, repl: str, label: str, flags: int = 0) -> None:
    text = path.read_text()
    updated, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count == 0:
        print(f"WARN {label}: pattern not found")
        return
    path.write_text(updated)
    print(f"OK {label}")


(SRC / "DestinationArtwork.kt").write_text(r'''package de.balabucha.reisepilot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun DestinationArtwork(region: TravelRegion, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sky = when (region) {
            TravelRegion.CANET -> Color(0xFFBDEBFA)
            TravelRegion.BARCELONA -> Color(0xFFFFD8A0)
            TravelRegion.ANDORRA -> Color(0xFFD6E8D5)
            TravelRegion.PARIS -> Color(0xFFD8D5EC)
        }
        drawRect(sky)
        drawCircle(Color(0xFFFFD166), w * .10f, Offset(w * .80f, h * .20f))
        when (region) {
            TravelRegion.CANET -> {
                drawRect(Color(0xFF3EA7C4), Offset(0f, h * .52f), Size(w, h * .30f))
                drawRect(Color(0xFFE8C982), Offset(0f, h * .82f), Size(w, h * .18f))
                repeat(3) { i ->
                    val y = h * (.58f + i * .08f)
                    drawLine(Color.White.copy(alpha = .85f), Offset(w * .05f, y), Offset(w * .95f, y), h * .018f)
                }
                val sail = Path().apply {
                    moveTo(w * .40f, h * .30f); lineTo(w * .40f, h * .62f); lineTo(w * .62f, h * .58f); close()
                }
                drawPath(sail, Color.White)
                drawLine(Color(0xFF364152), Offset(w * .40f, h * .27f), Offset(w * .40f, h * .66f), w * .018f)
            }
            TravelRegion.BARCELONA -> {
                drawRect(Color(0xFF587A9B), Offset(0f, h * .72f), Size(w, h * .28f))
                drawRect(Color(0xFF7A4E3A), Offset(w * .08f, h * .48f), Size(w * .22f, h * .32f))
                drawRect(Color(0xFFB45C3D), Offset(w * .70f, h * .42f), Size(w * .20f, h * .38f))
                val church = Path().apply {
                    moveTo(w * .36f, h * .78f); lineTo(w * .40f, h * .25f); lineTo(w * .44f, h * .78f)
                    moveTo(w * .48f, h * .78f); lineTo(w * .52f, h * .16f); lineTo(w * .56f, h * .78f)
                    moveTo(w * .60f, h * .78f); lineTo(w * .64f, h * .30f); lineTo(w * .68f, h * .78f)
                }
                drawPath(church, Color(0xFF5B3B2E), style = Stroke(w * .035f))
            }
            TravelRegion.ANDORRA -> {
                val mountains = Path().apply {
                    moveTo(0f, h * .72f); lineTo(w * .28f, h * .24f); lineTo(w * .48f, h * .70f)
                    lineTo(w * .72f, h * .18f); lineTo(w, h * .72f); close()
                }
                drawPath(mountains, Color(0xFF6F8D77))
                drawRect(Color(0xFF4D9AA5), Offset(0f, h * .72f), Size(w, h * .28f))
                drawLine(Color.White.copy(alpha = .75f), Offset(w * .12f, h * .82f), Offset(w * .88f, h * .82f), h * .018f)
            }
            TravelRegion.PARIS -> {
                drawRect(Color(0xFF8FB4C9), Offset(0f, h * .78f), Size(w, h * .22f))
                val tower = Path().apply {
                    moveTo(w * .50f, h * .16f); lineTo(w * .38f, h * .82f); lineTo(w * .45f, h * .82f)
                    lineTo(w * .50f, h * .58f); lineTo(w * .55f, h * .82f); lineTo(w * .62f, h * .82f); close()
                }
                drawPath(tower, Color(0xFF344054))
                drawLine(Color(0xFF344054), Offset(w * .40f, h * .63f), Offset(w * .60f, h * .63f), h * .024f)
                drawRect(Color(0xFF8E6F62), Offset(w * .05f, h * .58f), Size(w * .24f, h * .22f))
                drawRect(Color(0xFF8E6F62), Offset(w * .71f, h * .53f), Size(w * .24f, h * .27f))
            }
        }
    }
}
''')
print("OK local destination artwork")

discover = SRC / "DiscoverScreen.kt"
replace(
    discover,
    '        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n            Text(kindSymbol(place.kind)',
    '        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n            DestinationArtwork(place.region, Modifier.fillMaxSize())\n            Text(kindSymbol(place.kind)',
    "thumbnail fallback artwork"
)
regex(
    discover,
    r'(Modifier\.fillMaxWidth\(\)\.height\(230\.dp\)\.background\([\s\S]*?\)\n\s*\) \{\n)',
    r'\1                DestinationArtwork(place.region, Modifier.fillMaxSize())\n',
    "detail fallback artwork"
)

wiki = SRC / "WikiImage.kt"
replace(wiki, 'travel_images_v34', 'travel_images_v43', "fresh image cache")
replace(wiki, 'listOf(".pdf", ".djvu", ".tif", ".tiff")', 'listOf(".pdf", ".djvu", ".tif", ".tiff", ".svg")', "reject svg originals")
replace(wiki, 'ReisePilot/3.4 Android (family travel app)', 'ReisePilot/4.2 Android (family travel app)', "image user agent")

native = SRC / "NativeTripMap.kt"
replace(native, 'import android.os.Bundle\n', 'import android.os.Bundle\nimport android.view.MotionEvent\n', "map touch import")
replace(
    native,
    '        mapView = MapView(context)\n        mapView.onCreate(Bundle())',
    '''        mapView = MapView(context)
        mapView.isClickable = true
        mapView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        mapView.onCreate(Bundle())''',
    "map gesture interception"
)
replace(
    native,
    '            readyMap.uiSettings.isAttributionEnabled = true\n',
    '''            readyMap.uiSettings.isAttributionEnabled = true
            readyMap.uiSettings.isScrollGesturesEnabled = true
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isTiltGesturesEnabled = true
''',
    "map gesture settings"
)
replace(
    native,
    '        upsertPoints(style, "point-fallback-fuels", fallbackFuels, Color.rgb(84, 151, 116), 6f)',
    '        upsertPoints(style, "point-fallback-fuels", fallbackFuels, Color.rgb(126, 87, 194), 7f)',
    "distinct planned fuel color"
)
replace(
    native,
    '''        upsertPoints(
            style,
            "point-ends",
            listOf(TripConfig.origin(latestSnapshot.stage), TripConfig.destination(latestSnapshot.stage)),
            Color.rgb(24, 38, 63),
            7f
        )''',
    '''        upsertPoints(
            style,
            "point-start",
            listOf(TripConfig.origin(latestSnapshot.stage)),
            Color.rgb(24, 38, 63),
            8f
        )
        upsertPoints(
            style,
            "point-destination",
            listOf(TripConfig.destination(latestSnapshot.stage)),
            Color.rgb(180, 35, 24),
            10f
        )''',
    "separate start and destination markers"
)

screen = SRC / "MapScreen.kt"
replace(screen, '    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }\n', '    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }\n    var mapInstance by rememberSaveable { mutableIntStateOf(0) }\n', "map reset state")
replace(
    screen,
    '''        item {
            Card(
                Modifier.fillMaxWidth().height(480.dp),''',
    '''        item {
            Text("Mit einem Finger verschieben · mit zwei Fingern zoomen und drehen", color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        item {
            Card(
                Modifier.fillMaxWidth().height(520.dp),''',
    "map instructions"
)
replace(
    screen,
    '''                NativeTripMap(
                    snapshot = displaySnapshot,
                    previewRoute = preview.route,
                    modifier = Modifier.fillMaxSize()
                )''',
    '''                key(mapInstance) {
                    NativeTripMap(
                        snapshot = displaySnapshot,
                        previewRoute = preview.route,
                        modifier = Modifier.fillMaxSize()
                    )
                }''',
    "recreatable map"
)
replace(
    screen,
    '''        item {
            AppCard {
                Text("Legende", style = MaterialTheme.typography.titleMedium)
                StatusLine("Grün", "freie Strecke oder günstiger Tankpunkt", Light.GREEN)
                StatusLine("Gelb", "mäßiger Verkehr oder Mautpunkt", Light.YELLOW)
                StatusLine("Rot", "starker Verkehr oder sofort handeln", Light.RED)''',
    '''        item {
            OutlinedButton(
                onClick = { mapInstance++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Gesamte Route wieder anzeigen") }
        }

        item {
            AppCard {
                Text("Punkte auf der Karte", style = MaterialTheme.typography.titleMedium)
                StatusLine("Dunkelblau", "Startpunkt", Light.GREY)
                StatusLine("Rot", "Ziel", Light.RED)
                StatusLine("Blau", "dein aktueller Standort", Light.GREY)
                StatusLine("Gelb", "Mautstelle", Light.YELLOW)
                StatusLine("Lila", "geplanter Tankstopp", Light.GREY)
                StatusLine("Großes Grün", "aktuell empfohlene Tankstelle", Light.GREEN)
                val tolls = displaySnapshot.tolls.ifEmpty { preview.route?.tolls.orEmpty() }
                if (tolls.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mautpunkte", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    tolls.forEach { Text("• ${it.name}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Geplante Tankpunkte", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                TripConfig.fuelStops(stage).forEach { Text("• ${it.name}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                displaySnapshot.fuelSuggestion?.let { Text("• Empfohlen: ${it.name}", color = Green, style = MaterialTheme.typography.bodySmall) }''',
    "clear marker legend and names"
)

build = ROOT / "app/build.gradle.kts"
replace(build, 'versionCode = 10', 'versionCode = 11', "build number")

print("Applied ReisePilot 4.2 build 11 offline images and map usability update")
