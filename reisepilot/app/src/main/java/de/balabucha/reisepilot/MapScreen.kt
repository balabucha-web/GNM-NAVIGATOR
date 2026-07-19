package de.balabucha.reisepilot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
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
    val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val token = normalizeMapboxToken(prefs.getString("mapbox_token", "").orEmpty())
    val valid = mapboxTokenLooksValid(token)

    Page("Live-Karte", "Route, Verkehr, Maut und Tankstopps", modifier) {
        item {
            StatusLine(
                "Kartenzugang",
                when {
                    snapshot.apiOk -> "Live-Verkehr verbunden"
                    prefs.getBoolean("mapbox_token_valid", false) -> "Token geprüft · Tracking starten"
                    valid -> "Token gespeichert · unter Mehr prüfen"
                    else -> "Token fehlt oder ist unvollständig"
                },
                when {
                    snapshot.apiOk || prefs.getBoolean("mapbox_token_valid", false) -> Light.GREEN
                    valid -> Light.YELLOW
                    else -> Light.RED
                }
            )
        }

        if (!valid) {
            item {
                WarningCard(
                    "Karte noch nicht verfügbar",
                    "Unter Mehr den vollständigen Mapbox-Token eintragen und auf „Speichern und Verbindung prüfen“ drücken.",
                    Light.YELLOW
                )
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth().height(470.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFEAF0F5)
                )
            ) {
                MapWebView(token, snapshot)
            }
        }

        item {
            AppCard {
                Text("Legende", style = MaterialTheme.typography.titleMedium)
                StatusLine("Grün", "freie Strecke / planmäßig", Light.GREEN)
                StatusLine("Gelb", "beobachten / vorbereiten", Light.YELLOW)
                StatusLine("Rot", "Stau oder sofortiger Handlungsbedarf", Light.RED)
            }
        }

        item {
            Button(
                onClick = { activity.openMaps(snapshot.stage) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Navigation mit Google Maps") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapWebView(token: String, snapshot: TripSnapshot) {
    val payload = snapshot.json().put("token", token).toString()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                tag = payload
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        sendMapPayload(view, view.tag as? String ?: payload)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            view.loadData(
                                "<html><body style='font-family:sans-serif;padding:24px;background:#eaf0f5;color:#18263f'><h3>Karte konnte nicht geladen werden</h3><p>Internet und Mapbox-Token prüfen.</p></body></html>",
                                "text/html",
                                "UTF-8"
                            )
                        }
                    }
                }
                loadUrl("file:///android_asset/map.html")
            }
        },
        update = { web ->
            web.tag = payload
            if (web.progress == 100) {
                web.post { sendMapPayload(web, payload) }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun sendMapPayload(web: WebView, payload: String) {
    web.evaluateJavascript(
        "window.ReisePilot && window.ReisePilot.update(${JSONObject.quote(payload)});",
        null
    )
}
