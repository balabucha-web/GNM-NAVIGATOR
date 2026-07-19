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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSnapshot()
        val filter = IntentFilter(TripTrackingService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        setContent { ReiseTheme { ReisePilotApp(this, snapshot) } }
    }

    override fun onResume() { super.onResume(); loadSnapshot() }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }

    private fun loadSnapshot() {
        snapshot = TripSnapshot.fromJson(getSharedPreferences("trip_state", MODE_PRIVATE).getString("snapshot", null))
    }

    fun startTrip(stage: Stage) {
        val action = if (stage == Stage.SATURDAY) TripTrackingService.ACTION_START_SAT else TripTrackingService.ACTION_START_SUN
        ContextCompat.startForegroundService(this, Intent(this, TripTrackingService::class.java).setAction(action))
    }

    fun serviceAction(action: String) { startService(Intent(this, TripTrackingService::class.java).setAction(action)) }

    fun openMaps(stage: Stage) {
        val uri = if (stage == Stage.SATURDAY)
            "https://www.google.com/maps/dir/?api=1&origin=19057+Schwerin&destination=greet+H%C3%B4tel+Montb%C3%A9liard&travelmode=driving"
        else "https://www.google.com/maps/dir/?api=1&origin=greet+H%C3%B4tel+Montb%C3%A9liard&destination=Malibu+Village+Canet-en-Roussillon&travelmode=driving"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    fun openPackage(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) startActivity(launch)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
    }

    fun dial(number: String) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}
