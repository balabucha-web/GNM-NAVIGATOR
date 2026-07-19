package de.balabucha.reisepilot

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun normalizeMapboxToken(raw: String): String = raw
    .trim()
    .removePrefix("Bearer ")
    .trim('"', '\'', ' ', '\n', '\r', '\t')
    .replace("\n", "")
    .replace("\r", "")
    .replace(" ", "")

@Composable
fun MoreScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val prefs = remember { activity.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("mapbox_token", "").orEmpty()) }
    var litres by remember { mutableStateOf(prefs.getFloat("start_litres", 60f).toString()) }
    var consumption by remember { mutableStateOf(prefs.getFloat("consumption", 7.4f).toString()) }
    var voice by remember { mutableStateOf(prefs.getBoolean("voice_alerts", true)) }
    var saved by remember { mutableStateOf(false) }
    var tokenStatus by remember { mutableStateOf(
        if (normalizeMapboxToken(token).startsWith("pk.")) "Token gespeichert · noch nicht geprüft" else "Kein gültiger öffentlicher Token gespeichert"
    ) }
    var tokenLight by remember { mutableStateOf(if (normalizeMapboxToken(token).startsWith("pk.")) Light.YELLOW else Light.RED) }
    var checkingToken by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val diagnosticPrefs = remember { activity.getSharedPreferences("diagnostics", Context.MODE_PRIVATE) }

    val nm = activity.getSystemService(NotificationManager::class.java)
    val listener = nm.isNotificationListenerAccessGranted(ComponentName(activity, TravelNotificationListener::class.java))
    val appPrefs = activity.getSharedPreferences("app_status", Context.MODE_PRIVATE)

    Page("Mehr", "Kartenzugang, Apps und Hintergrundbetrieb", modifier) {
        item {
            AppCard {
                Text("Mapbox-Kartenzugang", style = MaterialTheme.typography.titleLarge)
                Text("Öffentlichen Token mit pk. eintragen.", color = Muted)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    token,
                    { token = it; saved = false; tokenStatus = "Noch nicht geprüft"; tokenLight = Light.GREY },
                    label = { Text("Mapbox-Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(7.dp))
                StatusLine("Token", tokenStatus, tokenLight)
                Spacer(Modifier.height(9.dp))
                Button(onClick = {
                    val clean = normalizeMapboxToken(token)
                    token = clean
                    if (!clean.startsWith("pk.") || clean.length < 30) {
                        tokenStatus = "Ungültig · muss mit pk. beginnen"
                        tokenLight = Light.RED
                        return@Button
                    }
                    val committed = prefs.edit().putString("mapbox_token", clean)
                        .putFloat("start_litres", litres.replace(',', '.').toFloatOrNull() ?: 60f)
                        .putFloat("consumption", consumption.replace(',', '.').toFloatOrNull() ?: 7.4f)
                        .putBoolean("voice_alerts", voice).commit()
                    if (!committed) {
                        tokenStatus = "Speichern fehlgeschlagen"
                        tokenLight = Light.RED
                        return@Button
                    }
                    saved = true
                    checkingToken = true
                    tokenStatus = "Verbindung wird geprüft …"
                    tokenLight = Light.YELLOW
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { MapboxClient.route(clean, TripConfig.hotel, TripConfig.canet) }
                        }
                        checkingToken = false
                        result.onSuccess {
                            tokenStatus = "Verbunden · Live-Verkehr funktioniert"
                            tokenLight = Light.GREEN
                        }.onFailure {
                            tokenStatus = it.message?.take(90) ?: "API-Prüfung fehlgeschlagen"
                            tokenLight = Light.RED
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Text(if (checkingToken) "Prüfe …" else if (saved) "Erneut prüfen" else "Speichern und prüfen")
                }
            }
        }
        item {
            AppCard {
                Text("Fahrzeug", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(litres, { litres = it }, label = { Text("Starttank · Liter") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(consumption, { consumption = it }, label = { Text("l/100 km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.weight(1f)) { Text("Sprachwarnungen", fontWeight = FontWeight.Bold); Text("Maut, Pause und Tanken", color = Muted) }
                    Switch(voice, { voice = it })
                }
            }
        }
        item {
            AppCard {
                Text("Hintergrundbetrieb", style = MaterialTheme.typography.titleLarge)
                StatusLine("Tracking", if (snapshot.active) "aktiv" else "nicht gestartet", if (snapshot.active) Light.GREEN else Light.GREY)
                StatusLine("Live-API", if (snapshot.apiOk) "verbunden" else snapshot.apiMessage, if (snapshot.apiOk) Light.GREEN else Light.YELLOW)
                StatusLine("App-Erkennung", if (listener) "freigegeben" else "Zugriff fehlt", if (listener) Light.GREEN else Light.YELLOW)
                val lastError = diagnosticPrefs.getString("last_error", "").orEmpty()
                if (lastError.isNotBlank()) StatusLine("Letzter Fehler", lastError.take(90), Light.YELLOW)
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Benachrichtigungszugriff") }
                OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Android-App-Einstellungen") }
            }
        }
        item {
            AppCard {
                Text("Reise-Apps", style = MaterialTheme.typography.titleLarge)
                val maps = appPrefs.getBoolean("maps", false)
                val coyote = appPrefs.getBoolean("coyote", false)
                StatusLine("Google Maps", if (!listener) "unbekannt" else if (maps) "aktiv" else "nicht erkannt", if (!listener) Light.GREY else if (maps) Light.GREEN else Light.YELLOW)
                StatusLine("Coyote Frankreich", if (!listener) "unbekannt" else if (coyote) "aktiv" else "nicht erkannt", if (!listener) Light.GREY else if (coyote) Light.GREEN else Light.YELLOW)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(onClick = { activity.openPackage("com.google.android.apps.maps") }, modifier = Modifier.weight(1f)) { Text("Maps öffnen") }
                    OutlinedButton(onClick = { activity.openPackage("com.coyotesystems.android") }, modifier = Modifier.weight(1f)) { Text("Coyote öffnen") }
                }
                Spacer(Modifier.height(7.dp))
                Text("Die Blitzer-App wird nicht automatisch gestartet oder als Pflichtstatus überwacht.", color = Muted)
            }
        }
    }
}
