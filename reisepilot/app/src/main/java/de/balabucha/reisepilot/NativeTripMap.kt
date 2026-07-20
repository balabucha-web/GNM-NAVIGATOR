package de.balabucha.reisepilot

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

private const val BASE_STYLE = "https://tiles.openfreemap.org/styles/liberty"

private data class MapMarker(val point: GeoPoint, val label: String)

@Composable
fun NativeTripMap(
    snapshot: TripSnapshot,
    previewRoute: RouteResult?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(context) { NativeMapController(context) }

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> controller.onLifecycle(event) }
        lifecycle.addObserver(observer)
        controller.sync(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.dispose()
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
    private var started = false
    private var resumed = false
    private var destroyed = false

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context)
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
        mapView.onCreate(Bundle())
        mapView.getMapAsync { readyMap ->
            if (destroyed) return@getMapAsync
            map = readyMap
            readyMap.uiSettings.isLogoEnabled = true
            readyMap.uiSettings.isAttributionEnabled = true
            readyMap.uiSettings.isScrollGesturesEnabled = true
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isTiltGesturesEnabled = true
            readyMap.setStyle(Style.Builder().fromUri(BASE_STYLE)) { readyStyle ->
                if (destroyed) return@setStyle
                style = readyStyle
                render()
            }
        }
    }

    fun sync(state: Lifecycle.State) {
        if (destroyed) return
        if (state.isAtLeast(Lifecycle.State.STARTED)) start()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) resume()
    }

    fun onLifecycle(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> dispose()
            else -> Unit
        }
    }

    private fun start() {
        if (!destroyed && !started) {
            mapView.onStart()
            started = true
        }
    }

    private fun resume() {
        if (destroyed) return
        start()
        if (!resumed) {
            mapView.onResume()
            resumed = true
        }
    }

    private fun pause() {
        if (!destroyed && resumed) {
            mapView.onPause()
            resumed = false
        }
    }

    private fun stop() {
        if (destroyed) return
        pause()
        if (started) {
            mapView.onStop()
            started = false
        }
    }

    fun dispose() {
        if (destroyed) return
        stop()
        destroyed = true
        style = null
        map = null
        mapView.onDestroy()
    }

    fun update(snapshot: TripSnapshot, preview: RouteResult?) {
        if (destroyed) return
        latestSnapshot = snapshot
        latestPreview = preview
        render()
    }

    private fun render() {
        if (destroyed) return
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

        val current = latestSnapshot.lat?.let { lat -> latestSnapshot.lon?.let { lon -> GeoPoint(lat, lon, "Dein Standort", "current") } }
        val tolls = latestSnapshot.tolls.ifEmpty { latestPreview?.tolls.orEmpty() }
        val fallbackFuels = TripConfig.fuelStops(latestSnapshot.stage)
        val recommended = latestSnapshot.fuelSuggestion

        upsertLabeledPoints(
            style, "point-current",
            current?.let { listOf(MapMarker(it, "Du")) }.orEmpty(),
            Color.rgb(23, 107, 135), 8f
        )
        upsertLabeledPoints(
            style, "point-tolls",
            tolls.map { MapMarker(it.point, "Maut · ${it.name.substringBefore(" · ")}") },
            Color.rgb(183, 121, 0), 7f
        )
        upsertLabeledPoints(
            style, "point-fallback-fuels",
            fallbackFuels.map { MapMarker(it, "Tank · ${it.name}") },
            Color.rgb(126, 87, 194), 7f
        )
        upsertLabeledPoints(
            style, "point-recommended-fuel",
            recommended?.let { fuel ->
                val price = fuel.pricePerLitre?.let { String.format(Locale.GERMANY, "%.3f €/l", it) }
                    ?: "Preis wird geladen"
                listOf(MapMarker(fuel.point, "$price\n${fuel.name}"))
            }.orEmpty(),
            Color.rgb(0, 158, 96), 12f,
            textSize = 14f,
            allowOverlap = true
        )
        val origin = TripConfig.origin(latestSnapshot.stage)
        val destination = TripConfig.destination(latestSnapshot.stage)
        upsertLabeledPoints(
            style, "point-start",
            listOf(MapMarker(origin, "Start · ${origin.name}")),
            Color.rgb(24, 38, 63), 8f
        )
        upsertLabeledPoints(
            style, "point-destination",
            listOf(MapMarker(destination, "Ziel · ${destination.name}")),
            Color.rgb(180, 35, 24), 10f,
            textSize = 13f,
            allowOverlap = true
        )

        val cameraKey = "${latestSnapshot.stage}:${route.firstOrNull()?.lat}:${route.lastOrNull()?.lat}:${route.size}"
        if (cameraKey != lastCameraKey) {
            lastCameraKey = cameraKey
            fitCamera(route.ifEmpty { listOf(origin, destination) })
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
        val raw = if (latestSnapshot.routeGeoJson.isNotBlank()) latestSnapshot.congestionJson
        else latestPreview?.congestionJson ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return List((pointCount - 1).coerceAtLeast(0)) { index -> array.optString(index, "unknown") }
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
                    lineColor(color), lineWidth(width), lineOpacity(0.92f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        } else source.setGeoJson(collection)
    }

    private fun upsertLabeledPoints(
        style: Style,
        id: String,
        markers: List<MapMarker>,
        color: Int,
        radius: Float,
        textSize: Float = 11f,
        allowOverlap: Boolean = false
    ) {
        val sourceId = "$id-source"
        val circleLayerId = "$id-circle"
        val labelLayerId = "$id-label"
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
                CircleLayer(circleLayerId, sourceId).withProperties(
                    circleColor(color), circleRadius(radius), circleStrokeColor(Color.WHITE), circleStrokeWidth(3f)
                )
            )
            style.addLayer(
                SymbolLayer(labelLayerId, sourceId).withProperties(
                    textField("{label}"),
                    textSize(textSize),
                    textColor(Color.rgb(24, 38, 63)),
                    textHaloColor(Color.WHITE),
                    textHaloWidth(2f),
                    textOffset(arrayOf(0f, 1.65f)),
                    textAnchor(Property.TEXT_ANCHOR_TOP),
                    textMaxWidth(15f),
                    textAllowOverlap(allowOverlap),
                    textIgnorePlacement(allowOverlap)
                )
            )
        } else source.setGeoJson(collection)
    }

    private fun fitCamera(points: List<GeoPoint>) {
        val map = map ?: return
        if (destroyed || points.isEmpty()) return
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.lat, it.lon)) }
        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 90), 650) }
    }
}
