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
        private const val LIVE_CHANNEL = "trip_live_v31"
        private const val ALERT_CHANNEL = "trip_alerts_v31"
        private const val LIVE_ID = 301
        private const val WARMUP_MS = 60_000L
        private const val MAX_SESSION_MS = 24L * 60L * 60L * 1000L
        private const val MAX_ROUTE_POINTS = 360
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
    private var route: RouteResult? = null
    private var routeUpdatedAt = 0L
    private var startedAt = 0L
    private var pauseStartedAt = 0L
    private var pausedTotal = 0L
    private var distanceKm = 0.0
    private var snapshot = TripSnapshot()
    private val announced = mutableSetOf<String>()
    private val previousDistance = mutableMapOf<String, Double>()
    private val runtime by lazy { getSharedPreferences("trip_runtime_v31", MODE_PRIVATE) }
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
        active = true
        paused = false
        startedAt = System.currentTimeMillis()
        pauseStartedAt = 0L
        pausedTotal = 0L
        distanceKm = 0.0
        lastLocation = null
        lastRouteLocation = null
        route = null
        routeUpdatedAt = 0L
        announced.clear()
        previousDistance.clear()
        diagnostics.edit().remove("last_error").apply()
        snapshot = TripSnapshot(active = true, stage = stage, nextTitle = "GPS wird gestartet")
        saveRuntime()
        saveAndBroadcast()
        foreground("GPS wird gestartet")
        requestLocations()
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
                if (location.accuracy <= 100f && jump in 5f..2_000f) distanceKm += jump / 1000.0
            }
            lastLocation = location
            val moved = lastRouteLocation?.distanceTo(location) ?: Float.MAX_VALUE
            val due = System.currentTimeMillis() - routeUpdatedAt >= 5 * 60_000L
            if (route == null || moved >= 20_000f || due) refreshRoute(location)
            recalculate()
        } catch (t: Throwable) {
            recordError("Standort: ${t.javaClass.simpleName}: ${t.message}")
            snapshot = snapshot.copy(apiOk = false, apiMessage = "Trackingfehler abgefangen")
            saveAndBroadcast()
        }
    }

    private fun cleanToken(): String = getSharedPreferences("settings", MODE_PRIVATE)
        .getString("mapbox_token", "").orEmpty().trim().removePrefix("Bearer ")
        .trim('"', '\'', ' ', '\n', '\r', '\t')
        .replace("\n", "").replace("\r", "").replace(" ", "")

    private fun refreshRoute(location: Location) {
        val token = cleanToken()
        if (!token.startsWith("pk.") || token.length < 30) {
            snapshot = snapshot.copy(apiOk = false, apiMessage = "Mapbox-Token ungültig oder nicht gespeichert")
            saveAndBroadcast()
            return
        }
        routeUpdatedAt = System.currentTimeMillis()
        lastRouteLocation = Location(location)
        worker.execute {
            runCatching {
                MapboxClient.route(token, GeoPoint(location.latitude, location.longitude), TripConfig.destination(stage))
            }.onSuccess {
                route = it
                main.post(::recalculate)
            }.onFailure {
                main.post {
                    val msg = it.message ?: "Live-Route nicht verfügbar"
                    recordError("Mapbox: $msg")
                    snapshot = snapshot.copy(apiOk = false, apiMessage = msg.take(120))
                    saveAndBroadcast(); notifyLive()
                }
            }
        }
    }

    private fun driveMinutes(): Int {
        if (!active || startedAt <= 0L) return 0
        val now = if (paused && pauseStartedAt > 0L) pauseStartedAt else System.currentTimeMillis()
        val elapsed = now - startedAt - pausedTotal
        if (elapsed < 0L || elapsed > MAX_SESSION_MS) {
            recordError("Timer verworfen: $elapsed ms")
            startedAt = System.currentTimeMillis(); pausedTotal = 0L; pauseStartedAt = 0L
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
            val startLitres = prefs.getFloat("start_litres", 60f).toDouble()
            val consumption = prefs.getFloat("consumption", 7.4f).toDouble()
            val litres = (startLitres - distanceKm * consumption / 100.0).coerceAtLeast(0.0)
            val minutes = driveMinutes()
            val result = route
            val eta = result?.let { System.currentTimeMillis() + it.durationSec * 1_000L }
            val trafficDelay = result?.typicalDurationSec?.let { ((result.durationSec - it) / 60).coerceAtLeast(0) }
            val schedule = scheduleLight(eta)
            val pauseLight = when { minutes >= 150 -> Light.RED; minutes >= 135 -> Light.YELLOW; else -> Light.GREEN }
            val fuelLight = when { litres <= 12 -> Light.RED; litres <= 22 -> Light.YELLOW; else -> Light.GREEN }
            val geometry = result?.geometry.orEmpty()
            val tolls = result?.tolls?.takeIf { it.isNotEmpty() } ?: fallbackTolls(geometry)
            val event = nextEvent(current, geometry, tolls, litres, minutes, schedule)
            val compact = compactRoute(result)

            snapshot = TripSnapshot(
                active = active, paused = paused, stage = stage,
                lat = current?.lat, lon = current?.lon,
                speedKmh = if (location?.hasSpeed() == true) (location.speed * 3.6).roundToInt() else null,
                accuracyM = location?.accuracy?.roundToInt(), driveMinutes = minutes,
                distanceTravelledKm = distanceKm, remainingKm = result?.distanceM?.div(1000),
                etaEpochMs = eta, typicalDurationMin = result?.typicalDurationSec?.div(60),
                trafficDelayMin = trafficDelay, scheduleLight = schedule, pauseLight = pauseLight,
                fuelLight = fuelLight, fuelLitres = litres, nextTitle = event.title,
                nextDetail = event.detail, nextDistanceM = event.distance,
                routeGeoJson = compact.first, congestionJson = compact.second,
                tolls = tolls.take(30), apiOk = result != null,
                apiMessage = if (result != null) "Live-Verkehr aktiv" else snapshot.apiMessage,
                lastUpdatedEpochMs = System.currentTimeMillis()
            )
            if (System.currentTimeMillis() - startedAt >= WARMUP_MS) {
                checkAlerts(current, geometry, tolls, litres, minutes, schedule)
            }
            saveRuntime(); saveAndBroadcast(); notifyLive()
        } catch (t: Throwable) {
            recordError("Berechnung: ${t.javaClass.simpleName}: ${t.message}")
            snapshot = snapshot.copy(apiOk = false, apiMessage = "Berechnungsfehler abgefangen", nextTitle = "Tracking läuft eingeschränkt", nextDetail = "Details unter Mehr → Letzter Fehler")
            saveAndBroadcast(); notifyLive()
        }
    }

    private fun compactRoute(result: RouteResult?): Pair<String, String> {
        if (result == null || result.geometry.size < 2) return "" to "[]"
        val source = result.geometry
        val step = ceil(source.size.toDouble() / MAX_ROUTE_POINTS).toInt().coerceAtLeast(1)
        val indices = mutableListOf<Int>()
        var i = 0
        while (i < source.size) { indices += i; i += step }
        if (indices.last() != source.lastIndex) indices += source.lastIndex
        val coordinates = JSONArray()
        indices.forEach { index -> val p = source[index]; coordinates.put(JSONArray().put(p.lon).put(p.lat)) }
        val geo = JSONObject().put("type", "LineString").put("coordinates", coordinates).toString()
        val sourceCongestion = runCatching { JSONArray(result.congestionJson) }.getOrDefault(JSONArray())
        val compactCongestion = JSONArray()
        for (n in 0 until indices.lastIndex) {
            val sourceIndex = indices[n].coerceAtMost((sourceCongestion.length() - 1).coerceAtLeast(0))
            compactCongestion.put(if (sourceCongestion.length() > 0) sourceCongestion.optString(sourceIndex, "unknown") else "unknown")
        }
        return geo to compactCongestion.toString()
    }

    private data class Event(val title: String, val detail: String, val distance: Int? = null)

    private fun nextEvent(current: GeoPoint?, geometry: List<GeoPoint>, tolls: List<TollPoint>, litres: Double, minutes: Int, schedule: Light): Event {
        if (minutes >= 150) return Event("Pause jetzt", "Nächste sichere Möglichkeit anfahren.")
        if (schedule == Light.RED) return Event(if (stage == Stage.SUNDAY) "Malibu anrufen" else "Hotel anrufen", "Die aktuelle ETA liegt außerhalb des entspannten Zeitfensters.")
        if (current != null) {
            val toll = tolls.map { it to Geo.distanceM(current, it.point) }.filter { it.second <= 20_000 }.minByOrNull { it.second }
            if (toll != null) return Event("Mautstelle voraus", toll.first.name, toll.second.roundToInt())
            val fuel = nextFuel(current, geometry)
            if (fuel != null && (litres <= 22 || fuel.second <= 10_000)) return Event(if (litres <= 15) "Tanken empfohlen" else "Günstiger Tankstopp", fuel.first.name, fuel.second.roundToInt())
        }
        if (minutes >= 135) return Event("Pause vorbereiten", "Spätestens in 15 Minuten anhalten.")
        return if (route != null) Event("Planmäßig unterwegs", "Live-Route und Verkehr werden automatisch geprüft.") else Event("Route wird geladen", "GPS und Mapbox-Verbindung werden geprüft.")
    }

    private fun nextFuel(current: GeoPoint, geometry: List<GeoPoint>): Pair<GeoPoint, Double>? {
        val destination = TripConfig.destination(stage)
        val remaining = Geo.distanceM(current, destination)
        return TripConfig.fuelStops(stage).filter { Geo.distanceM(it, destination) < remaining }
            .filter { geometry.isEmpty() || Geo.closestToPolylineM(it, geometry) <= 8_000 }
            .map { it to Geo.distanceM(current, it) }.filter { it.second <= 200_000 }.minByOrNull { it.second }
    }

    private fun fallbackTolls(geometry: List<GeoPoint>): List<TollPoint> = if (geometry.isEmpty()) emptyList() else TripConfig.fallbackTolls.filter { Geo.closestToPolylineM(it.point, geometry) <= 7_000 }
    private fun tripDate(): LocalDate = if (stage == Stage.SATURDAY) LocalDate.of(2026, 7, 25) else LocalDate.of(2026, 7, 26)

    private fun scheduleLight(etaMs: Long?): Light {
        if (etaMs == null || LocalDate.now() != tripDate()) return Light.GREY
        val eta = Instant.ofEpochMilli(etaMs).atZone(ZoneId.systemDefault())
        val greenTime = if (stage == Stage.SATURDAY) LocalTime.of(21, 0) else LocalTime.of(18, 30)
        val yellowTime = if (stage == Stage.SATURDAY) LocalTime.of(22, 0) else LocalTime.of(19, 0)
        val green = tripDate().atTime(greenTime).atZone(ZoneId.systemDefault())
        val yellow = tripDate().atTime(yellowTime).atZone(ZoneId.systemDefault())
        return when { !eta.isAfter(green) -> Light.GREEN; !eta.isAfter(yellow) -> Light.YELLOW; else -> Light.RED }
    }

    private fun checkAlerts(current: GeoPoint?, geometry: List<GeoPoint>, tolls: List<TollPoint>, litres: Double, minutes: Int, schedule: Light) {
        if (minutes >= 135 && announced.add("pause_135")) alert("Pause vorbereiten", "In spätestens 15 Minuten anhalten.", false)
        if (minutes >= 150 && announced.add("pause_150")) { alert("Pause jetzt", "Nächste sichere Möglichkeit anfahren.", true); speak("Pause jetzt. Bitte die nächste sichere Möglichkeit anfahren.") }
        if (schedule == Light.RED && announced.add("late")) { alert("Ankunft verspätet", if (stage == Stage.SUNDAY) "Malibu Village jetzt vorsorglich anrufen." else "Hotel jetzt vorsorglich anrufen.", true); speak("Ankunft verspätet. Bitte jetzt anrufen.") }
        if (litres <= 15 && announced.add("fuel_low")) { alert("Tanken empfohlen", "Geschätzter Tankinhalt: ${"%.1f".format(litres)} Liter.", true); speak("Tanken empfohlen.") }
        if (current == null) return
        tolls.forEach { proximity("toll_${it.id}", it.name, current, it.point, true) }
        nextFuel(current, geometry)?.let { proximity("fuel_${it.first.name}", it.first.name, current, it.first, false) }
    }

    private fun proximity(id: String, label: String, current: GeoPoint, target: GeoPoint, isToll: Boolean) {
        val distance = Geo.distanceM(current, target)
        val old = previousDistance.put(id, distance)
        if (old != null && distance > old + 80) return
        if (distance <= 500 && announced.add("${id}_500")) {
            alert(if (isToll) "Mautstelle in 500 m" else "Tankstopp in 500 m", label, true)
            speak(if (isToll) "Mautstelle in fünfhundert Metern." else "Tankmöglichkeit in fünfhundert Metern.")
        } else if (distance <= 2_000 && announced.add("${id}_2000")) alert(if (isToll) "Mautstelle in 2 km" else "Tankstopp in 2 km", label, false)
    }

    private fun togglePause() {
        if (!active) return
        paused = !paused
        if (paused) pauseStartedAt = System.currentTimeMillis() else if (pauseStartedAt > 0L) { pausedTotal += System.currentTimeMillis() - pauseStartedAt; pauseStartedAt = 0L }
        saveRuntime(); recalculate()
    }

    private fun resetBreak() {
        startedAt = System.currentTimeMillis(); pausedTotal = 0L; pauseStartedAt = 0L; paused = false
        announced.remove("pause_135"); announced.remove("pause_150"); saveRuntime(); recalculate()
    }

    private fun resetFuel() { distanceKm = 0.0; announced.removeAll { it.startsWith("fuel_") }; recalculate(); speak("Tank als voll markiert.") }

    private fun stopTrip() {
        runCatching { fused.removeLocationUpdates(callback) }
        active = false; paused = false; runtime.edit().clear().apply()
        snapshot = snapshot.copy(active = false, paused = false, nextTitle = "Tracking beendet")
        saveAndBroadcast(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun foreground(text: String) {
        val n = liveNotification(text)
        if (Build.VERSION.SDK_INT >= 29) startForeground(LIVE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(LIVE_ID, n)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, "Laufende Reise", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Reisewarnungen", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true); description = "Maut, Tanken, Pausen und Ankunft" })
    }

    private fun liveNotification(text: String): Notification {
        fun servicePi(code: Int, action: String) = PendingIntent.getService(this, code, Intent(this, TripTrackingService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, LIVE_CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (stage == Stage.SATURDAY) "Schwerin → Montbéliard" else "Montbéliard → Canet")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (paused) "Weiter" else "Pause", servicePi(11, ACTION_PAUSE))
            .addAction(0, "Pause erledigt", servicePi(12, ACTION_BREAK_DONE))
            .addAction(0, "Vollgetankt", servicePi(13, ACTION_REFUEL_FULL))
            .addAction(0, "Stop", servicePi(14, ACTION_STOP)).build()
    }

    private fun notifyLive() {
        if (!active) return
        val eta = snapshot.etaEpochMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "–"
        val distance = snapshot.nextDistanceM?.let { if (it < 1000) "$it m" else "${"%.1f".format(it / 1000.0)} km" }.orEmpty()
        val text = listOf(snapshot.nextTitle, distance, "ETA $eta", "${snapshot.fuelLitres.roundToInt()} L").filter { it.isNotBlank() }.joinToString(" · ")
        getSystemService(NotificationManager::class.java).notify(LIVE_ID, liveNotification(text))
    }

    private fun alert(title: String, text: String, urgent: Boolean) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, 21, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        getSystemService(NotificationManager::class.java).notify((title + text).hashCode(), notification)
    }

    private fun speak(text: String) {
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("voice_alerts", true) && ttsReady) {
            runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trip") }
                .onFailure { recordError("TTS: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun saveRuntime() {
        runtime.edit().putBoolean("active", active).putBoolean("paused", paused).putString("stage", stage.name)
            .putLong("started_at", startedAt).putLong("pause_started_at", pauseStartedAt)
            .putLong("paused_total", pausedTotal).putFloat("distance_km", distanceKm.toFloat()).commit()
    }

    private fun saveAndBroadcast() {
        val raw = snapshot.json().toString()
        getSharedPreferences("trip_state", MODE_PRIVATE).edit().putString("snapshot", raw).apply()
        runCatching { sendBroadcast(Intent(ACTION_UPDATE).apply { setPackage(packageName); putExtra("snapshot", raw) }) }
            .onFailure { recordError("Broadcast: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun restore() {
        snapshot = TripSnapshot.fromJson(getSharedPreferences("trip_state", MODE_PRIVATE).getString("snapshot", null))
        active = runtime.getBoolean("active", false); paused = runtime.getBoolean("paused", false)
        stage = runCatching { Stage.valueOf(runtime.getString("stage", Stage.SATURDAY.name)!!) }.getOrDefault(Stage.SATURDAY)
        startedAt = runtime.getLong("started_at", 0L); pauseStartedAt = runtime.getLong("pause_started_at", 0L)
        pausedTotal = runtime.getLong("paused_total", 0L); distanceKm = runtime.getFloat("distance_km", 0f).toDouble()
        if (!runtimeIsValid()) {
            active = false; paused = false; startedAt = 0L; pauseStartedAt = 0L; pausedTotal = 0L
            runtime.edit().clear().apply(); snapshot = snapshot.copy(active = false, paused = false, driveMinutes = 0)
        }
    }

    private fun recordError(message: String) {
        diagnostics.edit().putString("last_error", "${LocalTime.now().withNano(0)} · ${message.take(180)}").apply()
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }; worker.shutdownNow(); runCatching { tts?.shutdown() }; super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
