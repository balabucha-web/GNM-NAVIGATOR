package de.balabucha.reisepilot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class RealtimeDriveController54(private val activity: Activity) {
    companion object {
        private const val DATA_REFRESH_MS = 4L * 60L * 1_000L
        private const val DATA_REFRESH_DISTANCE_M = 10_000f
    }

    private val app = activity.applicationContext
    private val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private var snapshot = TripSnapshot()
    private var route: List<GeoPoint> = emptyList()
    private var routeKey = ""
    private var resumed = false
    private var requesting = false
    private var lastLocation: Location? = null
    private var lastDataLocation: Location? = null
    private var lastDataRefresh = 0L
    private var fuelRequestId = 0L
    private var roadsideRequestId = 0L
    private var smoothedSpeed: Double? = null

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
        .setMinUpdateIntervalMillis(500L)
        .setMinUpdateDistanceMeters(0f)
        .setMaxUpdateDelayMillis(1_000L)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.lastOrNull()?.let(::onLocation)
        }
    }

    fun onResume() {
        resumed = true
        updateRequestState()
    }

    fun onPause() {
        resumed = false
        stopLocations()
    }

    fun onSnapshot(value: TripSnapshot) {
        snapshot = value
        val newRouteKey = "${value.stage}:${value.routeGeoJson.hashCode()}"
        if (newRouteKey != routeKey) {
            routeKey = newRouteKey
            route = RouteAheadTools54.parseRoute(value.routeGeoJson)
            lastDataRefresh = 0L
        }
        RoadAheadStore54.state = RoadAheadStore54.state.copy(active = value.active)
        updateRequestState()
        if (value.active && route.size >= 2) lastLocation?.let { maybeRefreshData(it, force = false) }
    }

    fun forceRefresh() {
        lastDataRefresh = 0L
        lastLocation?.let { maybeRefreshData(it, force = true) }
    }

    fun destroy() {
        stopLocations()
        fuelRequestId++
        roadsideRequestId++
        worker.shutdownNow()
    }

    private fun updateRequestState() {
        if (snapshot.active && resumed && hasLocationPermission()) startLocations() else stopLocations()
    }

    private fun hasLocationPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocations() {
        if (requesting || !hasLocationPermission()) return
        requesting = true
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener {
                requesting = false
                RoadAheadStore54.state = RoadAheadStore54.state.copy(
                    message = "Realtime-GPS nicht verfügbar: ${it.message.orEmpty().take(100)}"
                )
            }
    }

    private fun stopLocations() {
        if (!requesting) return
        requesting = false
        runCatching { fused.removeLocationUpdates(callback) }
    }

    private fun onLocation(location: Location) {
        lastLocation = location
        val rawSpeed = if (location.hasSpeed() && location.accuracy <= 80f) {
            (location.speed * 3.6).toDouble().coerceIn(0.0, 260.0)
        } else null
        if (rawSpeed != null) {
            smoothedSpeed = when (val previous = smoothedSpeed) {
                null -> rawSpeed
                else -> previous * 0.30 + rawSpeed * 0.70
            }
        }
        val speed = smoothedSpeed?.let { if (it < 2.5) 0 else it.roundToInt() }
        val current = GeoPoint(location.latitude, location.longitude)
        val state = RoadAheadStore54.state
        val updatedFuels = updateFuelDistances(current, state.fuelOptions)
        val updatedRoadside = updateRoadsideDistances(current, state.roadsideStops)
        RoadAheadStore54.state = state.copy(
            active = snapshot.active,
            speedKmh = speed ?: snapshot.speedKmh,
            accuracyM = location.accuracy.roundToInt(),
            speedUpdatedAt = System.currentTimeMillis(),
            fuelOptions = updatedFuels,
            roadsideStops = updatedRoadside
        )
        maybeRefreshData(location, force = false)
    }

    private fun updateFuelDistances(current: GeoPoint, input: List<FuelSuggestion>): List<FuelSuggestion> =
        input.mapNotNull { fuel ->
            val ahead = RouteAheadTools54.forwardDistanceKm(current, fuel.point, route, 10_000.0)
                ?: return@mapNotNull null
            if (ahead > 125.0) return@mapNotNull null
            fuel.copy(distanceAheadKm = ahead)
        }.sortedBy { it.distanceAheadKm }

    private fun updateRoadsideDistances(current: GeoPoint, input: List<RoadsideStop54>): List<RoadsideStop54> =
        input.mapNotNull { stop ->
            val maxOffset = if (stop.kind == RoadsideKind54.PARKING) 2_500.0 else 5_500.0
            val ahead = RouteAheadTools54.forwardDistanceKm(current, stop.point, route, maxOffset)
                ?: return@mapNotNull null
            if (ahead > 130.0) return@mapNotNull null
            stop.copy(distanceAheadKm = ahead)
        }.sortedBy { it.distanceAheadKm }

    private fun maybeRefreshData(location: Location, force: Boolean) {
        if (!snapshot.active || route.size < 2) return
        val now = System.currentTimeMillis()
        val moved = lastDataLocation?.distanceTo(location) ?: Float.MAX_VALUE
        if (!force && now - lastDataRefresh < DATA_REFRESH_MS && moved < DATA_REFRESH_DISTANCE_M) return
        lastDataRefresh = now
        lastDataLocation = Location(location)
        val current = GeoPoint(location.latitude, location.longitude)
        val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val consumption = settings.getFloat("consumption", 7.4f).toDouble().coerceAtLeast(3.0)
        val tankerKey = settings.getString("tankerkoenig_key", "").orEmpty()
        val mapboxToken = normalizeMapboxToken(settings.getString("mapbox_token", "").orEmpty())
        val destination = TripConfig.destination(snapshot.stage)
        val baseDistance = snapshot.remainingKm?.times(1_000)

        val fuelId = ++fuelRequestId
        RoadAheadStore54.state = RoadAheadStore54.state.copy(loadingFuel = true)
        worker.execute {
            val result = runCatching {
                LiveFuelOptions54.query(
                    current = current,
                    route = route,
                    destination = destination,
                    consumption = consumption,
                    tankerKoenigKey = tankerKey,
                    mapboxToken = mapboxToken,
                    baseRouteDistanceM = baseDistance
                )
            }
            main.post {
                if (fuelId != fuelRequestId) return@post
                val old = RoadAheadStore54.state
                val message = result.exceptionOrNull()?.message?.take(120).orEmpty().ifBlank {
                    if (result.getOrNull().isNullOrEmpty() && CountryResolver.country(current) == FuelCountry.GERMANY && tankerKey.length < 30) {
                        "Für deutsche Echtzeitpreise Tankerkönig-Key unter Mehr → APIs eintragen."
                    } else old.message
                }
                RoadAheadStore54.state = old.copy(
                    fuelOptions = result.getOrNull().orEmpty().ifEmpty { old.fuelOptions },
                    loadingFuel = false,
                    dataUpdatedAt = System.currentTimeMillis(),
                    message = message
                )
            }
        }

        val roadsideId = ++roadsideRequestId
        RoadAheadStore54.state = RoadAheadStore54.state.copy(loadingRoadside = true)
        worker.execute {
            val result = runCatching {
                RoadsideClient54.query(
                    current = current,
                    route = route,
                    destination = destination,
                    mapboxToken = mapboxToken,
                    baseRouteDistanceM = baseDistance
                )
            }
            main.post {
                if (roadsideId != roadsideRequestId) return@post
                val old = RoadAheadStore54.state
                val error = result.exceptionOrNull()?.message?.take(120).orEmpty()
                RoadAheadStore54.state = old.copy(
                    roadsideStops = result.getOrNull().orEmpty().ifEmpty { old.roadsideStops },
                    loadingRoadside = false,
                    dataUpdatedAt = System.currentTimeMillis(),
                    message = if (error.isNotBlank()) error else old.message
                )
            }
        }
    }
}
