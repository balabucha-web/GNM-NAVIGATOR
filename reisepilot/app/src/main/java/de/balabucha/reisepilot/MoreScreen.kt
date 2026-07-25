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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun MoreScreen(
    activity: MainActivity,
    snapshot: TripSnapshot,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val prefs = remember { activity.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val diagnostics = remember { activity.getSharedPreferences("diagnostics", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("mapbox_token", "").orEmpty()) }
    var tankerKey by remember { mutableStateOf(prefs.getString("tankerkoenig_key", "").orEmpty()) }
    var litres by remember { mutableStateOf(prefs.getFloat("start_litres", 60f).toString()) }
    var consumption by remember { mutableStateOf(prefs.getFloat("consumption", 7.4f).toString()) }
    var voice by remember { mutableStateOf(prefs.getBoolean("voice_alerts", true)) }
    var checking by remember { mutableStateOf(false) }
    var checkingLiveSources by remember { mutableStateOf(false) }
    var liveSources by remember { mutableStateOf<List<LiveSourceStatus>>(emptyList()) }
    var confirmTestReset by remember { mutableStateOf(false) }
    var tokenStatus by remember {
        mutableStateOf(
            when {
                prefs.getBoolean("mapbox_token_valid", false) -> "Geprüft · Live-Verkehr bereit"
                mapboxTokenLooksValid(token) -> "Gespeichert · Verbindung noch nicht geprüft"
                else -> "Kein vollständiger öffentlicher Token gespeichert"
            }
        )
    }
    var tokenLight by remember {
        mutableStateOf(
            when {
                prefs.getBoolean("mapbox_token_valid", false) -> Light.GREEN
                mapboxTokenLooksValid(token) -> Light.YELLOW
                else -> Light.RED
            }
        )
    }
    val scope = rememberCoroutineScope()

    val nm = activity.getSystemService(NotificationManager::class.java)
    val listener = nm.isNotificationListenerAccessGranted(
        ComponentName(activity, TravelNotificationListener::class.java)
    )
    val appPrefs = activity.getSharedPreferences("app_status", Context.MODE_PRIVATE)
    val mapsActive = appPrefs.getBoolean("maps", false)
    val mapsInstalled = activity.isInstalled("com.google.android.apps.maps")
    val bisonInstalled = activity.isInstalled("fr.gouv.bisonfute")
    val hasTestData = snapshot.tripMode == TripMode.TEST && (
        snapshot.active || snapshot.distanceTravelledKm > 0.01 ||
            snapshot.driveMinutes > 0 || snapshot.remainingKm != null
        )

    Page("Einstellungen", "", modifier) {
        item {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Zurück zu Mehr") }
        }
        item {
            AppCard {
                Text(
                    "Karte und Live-Verkehr",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("technical-settings-screen")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        tokenStatus = "Änderung noch nicht gespeichert"
                        tokenLight = Light.GREY
                    },
                    label = { Text("Mapbox-Token") },
                    supportingText = { Text(mapboxTokenSummary(token)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                StatusLine("Verbindung", tokenStatus, tokenLight)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val clean = normalizeMapboxToken(token)
                        token = clean
                        if (!mapboxTokenLooksValid(clean)) {
                            tokenStatus = "Unvollständig · ${mapboxTokenSummary(clean)}"
                            tokenLight = Light.RED
                            prefs.edit().putBoolean("mapbox_token_valid", false).apply()
                            activity.updateApiStatus(false, "Mapbox-Token unvollständig")
                            return@Button
                        }

                        val saved = prefs.edit()
                            .putString("mapbox_token", clean)
                            .putString("tankerkoenig_key", tankerKey.trim())
                            .putFloat("start_litres", litres.replace(',', '.').toFloatOrNull() ?: 60f)
                            .putFloat("consumption", consumption.replace(',', '.').toFloatOrNull() ?: 7.4f)
                            .putBoolean("voice_alerts", voice)
                            .putBoolean("mapbox_token_valid", false)
                            .commit()

                        if (!saved) {
                            tokenStatus = "Speichern fehlgeschlagen"
                            tokenLight = Light.RED
                            return@Button
                        }

                        checking = true
                        tokenStatus = "Mapbox wird direkt geprüft …"
                        tokenLight = Light.YELLOW
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    MapboxClient.route(clean, TripConfig.hotel, TripConfig.canet)
                                }
                            }
                            checking = false
                            result.onSuccess {
                                prefs.edit()
                                    .putBoolean("mapbox_token_valid", true)
                                    .putLong("mapbox_token_checked_at", System.currentTimeMillis())
                                    .apply()
                                tokenStatus = "Verbunden · Live-Verkehr funktioniert"
                                tokenLight = Light.GREEN
                                diagnostics.edit().remove("last_error").apply()
                                activity.updateApiStatus(true, "Token geprüft · Live-Verkehr bereit")
                                activity.reloadTripConfig()
                            }.onFailure { error ->
                                val message = error.message?.take(150) ?: "Mapbox-Test fehlgeschlagen"
                                prefs.edit().putBoolean("mapbox_token_valid", false).apply()
                                diagnostics.edit().putString(
                                    "last_error",
                                    "${LocalDateTime.now().withNano(0)} · Mapbox-Test: $message"
                                ).apply()
                                tokenStatus = message
                                tokenLight = Light.RED
                                activity.updateApiStatus(false, message)
                            }
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (checking) "Prüfung läuft …" else "Speichern und Verbindung prüfen")
                }
            }
        }

        item {
            AppCard {
                Text("Live-Daten komplett prüfen", style = MaterialTheme.typography.titleLarge)
                if (liveSources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    liveSources.forEach { source ->
                        StatusLine(source.name, source.detail, source.light)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        checkingLiveSources = true
                        scope.launch {
                            liveSources = LiveDataDiagnostics.check(activity.applicationContext)
                            checkingLiveSources = false
                        }
                    },
                    enabled = !checkingLiveSources,
                    modifier = Modifier.fillMaxWidth().testTag("check-live-sources"),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (checkingLiveSources) "Live-Prüfung läuft …" else "Alle Live-Quellen jetzt prüfen")
                }
            }
        }

        item {
            AppCard {
                Text("Fahrzeug und Tankplanung", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = litres,
                        onValueChange = { litres = it },
                        label = { Text("Starttank · Liter") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = consumption,
                        onValueChange = { consumption = it },
                        label = { Text("l/100 km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tankerKey,
                    onValueChange = { tankerKey = it.trim() },
                    label = { Text("Tankerkönig-Key · optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                StatusLine("Frankreich", "offizielle Preisquelle eingerichtet · Live-Test oben", Light.GREY)
                StatusLine("Spanien", "offizielle Preisquelle eingerichtet · Live-Test oben", Light.GREY)
                StatusLine(
                    "Deutschland",
                    if (tankerKey.trim().length >= 30) "Tankerkönig-Key vorhanden" else "ohne Key nur geplante Fallback-Stopps",
                    if (tankerKey.trim().length >= 30) Light.YELLOW else Light.GREY
                )
                StatusLine("Andorra", "sinnvolle Stationen, Preis vor Ort vergleichen", Light.GREY)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sprachwarnungen", fontWeight = FontWeight.Bold)
                        Text("Maut, Pause und Tanken", color = Muted)
                    }
                    Switch(checked = voice, onCheckedChange = {
                        voice = it
                        prefs.edit().putBoolean("voice_alerts", it).apply()
                    })
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        prefs.edit()
                            .putString("tankerkoenig_key", tankerKey.trim())
                            .putFloat("start_litres", litres.replace(',', '.').toFloatOrNull() ?: 60f)
                            .putFloat("consumption", consumption.replace(',', '.').toFloatOrNull() ?: 7.4f)
                            .putBoolean("voice_alerts", voice)
                            .apply()
                        activity.reloadTripConfig()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Fahrzeug- und Tankdaten speichern") }
            }
        }

        item {
            AppCard {
                Text("Berechtigungen und Hintergrund", style = MaterialTheme.typography.titleLarge)
                StatusLine(
                    "Tracking",
                    if (snapshot.active) "aktiv" else "nicht gestartet",
                    if (snapshot.active) Light.GREEN else Light.GREY
                )
                val apiText = when {
                    snapshot.apiOk -> snapshot.apiMessage
                    prefs.getBoolean("mapbox_token_valid", false) -> "Token geprüft · Tracking starten"
                    else -> snapshot.apiMessage
                }
                StatusLine(
                    "Live-API",
                    apiText,
                    if (snapshot.apiOk || prefs.getBoolean("mapbox_token_valid", false)) Light.GREEN else Light.YELLOW
                )
                StatusLine(
                    "App-Erkennung",
                    if (listener) "freigegeben" else "optional · Zugriff fehlt",
                    if (listener) Light.GREEN else Light.GREY
                )
                val lastError = diagnostics.getString("last_error", "").orEmpty()
                if (lastError.isNotBlank()) {
                    StatusLine("Letzter Fehler", lastError, Light.YELLOW)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Benachrichtigungszugriff öffnen") }
                Spacer(Modifier.height(7.dp))
                OutlinedButton(
                    onClick = {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Android-Berechtigungen öffnen") }
            }
        }

        item {
            AppCard {
                Text("Reise-Apps", style = MaterialTheme.typography.titleLarge)
                StatusLine(
                    "Google Maps",
                    when {
                        !mapsInstalled -> "nicht installiert"
                        mapsActive -> "Navigation aktiv erkannt"
                        else -> "installiert · derzeit keine Navigation"
                    },
                    when {
                        !mapsInstalled -> Light.RED
                        mapsActive -> Light.GREEN
                        else -> Light.GREY
                    }
                )
                StatusLine(
                    "Bison Futé",
                    if (bisonInstalled) "installiert · französische Verkehrslage" else "Webansicht verfügbar",
                    if (bisonInstalled) Light.GREEN else Light.GREY
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = { activity.openPackage("com.google.android.apps.maps") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Google Maps öffnen") }
                    OutlinedButton(
                        onClick = { activity.openBisonFute() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Bison Futé öffnen") }
                }
            }
        }

        if (hasTestData) {
            item {
                AppCard {
                    Text("Testfahrt", style = MaterialTheme.typography.titleLarge)
                    StatusLine(
                        "Gespeicherter Teststand",
                        "${"%.1f".format(snapshot.distanceTravelledKm)} km · ${minuteText(snapshot.driveMinutes)} Std.",
                        Light.YELLOW
                    )
                    OutlinedButton(
                        onClick = { confirmTestReset = true },
                        modifier = Modifier.fillMaxWidth().testTag("reset-test-trip"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Testfahrt löschen") }
                }
            }
        }

        item {
            Text(
                "ReisePilot ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confirmTestReset) {
        AlertDialog(
            onDismissRequest = { confirmTestReset = false },
            title = { Text("Testfahrt löschen?") },
            text = {
                Text(
                    "Strecke, Fahrzeit, Route und Tankberechnung der Testfahrt werden gelöscht. " +
                        "Packliste, Ziele, Einstellungen und Parkplätze bleiben erhalten."
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmTestReset = false }) { Text("Abbrechen") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmTestReset = false
                        activity.serviceAction(TripTrackingService.ACTION_RESET)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    modifier = Modifier.testTag("confirm-reset-test-trip")
                ) { Text("Testdaten löschen") }
            }
        )
    }
}
