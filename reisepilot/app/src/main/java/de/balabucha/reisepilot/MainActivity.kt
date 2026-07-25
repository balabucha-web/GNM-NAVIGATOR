package de.balabucha.reisepilot

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    companion object {
        private const val STARTUP_LOCATION_REQUEST = 5601
    }

    private var snapshot by mutableStateOf(TripSnapshot())
    private lateinit var realtime54: RealtimeDriveController54
    private lateinit var nearby56: NearbyLiveController56

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("snapshot")?.let {
                snapshot = TripSnapshot.fromJson(it)
                if (::realtime54.isInitialized) realtime54.onSnapshot(snapshot)
                if (::nearby56.isInitialized) nearby56.onSnapshot(snapshot)
            }
            if (intent?.getBooleanExtra("appsChanged", false) == true) {
                snapshot = snapshot.copy(lastUpdatedEpochMs = System.currentTimeMillis())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSnapshot()
        realtime54 = RealtimeDriveController54(this)
        nearby56 = NearbyLiveController56(this)
        realtime54.onSnapshot(snapshot)
        nearby56.onSnapshot(snapshot)
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(TripTrackingService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent { ReiseTheme { ReisePilotApp(this, snapshot) } }
        maybeRequestStartupLocation()
    }

    override fun onResume() {
        super.onResume()
        loadSnapshot()
        if (::realtime54.isInitialized) {
            realtime54.onSnapshot(snapshot)
            realtime54.onResume()
        }
        if (::nearby56.isInitialized) {
            nearby56.onSnapshot(snapshot)
            nearby56.onResume()
        }
    }

    override fun onPause() {
        if (::realtime54.isInitialized) realtime54.onPause()
        if (::nearby56.isInitialized) nearby56.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        if (::realtime54.isInitialized) realtime54.destroy()
        if (::nearby56.isInitialized) nearby56.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STARTUP_LOCATION_REQUEST && ::nearby56.isInitialized) {
            nearby56.onPermissionChanged()
        }
    }

    private fun maybeRequestStartupLocation() {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("startup_live_location_prompted_v56", false)) return
        prefs.edit().putBoolean("startup_live_location_prompted_v56", true).apply()
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            STARTUP_LOCATION_REQUEST
        )
    }

    private fun loadSnapshot() {
        val loaded = TripSnapshot.fromJson(
            getSharedPreferences("trip_state", MODE_PRIVATE).getString("snapshot", null)
        )
        snapshot = if (
            !loaded.active && loaded.tripMode == TripMode.TEST &&
            tripModeAt(java.time.Instant.now()) == TripMode.REAL
        ) {
            TripSnapshot(
                stage = loaded.stage,
                tripMode = TripMode.REAL,
                nextTitle = "Reise bereit",
                nextDetail = "Reise starten"
            ).also {
                getSharedPreferences("trip_state", MODE_PRIVATE).edit()
                    .putString("snapshot", it.json().toString())
                    .apply()
            }
        } else {
            loaded
        }
    }

    fun startTrip(stage: Stage) {
        val action = if (stage == Stage.SATURDAY) {
            TripTrackingService.ACTION_START_SAT
        } else {
            TripTrackingService.ACTION_START_SUN
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, TripTrackingService::class.java).setAction(action)
        )
    }

    fun serviceAction(action: String) {
        startService(Intent(this, TripTrackingService::class.java).setAction(action))
    }

    fun reloadTripConfig() {
        serviceAction(TripTrackingService.ACTION_RELOAD_CONFIG)
    }

    fun refreshRoadAhead54() {
        if (snapshot.active) {
            if (::realtime54.isInitialized) realtime54.forceRefresh()
        } else {
            if (::nearby56.isInitialized) nearby56.forceRefresh()
        }
    }

    fun updateKeepScreenOn(tripActive: Boolean) {
        val enabled = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("keep_screen_on", true) && tripActive
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun updateApiStatus(ok: Boolean, message: String) {
        snapshot = snapshot.copy(apiOk = ok, apiMessage = message)
        getSharedPreferences("trip_state", MODE_PRIVATE).edit()
            .putString("snapshot", snapshot.json().toString())
            .apply()
    }

    fun openMaps(stage: Stage) {
        val uri = if (stage == Stage.SATURDAY) {
            "https://www.google.com/maps/dir/?api=1&origin=19057+Schwerin&destination=greet+H%C3%B4tel+Montb%C3%A9liard&travelmode=driving"
        } else {
            "https://www.google.com/maps/dir/?api=1&origin=greet+H%C3%B4tel+Montb%C3%A9liard&destination=Malibu+Village+Canet-en-Roussillon&travelmode=driving"
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    fun openPointRoute(point: GeoPoint, label: String) {
        val destination = "${point.lat},${point.lon}"
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}" +
                "&destination_place_id=&travelmode=driving"
        )
        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra("query", label)
        })
    }

    fun openMapSearch(query: String) {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
            )
        )
    }

    fun openWeb(url: String) {
        if (url.startsWith("https://")) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun openPackage(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                )
            )
        }
    }

    fun openBisonFute() {
        val pkg = "fr.gouv.bisonfute"
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.bison-fute.gouv.fr/information-trafic.html")
                )
            )
        }
    }

    fun dial(number: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }
}
