package de.balabucha.reisepilot

import android.Manifest
import android.annotation.SuppressLint
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Foreground-only controller for the period before a route is active.
 * It restores the previous nearby result immediately, resolves the current
 * position and refreshes fuel/roadside data without requiring trip activation.
 */
class NearbyLiveController56(private val activity: Activity) {
    companion object {
        private const val REFRESH_MS = 5L * 60L * 1_000L
        private const val REFRESH_DISTANCE_M = 2_000f
        private const val CACHE_MAX_AGE_MS = 8L * 60L * 60L * 1_000L
        private const val SLOW_NOTICE_MS = 15_000L
        private const val PREFS = "nearby_live_cache_v56"
    }

    private val app = activity.applicationContext
    private val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private var snapshot = TripSnapshot()
    private var resumed = false
    private var requesting = false
    private var lastLocation: Location? = null
    private var lastRefreshLocation: Location? = null
    private var lastRefreshAt = 0L
    private var fuelRequestId = 0L
    private var roadsideRequestId = 0L

    private val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8_000L)
        .setMinUpdateIntervalMillis(3_000L)
        .setMinUpdateDistanceMeters(5f)
        .setMaxUpdateDelayMillis(10_000L)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.lastOrNull()?.let(::onLocation)
        }
    }

    init {
        restoreCache()
    }

    fun onSnapshot(value: TripSnapshot) {
        snapshot = value
        if (value.active) {
            stopLocations()
            RoadAheadStore54.state = RoadAheadStore54.state.copy(active = true, nearbyMode = false)
        } else {
            RoadAheadStore54.state = RoadAheadStore54.state.copy(active = false, nearbyMode = true)
            updateRequestState()
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

    fun onPermissionChanged() {
        updateRequestState()
        if (hasLocationPermission()) requestLastLocation()
    }

    fun forceRefresh() {
        lastRefreshAt = 0L
        lastLocation?.let { refresh(it, force = true) }
    }

    fun destroy() {
        stopLocations()
        fuelRequestId++
        roadsideRequestId++
        worker.shutdownNow()
    }

    private fun updateRequestState() {
        if (!resumed || snapshot.active) {
            stopLocations()
            return
        }
        if (!hasLocationPermission()) {
            RoadAheadStore54.state = RoadAheadStore54.state.copy(
                active = false,
                nearbyMode = true,
                message = "Standortfreigabe erforderlich, damit Tankstellen und Parkplätze in deiner Nähe geladen werden."
            )
            return
        }
        startLocations()
        requestLastLocation()
    }

    private fun hasLocationPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun requestLastLocation() {
        if (!hasLocationPermission() || snapshot.active) return
        fused.lastLocation.addOnSuccessListener { location ->
            if (location != null && !snapshot.active) onLocation(location)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocations() {
        if (requesting || !hasLocationPermission() || snapshot.active) return
        requesting = true
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error ->
                requesting = false
                RoadAheadStore54.state = RoadAheadStore54.state.copy(
                    message = "Standort konnte nicht gestartet werden: ${error.message.orEmpty().take(100)}"
                )
            }
    }

    private fun stopLocations() {
        if (!requesting) return
        requesting = false
        runCatching { fused.removeLocationUpdates(callback) }
    }

    private fun onLocation(location: Location) {
        if (snapshot.active) return
        lastLocation = location
        val now = System.currentTimeMillis()
        RoadAheadStore54.state = RoadAheadStore54.state.copy(
            active = false,
            nearbyMode = true,
            speedKmh = if (location.hasSpeed()) (location.speed * 3.6).roundToInt().coerceAtLeast(0) else 0,
            accuracyM = location.accuracy.roundToInt(),
            speedUpdatedAt = now,
            locationUpdatedAt = now,
            message = if (location.accuracy > 120f) "Standort ist noch ungenau (${location.accuracy.roundToInt()} m)." else RoadAheadStore54.state.message
        )
        refresh(location, force = false)
    }

    private fun refresh(location: Location, force: Boolean) {
        if (snapshot.active) return
        val now = System.currentTimeMillis()
        val moved = lastRefreshLocation?.distanceTo(location) ?: Float.MAX_VALUE
        if (!force && now - lastRefreshAt < REFRESH_MS && moved < REFRESH_DISTANCE_M) return
        lastRefreshAt = now
        lastRefreshLocation = Location(location)
        val current = GeoPoint(location.latitude, location.longitude, "Aktueller Standort")
        val tankerKey = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("tankerkoenig_key", "").orEmpty()

        val fuelId = ++fuelRequestId
        val roadsideId = ++roadsideRequestId
        RoadAheadStore54.state = RoadAheadStore54.state.copy(
            active = false,
            nearbyMode = true,
            loadingFuel = true,
            loadingRoadside = true,
            fuelMessage = if (RoadAheadStore54.state.fuelOptions.isEmpty()) "Tankstellen werden in deiner Nähe gesucht." else "Tankpreise werden aktualisiert.",
            roadsideMessage = if (RoadAheadStore54.state.roadsideStops.isEmpty()) "Raststätten und Parkplätze werden in deiner Nähe gesucht." else "Umgebungsdaten werden aktualisiert.",
            message = "Live-Daten werden aktualisiert."
        )

        main.postDelayed({
            val state = RoadAheadStore54.state
            if (!snapshot.active && (state.loadingFuel || state.loadingRoadside)) {
                RoadAheadStore54.state = state.copy(
                    message = "Eine Datenquelle antwortet langsam. Vorhandene Ergebnisse bleiben sichtbar."
                )
            }
        }, SLOW_NOTICE_MS)

        worker.execute {
            val result = runCatching { NearbyFuelClient56.query(current, tankerKey) }
            main.post {
                if (snapshot.active || fuelId != fuelRequestId) return@post
                val old = RoadAheadStore54.state
                val values = result.getOrNull().orEmpty()
                val missingKey = CountryResolver.country(current) == FuelCountry.GERMANY && tankerKey.trim().length < 30
                val status = when {
                    result.isFailure -> "Tankstellen konnten nicht aktualisiert werden: ${result.exceptionOrNull()?.message.orEmpty().take(100)}"
                    values.isEmpty() && missingKey -> "Keine deutschen Preise: Tankerkönig-Key fehlt. Stationen ohne Preis werden über OpenStreetMap versucht."
                    values.isEmpty() -> "Keine passende Tankstelle im Umkreis gefunden."
                    missingKey -> "Tankstellen geladen; deutsche Preise benötigen einen Tankerkönig-Key."
                    else -> "${values.size} Tankstellen aktuell geladen."
                }
                RoadAheadStore54.state = old.copy(
                    fuelOptions = values.ifEmpty { old.fuelOptions },
                    loadingFuel = false,
                    fuelUpdatedAt = System.currentTimeMillis(),
                    dataUpdatedAt = System.currentTimeMillis(),
                    fuelMessage = status,
                    message = combinedMessage(status, old.roadsideMessage, old.loadingRoadside)
                )
                saveCache(current)
            }
        }

        worker.execute {
            val result = runCatching { NearbyRoadsideClient56.query(current) }
            main.post {
                if (snapshot.active || roadsideId != roadsideRequestId) return@post
                val old = RoadAheadStore54.state
                val values = result.getOrNull().orEmpty()
                val status = when {
                    result.isFailure -> "Raststätten und Parkplätze konnten nicht aktualisiert werden: ${result.exceptionOrNull()?.message.orEmpty().take(100)}"
                    values.isEmpty() -> "Keine Raststätte oder öffentlicher Parkplatz im Suchradius gefunden."
                    else -> "${values.size} Raststätten, Rastplätze und Parkplätze aktuell geladen."
                }
                RoadAheadStore54.state = old.copy(
                    roadsideStops = values.ifEmpty { old.roadsideStops },
                    loadingRoadside = false,
                    roadsideUpdatedAt = System.currentTimeMillis(),
                    dataUpdatedAt = System.currentTimeMillis(),
                    roadsideMessage = status,
                    message = combinedMessage(old.fuelMessage, status, old.loadingFuel)
                )
                saveCache(current)
            }
        }
    }

    private fun combinedMessage(first: String, second: String, otherLoading: Boolean): String = when {
        otherLoading -> "Ein Teil der Live-Daten wird noch aktualisiert."
        first.contains("konnten nicht", true) || second.contains("konnten nicht", true) -> listOf(first, second).filter(String::isNotBlank).joinToString(" ").take(220)
        else -> "Live-Daten in deiner Nähe sind aktuell."
    }

    private fun saveCache(current: GeoPoint) {
        val state = RoadAheadStore54.state
        val fuel = JSONArray().apply { state.fuelOptions.forEach { put(it.json()) } }
        val roadside = JSONArray().apply { state.roadsideStops.forEach { put(roadsideJson(it)) } }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("savedAt", System.currentTimeMillis())
            .putString("location", current.json().toString())
            .putString("fuel", fuel.toString())
            .putString("roadside", roadside.toString())
            .putString("fuelMessage", state.fuelMessage)
            .putString("roadsideMessage", state.roadsideMessage)
            .apply()
    }

    private fun restoreCache() {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong("savedAt", 0L)
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > CACHE_MAX_AGE_MS) return
        val fuel = runCatching {
            val array = JSONArray(prefs.getString("fuel", "[]"))
            buildList { for (index in 0 until array.length()) add(FuelSuggestion.fromJson(array.getJSONObject(index))) }
        }.getOrDefault(emptyList())
        val roadside = runCatching {
            val array = JSONArray(prefs.getString("roadside", "[]"))
            buildList { for (index in 0 until array.length()) add(roadsideFromJson(array.getJSONObject(index))) }
        }.getOrDefault(emptyList())
        if (fuel.isEmpty() && roadside.isEmpty()) return
        RoadAheadStore54.state = RoadAheadState54(
            active = false,
            nearbyMode = true,
            fuelOptions = fuel,
            roadsideStops = roadside,
            dataUpdatedAt = savedAt,
            fuelUpdatedAt = savedAt,
            roadsideUpdatedAt = savedAt,
            fuelMessage = prefs.getString("fuelMessage", "Letzter Tankstellenstand").orEmpty(),
            roadsideMessage = prefs.getString("roadsideMessage", "Letzter Umgebungsstand").orEmpty(),
            message = "Letzter Live-Stand wird mit dem aktuellen Standort abgeglichen."
        )
    }

    private fun roadsideJson(stop: RoadsideStop54): JSONObject = JSONObject()
        .put("id", stop.id)
        .put("name", stop.name)
        .put("point", stop.point.json())
        .put("kind", stop.kind.name)
        .put("distanceAheadKm", stop.distanceAheadKm)
        .put("routeOffsetKm", stop.routeOffsetKm)
        .put("detourKm", stop.detourKm ?: JSONObject.NULL)
        .put("hasFuel", stop.hasFuel)
        .put("hasToilets", stop.hasToilets)
        .put("hasFood", stop.hasFood)
        .put("directionChecked", stop.directionChecked)
        .put("source", stop.source)

    private fun roadsideFromJson(json: JSONObject): RoadsideStop54 = RoadsideStop54(
        id = json.optString("id"),
        name = json.optString("name", "Parkplatz"),
        point = GeoPoint.fromJson(json.getJSONObject("point")),
        kind = runCatching { RoadsideKind54.valueOf(json.optString("kind")) }.getOrDefault(RoadsideKind54.PARKING),
        distanceAheadKm = json.optDouble("distanceAheadKm"),
        routeOffsetKm = json.optDouble("routeOffsetKm"),
        detourKm = if (json.isNull("detourKm")) null else json.optDouble("detourKm"),
        hasFuel = json.optBoolean("hasFuel"),
        hasToilets = json.optBoolean("hasToilets"),
        hasFood = json.optBoolean("hasFood"),
        directionChecked = json.optBoolean("directionChecked"),
        source = json.optString("source", "OpenStreetMap")
    )
}
