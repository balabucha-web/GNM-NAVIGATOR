package de.balabucha.reisepilot

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val BASE_STYLE = "https://demotiles.maplibre.org/style.json"

@Composable
fun NativeTripMap(
    snapshot: TripSnapshot,
    previewRoute: RouteResult?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { NativeMapController(context) }

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> controller.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.mapView.onPause()
                Lifecycle.Event.ON_STOP -> controller.mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> controller.mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.mapView.onPause()
            controller.mapView.onStop()
            controller.mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { controller.mapView },
        update = { controller.update(snapshot, previewRoute) },
        modifier = modifier
    )
}

private class NativeMapController(context: Context) {
    val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var latestSnapshot = TripSnapshot()
    private var latestPreview: RouteResult? = null
    private var lastCameraKey = ""

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context)
        mapView.onCreate(Bundle())
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isLogoEnabled = true
            readyMap.uiSettings.isAttributionEnabled = true
            readyMap.setStyle(Style.Builder().fromUri(BASE_STYLE)) { readyStyle ->
                style = readyStyle
                render()
            }
        }
    }

    fun update(snapshot: TripSnapshot, preview: RouteResult?) {
        latestSnapshot = snapshot
        latestPreview = preview
        render()
    }

    private fun render() {
        val style = style ?: return
        val route = routePoints()
        val congestion = congestionLevels(route.size)

        val byLevel = linkedMapOf(
            "low" to mutableListOf<Feature>(),
            "moderate" to mutableListOf(),
            "heavy" to mutableListOf(),
            "severe" to mutableListOf(),
            "unknown" to mutableListOf()
        )
        if (route.size >= 2) {
            for (i in 0 until route.lastIndex) {
                val level = congestion.getOrNull(i)?.takeIf { byLevel.containsKey(it) } ?: "unknown"
                val segment = LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(route[i].lon, route[i].lat),
                        Point.fromLngLat(route[i + 1].lon, route[i + 1].lat)
                    )
                )
                byLevel.getValue(level).add(Feature.fromGeometry(segment))
            }
        }

        upsertLine(style, "route-low", byLevel.getValue("low"), Color.rgb(24, 121, 78), 6f)
        upsertLine(style, "route-moderate", byLevel.getValue("moderate"), Color.rgb(210, 158, 0), 6f)
        upsertLine(style, "route-heavy", byLevel.getValue("heavy"), Color.rgb(229, 107, 53), 6f)
        upsertLine(style, "route-severe", byLevel.getValue("severe"), Color.rgb(180, 35, 24), 7f)
        upsertLine(style, "route-unknown", byLevel.getValue("unknown"), Color.rgb(23, 107, 135), 5f)

        val current = latestSnapshot.lat?.let { lat -> latestSnapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
        val tolls = latestSnapshot.tolls.ifEmpty { latestPreview?.tolls.orEmpty() }
        val fuels = TripConfig.fuelStops(latestSnapshot.stage)

        upsertPoints(style, "point-current", current?.let { listOf(it) }.orEmpty(), Color.rgb(23, 107, 135), 8f)
        upsertPoints(style, "point-tolls", tolls.map { it.point }, Color.rgb(183, 121, 0), 7f)
        upsertPoints(style, "point-fuels", fuels, Color.rgb(24, 121, 78), 7f)
        upsertPoints(
            style,
            "point-ends",
            listOf(TripConfig.origin(latestSnapshot.stage), TripConfig.destination(latestSnapshot.stage)),
            Color.rgb(24, 38, 63),
            7f
        )

        val cameraKey = "${latestSnapshot.stage}:${route.firstOrNull()?.lat}:${route.lastOrNull()?.lat}:${route.size}"
        if (cameraKey != lastCameraKey) {
            lastCameraKey = cameraKey
            fitCamera(route.ifEmpty {
                listOf(TripConfig.origin(latestSnapshot.stage), TripConfig.destination(latestSnapshot.stage))
            })
        }
    }

    private fun routePoints(): List<GeoPoint> {
        if (latestSnapshot.routeGeoJson.isNotBlank()) {
            runCatching {
                val line = LineString.fromJson(latestSnapshot.routeGeoJson)
                return line.coordinates().map { GeoPoint(it.latitude(), it.longitude()) }
            }
        }
        return latestPreview?.geometry.orEmpty()
    }

    private fun congestionLevels(pointCount: Int): List<String> {
        val raw = if (latestSnapshot.routeGeoJson.isNotBlank()) {
            latestSnapshot.congestionJson
        } else {
            latestPreview?.congestionJson ?: "[]"
        }
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return List((pointCount - 1).coerceAtLeast(0)) { index ->
            array.optString(index, "unknown")
        }
    }

    private fun upsertLine(style: Style, id: String, features: List<Feature>, color: Int, width: Float) {
        val sourceId = "$id-source"
        val layerId = "$id-layer"
        val collection = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, collection))
            style.addLayer(
                LineLayer(layerId, sourceId).withProperties(
                    lineColor(color),
                    lineWidth(width),
                    lineOpacity(0.92f),
                    lineCap("round"),
                    lineJoin("round")
                )
            )
        } else {
            source.setGeoJson(collection)
        }
    }

    private fun upsertPoints(style: Style, id: String, points: List<GeoPoint>, color: Int, radius: Float) {
        val sourceId = "$id-source"
        val layerId = "$id-layer"
        val features = points.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
        }
        val collection = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, collection))
            style.addLayer(
                CircleLayer(layerId, sourceId).withProperties(
                    circleColor(color),
                    circleRadius(radius),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(3f)
                )
            )
        } else {
            source.setGeoJson(collection)
        }
    }

    private fun fitCamera(points: List<GeoPoint>) {
        val map = map ?: return
        if (points.isEmpty()) return
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.lat, it.lon)) }
        runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 90), 650)
        }
    }
}
