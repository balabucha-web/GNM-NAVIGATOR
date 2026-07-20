package de.balabucha.reisepilot

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.roundToInt

class TripTrackingService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_UPDATE = "de.balabucha.reisepilot.TRIP_UPDATE"
        const val ACTION_START_SAT = "START_SAT"
        const val ACTION_START_SUN = "START_SUN"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_STOP = "STOP"
        const val ACTION_BREAK_DONE = "BREAK_DONE"
        const val ACTION_REFUEL_FULL = "REFUEL_FULL"
        const val ACTION_RELOAD_CONFIG = "RELOAD_CONFIG"

        private const val LIVE_CHANNEL = "trip_live_v33"
        private const val ALERT_CHANNEL = "trip_alerts_v33"
        private const val LIVE_ID = 301
        private const val WARMUP_MS = 60_000L
        private const val MAX_SESSION_MS = 24L * 60L * 60L * 1000L
        private const val MAX_ROUTE_POINTS = 360
        private const val ROUTE_REFRESH_MS = 5L * 60L * 1000L
        private const val FUEL_REFRESH_MS = 12L * 60L * 1000L
    }

    private lateinit var fused: FusedLocationProviderClient
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var stage = Stage.SATURDAY
    private var active = false
    private var paused = false
    private var lastLocation: Location? = null
    private var lastRouteLocation: Location? = null
    private var lastFuelLocation: Location? = null
    private var route: RouteResult? = null
    private var routeUpdatedAt = 0L
    private var fuelUpdatedAt = 0L
    private var startedAt = 0L
    private var pauseStartedAt = 0L
    private var pausedTotal = 0L
    private var distanceKm = 0.0
    private var fuelSuggestion: FuelSuggestion? = null
    private var snapshot = TripSnapshot()
    private var sessionId = System.nanoTime()
    private var routeRequestId = 0L
    private var fuelRequestId = 0L

    private val announced = mutableSetOf<String>()
    private val previousDistance = mutableMapOf<String, Double>()
    private val runtime by lazy { getSharedPreferences("trip_runtime_v33", MODE_PRIVATE) }
    private val diagnostics by lazy { getSharedPreferences("diagnostics", MODE_PRIVATE) }

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateDistanceMeters(15f)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannels()
        tts = TextToSpeech(this, this)
        restore()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) runCatching { tts?.language = Locale.GERMAN }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            if (intent == null) {
                if (active && runtimeIsValid()) {
                    foreground("Tracking wird fortgesetzt")
                    requestLocations()
                    START_STICKY
                } else {
                    clearStaleRuntime()
                    stopSelf()
                    START_NOT_STICKY
                }
            } else {
                when (intent.action) {
                    ACTION_START_SAT -> start(Stage.SATURDAY)
                    ACTION_START_SUN -> start(Stage.SUNDAY)
                    ACTION_PAUSE -> togglePause()
                    ACTION_BREAK_DONE -> resetBreak()
                    ACTION_REFUEL_FULL -> resetFuel()
                    ACTION_RELOAD_CONFIG -> reloadConfig()
                    ACTION_STOP -> stopTrip()
                }
                START_STICKY
            }
        } catch (t: Throwable) {
            recordError("Start: ${t.javaClass.simpleName}: ${t.message}")
            stopTrip()
            START_NOT_STICKY
        }
    }

    private fun start(newStage: Stage) {
        stage = newStage
        sessionId = System.nanoTime()
        routeRequestId = 0L
        fuelRequestId = 0L
        active = true
        paused = false
        startedAt = System.currentTimeMillis()
        pauseStartedAt = 0L
        pausedTotal = 0L
        distanceKm = 0.0
        lastLocation = null
        lastRouteLocation = null
        lastFuelLocation = null
        route = null
        fuelSuggestion = null
        routeUpdatedAt = 0L
        fuelUpdatedAt = 0L
        announced.clear()
        previousDistance.clear()
        diagnostics.edit().remove("last_error").apply()
        snapshot = TripSnapshot(active = true, stage = stage, nextTitle = "GPS wird gestartet")
        saveRuntime()
        saveAndBroadcast()
        foreground("GPS wird gestartet")
        requestLocations()
    }

    private fun reloadConfig() {
        routeRequestId++
        fuelRequestId++
        routeUpdatedAt = 0L
        fuelUpdatedAt = 0L
        lastLocation?.let {
            refreshRoute(it)
            maybeRefreshFuel(it, force = true)
        }
        recalculate()
    }

    private fun runtimeIsValid(): Boolean {
        val age = System.currentTimeMillis() - startedAt
        return startedAt > 0L && age in 0..MAX_SESSION_MS
    }

    private fun clearStaleRuntime() {
        active = false
        paused = false
        startedAt = 0L
        pauseStartedAt = 0L
        pausedTotal = 0L
        runtime.edit().clear().apply()
        snapshot = TripSnapshot(active = false, stage = stage, nextTitle = "Bereit")
        saveAndBroadcast()
    }

    private fun requestLocations() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            alert("Standort fehlt", "Standortberechtigung wurde nicht erteilt.", true)
            stopTrip()
            return
        }
        fused.removeLocationUpdates(callback).addOnCompleteListener {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    recordError("GPS: ${it.javaClass.simpleName}: ${it.message}")
                    alert("GPS-Fehler", it.message ?: "Standort konnte nicht gestartet werden.", true)
                }
        }
    }

    private fun onLocation(location: Location) {
        try {
            lastLocation?.let { old ->
                val jump = old.distanceTo(location)
                if (location.accuracy <= 100f && jump in 5f..2_000f) {
                    distanceKm += jump / 1000.0
                }
            }
            lastLocation = location

            val routeMove = lastRouteLocation?.distanceTo(location) ?: Float.MAX_VALUE
            val routeDue = System.currentTimeMillis() - routeUpdatedAt >= ROUTE_REFRESH_MS
            if (route == null || routeMove >= 20_000f || routeDue) refreshRoute(location)

            maybeRefreshFuel(location)
            recalculate()
        } catch (t: Throwable) {
            recordError("Standort: ${t.javaClass.simpleName}: ${t.message}")
            snapshot = snapshot.copy(apiOk = false, apiMessage = "Trackingfehler abgefangen")
            saveAndBroadcast()
        }
    }

    private fun cleanToken(): String = normalizeMapboxToken(
        getSharedPreferences("settings", MODE_PRIVATE)
            .getString("mapbox_token", "").orEmpty()
    )

    private fun refreshRoute(location: Location) {
        val token = cleanToken()
        if (!mapboxTokenLooksValid(token)) {
            snapshot = snapshot.copy(apiOk = false, apiMessage = "Mapbox-Token ungültig oder nicht gespeichert")
            saveAndBroadcast()
            return
        }
        routeUpdatedAt = System.currentTimeMillis()
        lastRouteLocation = Location(location)
        val requestSession = sessionId
        val requestStage = stage
        val requestId = ++routeRequestId
        val requestLocation = Location(location)
        worker.execute {
            val outcome = runCatching {
                MapboxClient.route(
                    token,
                    GeoPoint(requestLocation.latitude, requestLocation.longitude),
                    TripConfig.destination(requestStage)
                )
            }
            main.post {
                if (!active || sessionId != requestSession || stage != requestStage || routeRequestId != requestId) {
                    return@post
                }
                outcome.onSuccess { result ->
                    route = result
                    maybeRefreshFuel(requestLocation, force = true)
                    recalculate()
                }.onFailure {
                    val msg = it.message ?: "Live-Route nicht verfügbar"
                    recordError("Mapbox: $msg")
                    snapshot = snapshot.copy(apiOk = false, apiMessage = msg.take(140))
                    saveAndBroadcast()
                    notifyLive()
                }
            }
        }
    }

    private fun maybeRefreshFuel(location: Location, force: Boolean = false) {
        val result = route ?: return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val consumption = prefs.getFloat("consumption", 7.4f).toDouble().coerceAtLeast(3.0)
        val litres = estimatedLitres(consumption)
        val moved = lastFuelLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val due = System.currentTimeMillis() - fuelUpdatedAt >= FUEL_REFRESH_MS

        val shouldPlan = litres <= 42.0 || distanceKm >= 190.0
        if (!force && (!shouldPlan || (!due && moved < 20_000f))) return

        fuelUpdatedAt = System.currentTimeMillis()
        lastFuelLocation = Location(location)
        val current = GeoPoint(location.latitude, location.longitude)
        val tankerKey = prefs.getString("tankerkoenig_key", "").orEmpty()
        val mapboxToken = cleanToken()
        val requestSession = sessionId
        val requestStage = stage
        val requestId = ++fuelRequestId

        worker.execute {
            val suggestion = runCatching {
                FuelPriceClient.query(
                    current = current,
                    route = result.geometry,
                    destination = TripConfig.destination(requestStage),
                    consumption = consumption,
                    tankerKoenigKey = tankerKey,
                    mapboxToken = mapboxToken,
                    baseRouteDistanceM = result.distanceM
                )
            }.getOrElse {
                recordError("Tankdaten: ${it.javaClass.simpleName}: ${it.message}")
                null
            } ?: fallbackFuel(current, result.geometry, consumption)

            main.post {
                if (!active || sessionId != requestSession || stage != requestStage || fuelRequestId != requestId) {
                    return@post
                }
                fuelSuggestion = suggestion
                recalculate()
            }
        }
    }

    private fun fallbackFuel(
        current: GeoPoint,
        geometry: List<GeoPoint>,
        consumption: Double
    ): FuelSuggestion? {
        val candidates = TripConfig.fuelStops(stage).map {
            FuelCandidate(
                name = it.name,
                address = "Geplanter günstiger Stopp abseits der Autobahn",
                point = it,
                dieselPrice = null,
                motorway = false,
                source = "Geplanter Ersatzstopp"
            )
        }
        return FuelRanking.best(
            current,
            TripConfig.destination(stage),
            geometry,
            candidates,
            consumption
        )
    }

    private fun estimatedLitres(consumption: Double): Double {
        val start = getSharedPreferences("settings", MODE_PRIVATE)
            .getFloat("start_litres", 60f).toDouble()
        return (start - distanceKm * consumption / 100.0).coerceAtLeast(0.0)
    }

    private fun driveMinutes(): Int {
        if (!active || startedAt <= 0L) return 0
        val now = if (paused && pauseStartedAt > 0L) pauseStartedAt else System.currentTimeMillis()
        val elapsed = now - startedAt - pausedTotal
        if (elapsed < 0L || elapsed > MAX_SESSION_MS) {
            recordError("Timer verworfen: $elapsed ms")
            startedAt = System.currentTimeMillis()
            pausedTotal = 0L
            pauseStartedAt = 0L
            saveRuntime()
            return 0
        }
        return (elapsed / 60_000L).toInt()
    }

    private fun recalculate() {
        try {
            val location = lastLocation
            val current = location?.let { GeoPoint(it.latitude, it.longitude) }
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val consumption = prefs.getFloat("consumption", 7.4f).toDouble().coerceAtLeast(3.0)
            val litres = estimatedLitres(consumption)
            val minutes = driveMinutes()
            val result = route
            val eta = result?.let { System.currentTimeMillis() + it.durationSec * 1_000L }
            val trafficDelay = result?.typicalDurationSec?.let {
                ((result.durationSec - it) / 60).coerceAtLeast(0)
            }
            val schedule = scheduleLight(eta)
            val pauseLight = when {
                minutes >= 150 -> Light.RED
                minutes >= 135 -> Light.YELLOW
                else -> Light.GREEN
            }
            val fuelLight = when {
                litres <= 12 -> Light.RED
                litres <= 24 -> Light.YELLOW
                else -> Light.GREEN
            }
            val geometry = result?.geometry.orEmpty()
            val tolls = result?.tolls?.takeIf { it.isNotEmpty() } ?: fallbackTolls(geometry)
            val updatedFuel = current?.let { updateFuelDistance(it, fuelSuggestion, geometry) }
            fuelSuggestion = updatedFuel
            val event = nextEvent(current, tolls, updatedFuel, litres, minutes, schedule)
            val compact = compactRoute(result)

            snapshot = TripSnapshot(
                active = active,
                paused = paused,
                stage = stage,
                lat = current?.lat,
                lon = current?.lon,
                speedKmh = if (location?.hasSpeed() == true) (location.speed * 3.6).roundToInt() else null,
                accuracyM = location?.accuracy?.roundToInt(),
                driveMinutes = minutes,
                distanceTravelledKm = distanceKm,
                remainingKm = result?.distanceM?.div(1000),
                etaEpochMs = eta,
                typicalDurationMin = result?.typicalDurationSec?.div(60),
                trafficDelayMin = trafficDelay,
                scheduleLight = schedule,
                pauseLight = pauseLight,
                fuelLight = fuelLight,
                fuelLitres = litres,
                fuelSuggestion = updatedFuel,
                nextTitle = event.title,
                nextDetail = event.detail,
                nextDistanceM = event.distance,
                routeGeoJson = compact.first,
                congestionJson = compact.second,
                tolls = tolls.take(30),
                apiOk = result != null,
                apiMessage = if (result != null) "Live-Verkehr aktiv" else snapshot.apiMessage,
                lastUpdatedEpochMs = System.currentTimeMillis()
            )

            if (System.currentTimeMillis() - startedAt >= WARMUP_MS) {
                checkAlerts(current, tolls, updatedFuel, litres, minutes, schedule)
            }
            saveRuntime()
            saveAndBroadcast()
            notifyLive()
        } catch (t: Throwable) {
            recordError("Berechnung: ${t.javaClass.simpleName}: ${t.message}")
            snapshot = snapshot.copy(
                apiOk = false,
                apiMessage = "Berechnungsfehler abgefangen",
                nextTitle = "Tracking läuft eingeschränkt",
                nextDetail = "Details unter Mehr → Letzter Fehler"
            )
            saveAndBroadcast()
            notifyLive()
        }
    }

    private fun updateFuelDistance(
        current: GeoPoint,
        suggestion: FuelSuggestion?,
        geometry: List<GeoPoint>
    ): FuelSuggestion? {
        suggestion ?: return null
        val routeDistance = Geo.distanceAheadOnRouteM(
            current,
            suggestion.point,
            geometry,
            maxCurrentOffsetM = 8_000.0,
            maxTargetOffsetM = 12_000.0,
            includeTargetOffset = true
        )?.div(1000.0)
        val distance = routeDistance ?: Geo.distanceM(current, suggestion.point) / 1000.0
        val old = suggestion.distanceAheadKm
        if (routeDistance == null && distance > old + 4.0 && old < 12.0) {
            fuelUpdatedAt = 0L
            return null
        }
        return suggestion.copy(distanceAheadKm = distance)
    }

    private fun compactRoute(result: RouteResult?): Pair<String, String> {
        if (result == null || result.geometry.size < 2) return "" to "[]"
        val source = result.geometry
        val step = ceil(source.size.toDouble() / MAX_ROUTE_POINTS).toInt().coerceAtLeast(1)
        val indices = mutableListOf<Int>()
        var i = 0
        while (i < source.size) {
            indices += i
            i += step
        }
        if (indices.last() != source.lastIndex) indices += source.lastIndex

        val coordinates = JSONArray()
        indices.forEach { index ->
            val p = source[index]
            coordinates.put(JSONArray().put(p.lon).put(p.lat))
        }
        val geo = JSONObject()
            .put("type", "LineString")
            .put("coordinates", coordinates)
            .toString()

        val sourceCongestion = runCatching { JSONArray(result.congestionJson) }
            .getOrDefault(JSONArray())
        val compactCongestion = JSONArray()
        for (n in 0 until indices.lastIndex) {
            val sourceIndex = indices[n]
                .coerceAtMost((sourceCongestion.length() - 1).coerceAtLeast(0))
            compactCongestion.put(
                if (sourceCongestion.length() > 0) {
                    sourceCongestion.optString(sourceIndex, "unknown")
                } else {
                    "unknown"
                }
            )
        }
        return geo to compactCongestion.toString()
    }

    private data class Event(val title: String, val detail: String, val distance: Int? = null)

    private fun nextEvent(
        current: GeoPoint?,
        tolls: List<TollPoint>,
        fuel: FuelSuggestion?,
        litres: Double,
        minutes: Int,
        schedule: Light
    ): Event {
        if (minutes >= 150) return Event("Pause jetzt", "Nächste sichere Möglichkeit anfahren.")
        if (schedule == Light.RED) {
            return Event(
                if (stage == Stage.SUNDAY) "Malibu anrufen" else "Hotel anrufen",
                "Die aktuelle ETA liegt außerhalb des entspannten Zeitfensters."
            )
        }
        if (current != null) {
            val geometry = route?.geometry.orEmpty()
            val toll = tolls
                .mapNotNull { toll ->
                    Geo.distanceAheadOnRouteM(
                        current, toll.point, geometry,
                        maxCurrentOffsetM = 8_000.0,
                        maxTargetOffsetM = 2_500.0,
                        includeTargetOffset = true
                    )?.let { toll to it }
                }
                .filter { it.second <= 20_000 }
                .minByOrNull { it.second }
            if (toll != null) {
                return Event("Mautstelle voraus", toll.first.name, toll.second.roundToInt())
            }
        }
        if (fuel != null && (fuel.distanceAheadKm <= 45.0 || litres <= 32.0)) {
            val price = fuel.pricePerLitre?.let { "${"%.3f".format(it)} €/l · " }.orEmpty()
            val detail = "$price${fuel.name} · Umweg ca. ${"%.1f".format(fuel.detourKm)} km"
            return Event(
                if (litres <= 20.0) "Tanken jetzt einplanen" else "Günstiger Tankstopp",
                detail,
                (fuel.distanceAheadKm * 1_000).roundToInt()
            )
        }
        if (minutes >= 135) return Event("Pause vorbereiten", "Spätestens in 15 Minuten anhalten.")
        return if (route != null) {
            Event("Planmäßig unterwegs", "Live-Route, Verkehr, Maut und Tankbedarf werden geprüft.")
        } else {
            Event("Route wird geladen", "GPS und Mapbox-Verbindung werden geprüft.")
        }
    }

    private fun fallbackTolls(geometry: List<GeoPoint>): List<TollPoint> =
        if (geometry.isEmpty()) emptyList()
        else TripConfig.fallbackTolls.filter {
            Geo.closestToPolylineM(it.point, geometry) <= 7_000
        }

    private fun tripDate(): LocalDate =
        if (stage == Stage.SATURDAY) LocalDate.of(2026, 7, 25)
        else LocalDate.of(2026, 7, 26)

    private fun scheduleLight(etaMs: Long?): Light {
        if (etaMs == null || LocalDate.now() != tripDate()) return Light.GREY
        val eta = Instant.ofEpochMilli(etaMs).atZone(ZoneId.systemDefault())
        val greenTime = if (stage == Stage.SATURDAY) LocalTime.of(21, 0) else LocalTime.of(18, 30)
        val yellowTime = if (stage == Stage.SATURDAY) LocalTime.of(22, 0) else LocalTime.of(19, 0)
        val green = tripDate().atTime(greenTime).atZone(ZoneId.systemDefault())
        val yellow = tripDate().atTime(yellowTime).atZone(ZoneId.systemDefault())
        return when {
            !eta.isAfter(green) -> Light.GREEN
            !eta.isAfter(yellow) -> Light.YELLOW
            else -> Light.RED
        }
    }

    private fun checkAlerts(
        current: GeoPoint?,
        tolls: List<TollPoint>,
        fuel: FuelSuggestion?,
        litres: Double,
        minutes: Int,
        schedule: Light
    ) {
        if (minutes >= 135 && announced.add("pause_135")) {
            alert("Pause vorbereiten", "In spätestens 15 Minuten anhalten.", false)
        }
        if (minutes >= 150 && announced.add("pause_150")) {
            alert("Pause jetzt", "Nächste sichere Möglichkeit anfahren.", true)
            speak("Pause jetzt. Bitte die nächste sichere Möglichkeit anfahren.")
        }
        if (schedule == Light.RED && announced.add("late")) {
            alert(
                "Ankunft verspätet",
                if (stage == Stage.SUNDAY) "Malibu Village jetzt vorsorglich anrufen."
                else "Hotel jetzt vorsorglich anrufen.",
                true
            )
            speak("Ankunft verspätet. Bitte jetzt anrufen.")
        }
        if (litres <= 15 && announced.add("fuel_low")) {
            alert("Tanken empfohlen", "Geschätzter Tankinhalt: ${"%.1f".format(litres)} Liter.", true)
            speak("Tanken empfohlen.")
        }
        if (current == null) return

        tolls.forEach {
            proximity(
                id = "toll_${it.id}",
                label = it.name,
                current = current,
                target = it.point,
                isToll = true,
                thresholds = listOf(2_000, 500)
            )
        }
        fuel?.let {
            proximity(
                id = "fuel_${it.name}_${it.point.lat}_${it.point.lon}",
                label = fuelAlertText(it),
                current = current,
                target = it.point,
                isToll = false,
                thresholds = listOf(10_000, 2_000, 500)
            )
        }
    }

    private fun fuelAlertText(fuel: FuelSuggestion): String {
        val price = fuel.pricePerLitre?.let { " · ${"%.3f".format(it)} €/l" }.orEmpty()
        return "${fuel.name}$price · Umweg ca. ${"%.1f".format(fuel.detourKm)} km"
    }

    private fun proximity(
        id: String,
        label: String,
        current: GeoPoint,
        target: GeoPoint,
        isToll: Boolean,
        thresholds: List<Int>
    ) {
        val geometry = route?.geometry.orEmpty()
        val distance = Geo.distanceAheadOnRouteM(
            current,
            target,
            geometry,
            maxCurrentOffsetM = 8_000.0,
            maxTargetOffsetM = if (isToll) 2_500.0 else 12_000.0,
            includeTargetOffset = true
        ) ?: return
        val old = previousDistance.put(id, distance)
        if (old != null && distance > old + 150.0) return

        val crossed = thresholds.sortedDescending().filter { distance <= it }
        val newlyCrossed = crossed.filter { "${id}_$it" !in announced }
        if (newlyCrossed.isEmpty()) return
        crossed.forEach { announced.add("${id}_$it") }
        val threshold = newlyCrossed.minOrNull() ?: return
        val distanceText = if (threshold < 1_000) "$threshold m" else "${threshold / 1_000} km"
        val title = if (isToll) "Mautstelle in $distanceText" else "Tankstopp in $distanceText"
        alert(title, label, threshold <= 500)
        if (threshold <= 500) {
            speak(
                if (isToll) "Mautstelle in fünfhundert Metern."
                else "Empfohlene Tankstelle in fünfhundert Metern."
            )
        }
    }

    private fun togglePause() {
        if (!active) return
        paused = !paused
        if (paused) {
            pauseStartedAt = System.currentTimeMillis()
        } else if (pauseStartedAt > 0L) {
            pausedTotal += System.currentTimeMillis() - pauseStartedAt
            pauseStartedAt = 0L
        }
        saveRuntime()
        recalculate()
    }

    private fun resetBreak() {
        startedAt = System.currentTimeMillis()
        pausedTotal = 0L
        pauseStartedAt = 0L
        paused = false
        announced.remove("pause_135")
        announced.remove("pause_150")
        saveRuntime()
        recalculate()
    }

    private fun resetFuel() {
        distanceKm = 0.0
        fuelSuggestion = null
        fuelUpdatedAt = 0L
        announced.removeAll { it.startsWith("fuel_") }
        saveRuntime()
        lastLocation?.let { maybeRefreshFuel(it, force = false) }
        recalculate()
        speak("Tank als voll markiert.")
    }

    private fun stopTrip() {
        runCatching { fused.removeLocationUpdates(callback) }
        sessionId = System.nanoTime()
        routeRequestId++
        fuelRequestId++
        active = false
        paused = false
        runtime.edit().clear().apply()
        snapshot = snapshot.copy(active = false, paused = false, nextTitle = "Tracking beendet")
        saveAndBroadcast()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foreground(text: String) {
        val notification = liveNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(LIVE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(LIVE_ID, notification)
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(LIVE_CHANNEL, "Laufende Reise", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Reisewarnungen", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                description = "Maut, Tanken, Pausen und Ankunft"
            }
        )
    }

    private fun liveNotification(text: String): Notification {
        fun servicePi(code: Int, action: String) = PendingIntent.getService(
            this,
            code,
            Intent(this, TripTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(
                if (stage == Stage.SATURDAY) "Schwerin → Montbéliard"
                else "Montbéliard → Canet"
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    20,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (paused) "Weiter" else "Pause", servicePi(11, ACTION_PAUSE))
            .addAction(0, "Pause erledigt", servicePi(12, ACTION_BREAK_DONE))
            .addAction(0, "Vollgetankt", servicePi(13, ACTION_REFUEL_FULL))
            .addAction(0, "Stop", servicePi(14, ACTION_STOP))
            .build()
    }

    private fun notifyLive() {
        if (!active) return
        val eta = snapshot.etaEpochMs?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        } ?: "–"
        val distance = snapshot.nextDistanceM?.let {
            if (it < 1_000) "$it m" else "${"%.1f".format(it / 1_000.0)} km"
        }.orEmpty()
        val text = listOf(
            snapshot.nextTitle,
            distance,
            "ETA $eta",
            "${snapshot.fuelLitres.roundToInt()} L"
        ).filter { it.isNotBlank() }.joinToString(" · ")
        getSystemService(NotificationManager::class.java)
            .notify(LIVE_ID, liveNotification(text))
    }

    private fun alert(title: String, text: String, urgent: Boolean) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    21,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        getSystemService(NotificationManager::class.java)
            .notify((title + text).hashCode(), notification)
    }

    private fun speak(text: String) {
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("voice_alerts", true) && ttsReady) {
            runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trip") }
                .onFailure { recordError("TTS: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun saveRuntime() {
        runtime.edit()
            .putBoolean("active", active)
            .putBoolean("paused", paused)
            .putString("stage", stage.name)
            .putLong("started_at", startedAt)
            .putLong("pause_started_at", pauseStartedAt)
            .putLong("paused_total", pausedTotal)
            .putFloat("distance_km", distanceKm.toFloat())
            .commit()
    }

    private fun saveAndBroadcast() {
        val raw = snapshot.json().toString()
        getSharedPreferences("trip_state", MODE_PRIVATE).edit()
            .putString("snapshot", raw)
            .apply()
        runCatching {
            sendBroadcast(Intent(ACTION_UPDATE).apply {
                setPackage(packageName)
                putExtra("snapshot", raw)
            })
        }.onFailure {
            recordError("Broadcast: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun restore() {
        snapshot = TripSnapshot.fromJson(
            getSharedPreferences("trip_state", MODE_PRIVATE).getString("snapshot", null)
        )
        active = runtime.getBoolean("active", false)
        paused = runtime.getBoolean("paused", false)
        stage = runCatching {
            Stage.valueOf(runtime.getString("stage", Stage.SATURDAY.name)!!)
        }.getOrDefault(Stage.SATURDAY)
        startedAt = runtime.getLong("started_at", 0L)
        pauseStartedAt = runtime.getLong("pause_started_at", 0L)
        pausedTotal = runtime.getLong("paused_total", 0L)
        distanceKm = runtime.getFloat("distance_km", 0f).toDouble()
        fuelSuggestion = snapshot.fuelSuggestion
        sessionId = System.nanoTime()
        if (!runtimeIsValid()) {
            active = false
            paused = false
            startedAt = 0L
            pauseStartedAt = 0L
            pausedTotal = 0L
            runtime.edit().clear().apply()
            snapshot = snapshot.copy(active = false, paused = false, driveMinutes = 0)
        }
    }

    private fun recordError(message: String) {
        diagnostics.edit()
            .putString("last_error", "${LocalTime.now().withNano(0)} · ${message.take(220)}")
            .apply()
    }

    override fun onDestroy() {
        sessionId = System.nanoTime()
        routeRequestId++
        fuelRequestId++
        runCatching { fused.removeLocationUpdates(callback) }
        worker.shutdownNow()
        runCatching { tts?.shutdown() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
