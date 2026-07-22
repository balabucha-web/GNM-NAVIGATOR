package de.balabucha.reisepilot

import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var snapshot by mutableStateOf(TripSnapshot())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("snapshot")?.let { snapshot = TripSnapshot.fromJson(it) }
            if (intent?.getBooleanExtra("appsChanged", false) == true) {
                snapshot = snapshot.copy(lastUpdatedEpochMs = System.currentTimeMillis())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSnapshot()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(TripTrackingService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent { ReiseTheme { ReisePilotApp(this, snapshot) } }
    }

    override fun onResume() {
        super.onResume()
        loadSnapshot()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun loadSnapshot() {
        snapshot = TripSnapshot.fromJson(
            getSharedPreferences("trip_state", MODE_PRIVATE).getString("snapshot", null)
        )
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
