package de.balabucha.reisepilot

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class TravelNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = scan()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = scan()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = scan()

    private fun scan() {
        var maps = false
        var coyote = false
        var blitzer = false
        runCatching {
            activeNotifications.forEach {
                when (it.packageName) {
                    "com.google.android.apps.maps" -> maps = true
                    "com.coyotesystems.android" -> coyote = true
                    "de.blitzer.plus", "de.blitzer" -> blitzer = true
                }
            }
        }
        getSharedPreferences("app_status", MODE_PRIVATE).edit()
            .putBoolean("maps", maps)
            .putBoolean("coyote", coyote)
            .putBoolean("blitzer", blitzer)
            .apply()
        sendBroadcast(Intent(TripTrackingService.ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra("appsChanged", true)
        })
    }
}
