package de.balabucha.reisepilot

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
fun MapScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val token = activity.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mapbox_token", "").orEmpty()
    Page("Live-Karte", "Route, Verkehr, Maut und Tankstopps", modifier) {
        if (!token.startsWith("pk.")) item { WarningCard("Kartenzugang fehlt", "Unter Mehr einen öffentlichen Mapbox-Token eintragen.", Light.YELLOW) }
        item {
            Card(Modifier.fillMaxWidth().height(470.dp), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Line)) {
                MapWebView(token, snapshot)
            }
        }
        item {
            AppCard {
                Text("Legende", style = MaterialTheme.typography.titleMedium)
                StatusLine("Grün", "frei / planmäßig", Light.GREEN)
                StatusLine("Gelb", "beobachten / vorbereiten", Light.YELLOW)
                StatusLine("Rot", "jetzt handeln", Light.RED)
            }
        }
        item {
            Button(onClick = { activity.openMaps(snapshot.stage) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Text("Navigation mit Google Maps")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapWebView(token: String, snapshot: TripSnapshot) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = WebViewClient()
                loadUrl("file:///android_asset/map.html")
            }
        },
        update = { web ->
            val payload = snapshot.json().put("token", token).toString()
            web.post { web.evaluateJavascript("window.ReisePilot && window.ReisePilot.update(${JSONObject.quote(payload)});", null) }
        },
        modifier = Modifier.fillMaxSize()
    )
}
