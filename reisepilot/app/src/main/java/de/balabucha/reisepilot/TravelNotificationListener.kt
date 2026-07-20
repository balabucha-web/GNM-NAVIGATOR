package de.balabucha.reisepilot

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class TravelNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = scan()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = scan()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = scan()

    private fun scan() {
        val maps = runCatching {
            activeNotifications.any { it.packageName == "com.google.android.apps.maps" }
        }.getOrDefault(false)
        getSharedPreferences("app_status", MODE_PRIVATE).edit()
            .putBoolean("maps", maps)
            .apply()
        sendBroadcast(Intent(TripTrackingService.ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra("appsChanged", true)
        })
    }
}
