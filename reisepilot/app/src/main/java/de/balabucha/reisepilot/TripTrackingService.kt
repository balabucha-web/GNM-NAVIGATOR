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
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
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
        private const val LIVE_CHANNEL = "trip_live"
        private const val ALERT_CHANNEL = "trip_alerts"
        private const val LIVE_ID = 301
    }

    private lateinit var fused: FusedLocationProviderClient
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
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
        if (ttsReady) tts?.language = Locale.GERMAN
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SAT -> start(Stage.SATURDAY)
            ACTION_START_SUN -> start(Stage.SUNDAY)
            ACTION_PAUSE -> togglePause()
            ACTION_BREAK_DONE -> resetBreak()
            ACTION_REFUEL_FULL -> resetFuel()
            ACTION_STOP -> stopTrip()
            null -> if (active) resumeAfterRestart()
        }
        return START_STICKY
    }

    private fun start(newStage: Stage) {
        stage = newStage
        active = true
        paused = false
        startedAt = System.currentTimeMillis()
        pausedTotal = 0L
        pauseStartedAt = 0L
        distanceKm = 0.0
        lastLocation = null
        lastRouteLocation = null
        route = null
        routeUpdatedAt = 0L
        announced.clear()
        previousDistance.clear()
        snapshot = TripSnapshot(active = true, stage = stage)
        saveAndBroadcast()
        foreground("GPS wird gestartet")
        requestLocations()
    }

    private fun resumeAfterRestart() {
        foreground("Tracking wird fortgesetzt")
        requestLocations()
    }

    private fun requestLocations() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            alert("Standort fehlt", "Standortberechtigung wurde nicht erteilt.", true)
            stopTrip()
            return
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun onLocation(location: Location) {
        lastLocation?.let { old ->
            val jump = old.distanceTo(location)
            if (location.accuracy <= 100f && jump in 5f..2_000f) distanceKm += jump / 1000.0
        }
        lastLocation = location
        val moved = lastRouteLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val due = System.currentTimeMillis() - routeUpdatedAt >= 5 * 60_000L
        if (route == null || moved >= 20_000f || due) refreshRoute(location)
        recalculate()
    }

    private fun refreshRoute(location: Location) {
        val token = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("mapbox_token", "").orEmpty().trim()
        if (!token.startsWith("pk.")) {
            snapshot = snapshot.copy(apiOk = false, apiMessage = "Mapbox-Token fehlt")
            saveAndBroadcast()
            return
        }
        routeUpdatedAt = System.currentTimeMillis()
        lastRouteLocation = Location(location)
        executor.execute {
            runCatching {
                MapboxClient.route(token, GeoPoint(location.latitude, location.longitude), TripConfig.destination(stage))
            }.onSuccess {
                route = it
                mainHandler.post(::recalculate)
            }.onFailure {
                mainHandler.post {
                    snapshot = snapshot.copy(apiOk = false, apiMessage = it.message ?: "Live-Route nicht verfügbar")
                    saveAndBroadcast(); notifyLive()
                }
            }
        }
    }

    private fun driveMinutes(): Int {
        if (!active) return 0
        val end = if (paused && pauseStartedAt > 0) pauseStartedAt else System.currentTimeMillis()
        return ((end - startedAt - pausedTotal).coerceAtLeast(0L) / 60_000L).toInt()
    }

    private fun recalculate() {
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
            nextTitle = event.title,
            nextDetail = event.detail,
            nextDistanceM = event.distance,
            routeGeoJson = result?.geometryJson.orEmpty(),
            congestionJson = result?.congestionJson ?: "[]",
            tolls = tolls,
            apiOk = result != null,
            apiMessage = if (result != null) "Live-Verkehr aktiv" else snapshot.apiMessage,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        checkAlerts(current, geometry, tolls, litres, minutes, schedule)
        saveAndBroadcast()
        notifyLive()
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
        return Event(if (route != null) "Planmäßig unterwegs" else "Kartenzugang einrichten", if (route != null) "Live-Route und Verkehr werden automatisch geprüft." else "Öffentlichen Mapbox-Token in Einstellungen eintragen.")
    }

    private fun nextFuel(current: GeoPoint, geometry: List<GeoPoint>): Pair<GeoPoint, Double>? {
        val destination = TripConfig.destination(stage)
        val remaining = Geo.distanceM(current, destination)
        return TripConfig.fuelStops(stage)
            .filter { Geo.distanceM(it, destination) < remaining }
            .filter { geometry.isEmpty() || Geo.closestToPolylineM(it, geometry) <= 8_000 }
            .map { it to Geo.distanceM(current, it) }
            .filter { it.second <= 200_000 }
            .minByOrNull { it.second }
    }

    private fun fallbackTolls(geometry: List<GeoPoint>): List<TollPoint> = if (geometry.isEmpty()) emptyList() else TripConfig.fallbackTolls.filter { Geo.closestToPolylineM(it.point, geometry) <= 7_000 }

    private fun scheduleLight(etaMs: Long?): Light {
        if (etaMs == null) return Light.GREY
        val eta = Instant.ofEpochMilli(etaMs).atZone(ZoneId.systemDefault())
        val green = eta.toLocalDate().atTime(if (stage == Stage.SATURDAY) LocalTime.of(21, 0) else LocalTime.of(18, 30)).atZone(ZoneId.systemDefault())
        val yellow = eta.toLocalDate().atTime(if (stage == Stage.SATURDAY) LocalTime.of(22, 0) else LocalTime.of(19, 0)).atZone(ZoneId.systemDefault())
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
        } else if (distance <= 2_000 && announced.add("${id}_2000")) {
            alert(if (isToll) "Mautstelle in 2 km" else "Tankstopp in 2 km", label, false)
        }
    }

    private fun togglePause() {
        if (!active) return
        paused = !paused
        if (paused) pauseStartedAt = System.currentTimeMillis() else if (pauseStartedAt > 0) pausedTotal += System.currentTimeMillis() - pauseStartedAt
        recalculate()
    }

    private fun resetBreak() {
        startedAt = System.currentTimeMillis(); pausedTotal = 0L; pauseStartedAt = 0L; paused = false
        announced.remove("pause_135"); announced.remove("pause_150"); recalculate()
    }

    private fun resetFuel() {
        distanceKm = 0.0; announced.removeAll { it.startsWith("fuel_") }; recalculate(); speak("Tank als voll markiert.")
    }

    private fun stopTrip() {
        runCatching { fused.removeLocationUpdates(callback) }
        active = false
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
        return NotificationCompat.Builder(this, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
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
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, 21, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        getSystemService(NotificationManager::class.java).notify((title + text).hashCode(), notification)
    }

    private fun speak(text: String) {
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("voice_alerts", true) && ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trip")
    }

    private fun saveAndBroadcast() {
        getSharedPreferences("trip_state", MODE_PRIVATE).edit().putString("snapshot", snapshot.json().toString()).apply()
        sendBroadcast(Intent(ACTION_UPDATE).apply { setPackage(packageName); putExtra("snapshot", snapshot.json().toString()) })
    }

    private fun restore() {
        snapshot = TripSnapshot.fromJson(getSharedPreferences("trip_state", MODE_PRIVATE).getString("snapshot", null))
        stage = snapshot.stage; active = snapshot.active; paused = snapshot.paused; distanceKm = snapshot.distanceTravelledKm
    }

    override fun onDestroy() { runCatching { fused.removeLocationUpdates(callback) }; executor.shutdownNow(); tts?.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
