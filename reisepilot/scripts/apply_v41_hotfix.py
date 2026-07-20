#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/de/balabucha/reisepilot"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Missing expected block in {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


# Always-visible local artwork. Wikimedia photos remain an optional overlay.
(SRC / "DestinationArtwork.kt").write_text(r'''package de.balabucha.reisepilot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

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
        drawCircle(Color(0xFFFFD166), radius = w * .10f, center = Offset(w * .80f, h * .20f))

        when (region) {
            TravelRegion.CANET -> {
                drawRect(Color(0xFF3EA7C4), topLeft = Offset(0f, h * .52f), size = androidx.compose.ui.geometry.Size(w, h * .30f))
                drawRect(Color(0xFFE8C982), topLeft = Offset(0f, h * .82f), size = androidx.compose.ui.geometry.Size(w, h * .18f))
                repeat(3) { i ->
                    val y = h * (.58f + i * .08f)
                    drawLine(Color.White.copy(alpha = .85f), Offset(w * .05f, y), Offset(w * .95f, y), strokeWidth = h * .018f)
                }
                val sail = Path().apply {
                    moveTo(w * .40f, h * .30f); lineTo(w * .40f, h * .62f); lineTo(w * .62f, h * .58f); close()
                }
                drawPath(sail, Color.White)
                drawLine(Color(0xFF364152), Offset(w * .40f, h * .27f), Offset(w * .40f, h * .66f), strokeWidth = w * .018f)
            }
            TravelRegion.BARCELONA -> {
                drawRect(Color(0xFF587A9B), topLeft = Offset(0f, h * .72f), size = androidx.compose.ui.geometry.Size(w, h * .28f))
                val building = Color(0xFF7A4E3A)
                drawRect(building, topLeft = Offset(w * .08f, h * .48f), size = androidx.compose.ui.geometry.Size(w * .22f, h * .32f))
                drawRect(Color(0xFFB45C3D), topLeft = Offset(w * .70f, h * .42f), size = androidx.compose.ui.geometry.Size(w * .20f, h * .38f))
                val church = Path().apply {
                    moveTo(w * .36f, h * .78f); lineTo(w * .40f, h * .25f); lineTo(w * .44f, h * .78f)
                    moveTo(w * .48f, h * .78f); lineTo(w * .52f, h * .16f); lineTo(w * .56f, h * .78f)
                    moveTo(w * .60f, h * .78f); lineTo(w * .64f, h * .30f); lineTo(w * .68f, h * .78f)
                }
                drawPath(church, Color(0xFF5B3B2E), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .035f))
            }
            TravelRegion.ANDORRA -> {
                val back = Path().apply {
                    moveTo(0f, h * .72f); lineTo(w * .28f, h * .24f); lineTo(w * .48f, h * .70f); lineTo(w * .72f, h * .18f); lineTo(w, h * .72f); close()
                }
                drawPath(back, Color(0xFF6F8D77))
                val snow = Path().apply {
                    moveTo(w * .18f, h * .40f); lineTo(w * .28f, h * .24f); lineTo(w * .37f, h * .44f); lineTo(w * .30f, h * .39f); lineTo(w * .25f, h * .45f); close()
                    moveTo(w * .62f, h * .38f); lineTo(w * .72f, h * .18f); lineTo(w * .82f, h * .40f); lineTo(w * .74f, h * .34f); lineTo(w * .68f, h * .42f); close()
                }
                drawPath(snow, Color.White.copy(alpha = .92f))
                drawRect(Color(0xFF4D9AA5), topLeft = Offset(0f, h * .72f), size = androidx.compose.ui.geometry.Size(w, h * .28f))
                drawLine(Color.White.copy(alpha = .7f), Offset(w * .12f, h * .82f), Offset(w * .88f, h * .82f), strokeWidth = h * .018f)
            }
            TravelRegion.PARIS -> {
                drawRect(Color(0xFF8FB4C9), topLeft = Offset(0f, h * .78f), size = androidx.compose.ui.geometry.Size(w, h * .22f))
                val tower = Path().apply {
                    moveTo(w * .50f, h * .16f); lineTo(w * .38f, h * .82f); lineTo(w * .45f, h * .82f); lineTo(w * .50f, h * .58f)
                    lineTo(w * .55f, h * .82f); lineTo(w * .62f, h * .82f); close()
                }
                drawPath(tower, Color(0xFF344054))
                drawLine(Color(0xFF344054), Offset(w * .40f, h * .63f), Offset(w * .60f, h * .63f), strokeWidth = h * .024f)
                drawLine(Color(0xFF344054), Offset(w * .36f, h * .80f), Offset(w * .64f, h * .80f), strokeWidth = h * .026f)
                drawRect(Color(0xFF8E6F62), topLeft = Offset(w * .05f, h * .58f), size = androidx.compose.ui.geometry.Size(w * .24f, h * .22f))
                drawRect(Color(0xFF8E6F62), topLeft = Offset(w * .71f, h * .53f), size = androidx.compose.ui.geometry.Size(w * .24f, h * .27f))
            }
        }
    }
}
''')

# Destination thumbnails/details: local illustration is always present below optional online photo.
discover = SRC / "DiscoverScreen.kt"
replace_once(
    discover,
    '''        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(kindSymbol(place.kind), color = Navy.copy(alpha = .62f), fontWeight = FontWeight.Black, fontSize = 20.sp)
            imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = place.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (!finished) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy.copy(alpha = .55f))
            }
        }''',
    '''        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DestinationArtwork(place.region, Modifier.fillMaxSize())
            imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = place.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Surface(
                color = Navy.copy(alpha = .78f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
            ) {
                Text(kindSymbol(place.kind), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            if (!finished) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(18.dp), strokeWidth = 2.dp, color = Navy.copy(alpha = .65f))
            }
        }'''
)
replace_once(
    discover,
    '''                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(kindSymbol(place.kind), color = Navy.copy(alpha = .55f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (imageFinished) place.region.label else "Bild wird geladen …",
                        color = Navy.copy(alpha = .65f),
                        fontWeight = FontWeight.Bold
                    )
                }
                imageUrl?.let { url ->''',
    '''                DestinationArtwork(place.region, Modifier.fillMaxSize())
                imageUrl?.let { url ->'''
)

# Retry prior failures with a fresh cache and reject unsupported SVG originals.
wiki = SRC / "WikiImage.kt"
replace_once(wiki, 'getSharedPreferences("travel_images_v34", Context.MODE_PRIVATE)', 'getSharedPreferences("travel_images_v43", Context.MODE_PRIVATE)')
replace_once(
    wiki,
    '''            val thumbnail = info.optString("thumburl")
            val original = info.optString("url")
            val candidate = thumbnail.takeIf(::usable) ?: original.takeIf(::usable)''',
    '''            val mime = info.optString("mime").lowercase()
            if (!mime.startsWith("image/") || mime.contains("svg")) continue
            val thumbnail = info.optString("thumburl")
            val original = info.optString("url")
            val candidate = thumbnail.takeIf(::usable) ?: original.takeIf(::usable)'''
)
replace_once(wiki, 'listOf(".pdf", ".djvu", ".tif", ".tiff")', 'listOf(".pdf", ".djvu", ".tif", ".tiff", ".svg")')
replace_once(wiki, 'ReisePilot/3.4 Android (family travel app)', 'ReisePilot/4.2 Android (family travel app)')

# Map touch handling, persistent marker labels and camera reset support.
map_file = SRC / "NativeTripMap.kt"
replace_once(map_file, 'import android.os.Bundle\n', 'import android.os.Bundle\nimport android.view.MotionEvent\n')
replace_once(map_file, 'import org.maplibre.android.style.layers.LineLayer\n', 'import org.maplibre.android.style.layers.LineLayer\nimport org.maplibre.android.style.layers.SymbolLayer\n')
replace_once(map_file, 'import org.maplibre.android.style.layers.PropertyFactory.*\n', 'import org.maplibre.android.style.layers.PropertyFactory.*\nimport org.maplibre.android.style.expressions.Expression.get\n')
replace_once(map_file, 'private const val BASE_STYLE = "https://tiles.openfreemap.org/styles/liberty"\n', 'private const val BASE_STYLE = "https://tiles.openfreemap.org/styles/liberty"\n\nprivate data class MapMarker(val point: GeoPoint, val label: String)\n')
replace_once(
    map_file,
    '''    previewRoute: RouteResult?,
    modifier: Modifier = Modifier
) {''',
    '''    previewRoute: RouteResult?,
    cameraResetToken: Int = 0,
    modifier: Modifier = Modifier
) {'''
)
replace_once(map_file, 'update = { controller.update(snapshot, previewRoute) },', 'update = { controller.update(snapshot, previewRoute, cameraResetToken) },')
replace_once(map_file, '    private var latestPreview: RouteResult? = null\n', '    private var latestPreview: RouteResult? = null\n    private var latestCameraResetToken = 0\n')
replace_once(
    map_file,
    '''        mapView = MapView(context)
        mapView.onCreate(Bundle())''',
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
        mapView.onCreate(Bundle())'''
)
replace_once(
    map_file,
    '''            readyMap.uiSettings.isLogoEnabled = true
            readyMap.uiSettings.isAttributionEnabled = true''',
    '''            readyMap.uiSettings.isLogoEnabled = true
            readyMap.uiSettings.isAttributionEnabled = true
            readyMap.uiSettings.isScrollGesturesEnabled = true
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isTiltGesturesEnabled = true'''
)
replace_once(
    map_file,
    '''    fun update(snapshot: TripSnapshot, preview: RouteResult?) {
        if (destroyed) return
        latestSnapshot = snapshot
        latestPreview = preview
        render()
    }''',
    '''    fun update(snapshot: TripSnapshot, preview: RouteResult?, cameraResetToken: Int) {
        if (destroyed) return
        latestSnapshot = snapshot
        latestPreview = preview
        latestCameraResetToken = cameraResetToken
        render()
    }'''
)
replace_once(
    map_file,
    '''        upsertPoints(style, "point-current", current?.let(::listOf).orEmpty(), Color.rgb(23, 107, 135), 8f)
        upsertPoints(style, "point-tolls", tolls.map { it.point }, Color.rgb(183, 121, 0), 7f)
        upsertPoints(style, "point-fallback-fuels", fallbackFuels, Color.rgb(84, 151, 116), 6f)
        upsertPoints(style, "point-recommended-fuel", recommendedFuel?.let(::listOf).orEmpty(), Color.rgb(0, 158, 96), 11f)
        upsertPoints(
            style,
            "point-ends",
            listOf(TripConfig.origin(latestSnapshot.stage), TripConfig.destination(latestSnapshot.stage)),
            Color.rgb(24, 38, 63),
            7f
        )

        val cameraKey = "${latestSnapshot.stage}:${route.firstOrNull()?.lat}:${route.lastOrNull()?.lat}:${route.size}"''',
    '''        upsertMarkers(style, "point-current", current?.let { listOf(MapMarker(it, "Dein Standort")) }.orEmpty(), Color.rgb(23, 107, 135), 9f)
        upsertMarkers(style, "point-tolls", tolls.mapIndexed { index, toll -> MapMarker(toll.point, toll.name.ifBlank { "Maut ${index + 1}" }) }, Color.rgb(183, 121, 0), 8f)
        upsertMarkers(style, "point-fallback-fuels", fallbackFuels.map { MapMarker(it, it.name.ifBlank { "Geplanter Tankstopp" }) }, Color.rgb(84, 151, 116), 7f)
        upsertMarkers(
            style,
            "point-recommended-fuel",
            latestSnapshot.fuelSuggestion?.let { listOf(MapMarker(it.point, "Empfohlen · ${it.name}")) }.orEmpty(),
            Color.rgb(0, 158, 96),
            12f
        )
        val origin = TripConfig.origin(latestSnapshot.stage)
        val destination = TripConfig.destination(latestSnapshot.stage)
        upsertMarkers(
            style,
            "point-ends",
            listOf(MapMarker(origin, "Start · ${origin.name}"), MapMarker(destination, "Ziel · ${destination.name}")),
            Color.rgb(24, 38, 63),
            8f
        )

        val cameraKey = "${latestSnapshot.stage}:${route.firstOrNull()?.lat}:${route.lastOrNull()?.lat}:${route.size}:$latestCameraResetToken"'''
)
replace_once(
    map_file,
    '''    private fun upsertPoints(style: Style, id: String, points: List<GeoPoint>, color: Int, radius: Float) {
        val sourceId = "$id-source"
        val layerId = "$id-layer"
        val collection = FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, collection))
            style.addLayer(
                CircleLayer(layerId, sourceId).withProperties(
                    circleColor(color), circleRadius(radius), circleStrokeColor(Color.WHITE), circleStrokeWidth(3f)
                )
            )
        } else source.setGeoJson(collection)
    }''',
    '''    private fun upsertMarkers(style: Style, id: String, markers: List<MapMarker>, color: Int, radius: Float) {
        val sourceId = "$id-source"
        val layerId = "$id-layer"
        val labelLayerId = "$id-label-layer"
        val features = markers.map { marker ->
            Feature.fromGeometry(Point.fromLngLat(marker.point.lon, marker.point.lat)).apply {
                addStringProperty("label", marker.label)
            }
        }
        val collection = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, collection))
            style.addLayer(
                CircleLayer(layerId, sourceId).withProperties(
                    circleColor(color), circleRadius(radius), circleStrokeColor(Color.WHITE), circleStrokeWidth(3f)
                )
            )
            style.addLayer(
                SymbolLayer(labelLayerId, sourceId).withProperties(
                    textField(get("label")),
                    textSize(12f),
                    textColor(Color.rgb(24, 38, 63)),
                    textHaloColor(Color.WHITE),
                    textHaloWidth(2f),
                    textAllowOverlap(false)
                )
            )
        } else source.setGeoJson(collection)
    }'''
)

# Clear map instructions and a route reset button.
map_screen = SRC / "MapScreen.kt"
replace_once(map_screen, '    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }\n', '    var stage by rememberSaveable { mutableStateOf(snapshot.stage) }\n    var cameraResetToken by rememberSaveable { mutableIntStateOf(0) }\n')
replace_once(
    map_screen,
    '''        item {
            Card(
                Modifier.fillMaxWidth().height(480.dp),''',
    '''        item {
            Text("Karte mit einem Finger verschieben · mit zwei Fingern zoomen und drehen", color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        item {
            Card(
                Modifier.fillMaxWidth().height(520.dp),'''
)
replace_once(
    map_screen,
    '''                    snapshot = displaySnapshot,
                    previewRoute = preview.route,
                    modifier = Modifier.fillMaxSize()''',
    '''                    snapshot = displaySnapshot,
                    previewRoute = preview.route,
                    cameraResetToken = cameraResetToken,
                    modifier = Modifier.fillMaxSize()'''
)
replace_once(
    map_screen,
    '''        item {
            AppCard {
                Text("Legende", style = MaterialTheme.typography.titleMedium)
                StatusLine("Grün", "freie Strecke oder günstiger Tankpunkt", Light.GREEN)
                StatusLine("Gelb", "mäßiger Verkehr oder Mautpunkt", Light.YELLOW)
                StatusLine("Rot", "starker Verkehr oder sofort handeln", Light.RED)''',
    '''        item {
            OutlinedButton(
                onClick = { cameraResetToken++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Gesamte Route wieder anzeigen") }
        }

        item {
            AppCard {
                Text("Punkte und Streckenfarben", style = MaterialTheme.typography.titleMedium)
                StatusLine("Dunkelblauer Punkt", "Start und Ziel · direkt auf der Karte beschriftet", Light.GREY)
                StatusLine("Blauer Punkt", "dein aktueller Standort", Light.GREY)
                StatusLine("Gelber Punkt", "Mautstelle mit Namen", Light.YELLOW)
                StatusLine("Hellgrüner Punkt", "geplanter Tankstopp", Light.GREEN)
                StatusLine("Großer grüner Punkt", "aktuell empfohlene Tankstelle", Light.GREEN)
                StatusLine("Grüne Linie", "freie Strecke", Light.GREEN)
                StatusLine("Gelb/Orange/Rot", "zunehmender Verkehr", Light.YELLOW)'''
)

# Build 11 hotfix; keep version name 4.2 so the existing workflow guard and artifact names remain valid.
gradle = ROOT / "app/build.gradle.kts"
replace_once(gradle, 'versionCode = 10', 'versionCode = 11')

print("Applied ReisePilot 4.2 build 11 image/map hotfix")
