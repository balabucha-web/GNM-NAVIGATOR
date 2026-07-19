package de.balabucha.reisepilot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun LiveScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val context = LocalContext.current
    var chosen by rememberSaveable(snapshot.stage) { mutableStateOf(snapshot.stage) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locationGranted) activity.startTrip(chosen)
    }
    Page("ReisePilot", "Nach dem Start läuft die Prüfung im Hintergrund", modifier) {
        item { ClockCard(snapshot) }
        item {
            AppCard {
                Text(if (chosen == Stage.SATURDAY) "SAMSTAG · HINREISE" else "SONNTAG · WEITERREISE", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (snapshot.active) if (snapshot.paused) "Pause aktiv" else "Tracking aktiv" else "Bereit", style = MaterialTheme.typography.headlineLarge)
                Text(if (chosen == Stage.SATURDAY) "Abfahrt 07:30–08:00 · Standard 07:45" else "Frühstück 07:00 · Abfahrt 07:35–07:45", color = Muted)
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Stage.entries.forEachIndexed { index, stage ->
                        SegmentedButton(selected = chosen == stage, onClick = { if (!snapshot.active) chosen = stage }, shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size), label = { Text(if (stage == Stage.SATURDAY) "Samstag" else "Sonntag") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray()
                        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (locationGranted) activity.startTrip(chosen) else permissionLauncher.launch(permissions)
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(13.dp)) { Text(if (snapshot.active) "Neu starten" else "Fahrt starten") }
                    OutlinedButton(onClick = { activity.openMaps(chosen) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Google Maps") }
                }
                if (snapshot.active) TextButton(onClick = { activity.serviceAction(TripTrackingService.ACTION_STOP) }, modifier = Modifier.fillMaxWidth()) { Text("Tracking stoppen", color = Red) }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("ETA", snapshot.etaEpochMs?.let(::formatTime) ?: "–", snapshot.scheduleLight, Modifier.weight(1f))
            Metric("Rest", snapshot.remainingKm?.let { "$it km" } ?: "–", Light.GREY, Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Fahrzeit", minuteText(snapshot.driveMinutes), snapshot.pauseLight, Modifier.weight(1f))
            Metric("Tank", "${"%.1f".format(snapshot.fuelLitres)} L", snapshot.fuelLight, Modifier.weight(1f))
        } }
        item { NextAction(activity, snapshot) }
        item { AutomaticStatus(snapshot) }
        if (!snapshot.apiOk) item { WarningCard("Live-Verkehr noch aus", "${snapshot.apiMessage}. Unter Mehr den öffentlichen Mapbox-Token eintragen.", Light.YELLOW) }
    }
}

@Composable
private fun ClockCard(snapshot: TripSnapshot) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
    val overall = when {
        !snapshot.active -> Light.GREY
        snapshot.scheduleLight == Light.RED || snapshot.pauseLight == Light.RED || snapshot.fuelLight == Light.RED -> Light.RED
        snapshot.scheduleLight == Light.YELLOW || snapshot.pauseLight == Light.YELLOW || snapshot.fuelLight == Light.YELLOW -> Light.YELLOW
        else -> Light.GREEN
    }
    Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)), color = Color(0xFFCFD8E4))
            }
            Lamp(overall, 48.dp)
        }
    }
}

@Composable
private fun NextAction(activity: MainActivity, s: TripSnapshot) {
    AppCard {
        Text("NÄCHSTER SCHRITT", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp)); Text(s.nextTitle, style = MaterialTheme.typography.titleLarge); Text(s.nextDetail, color = Muted)
        s.nextDistanceM?.let { Spacer(Modifier.height(7.dp)); Text(distanceText(it), color = Blue, fontSize = 26.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(12.dp))
        val action = when { s.nextTitle.contains("anrufen", true) -> "Anrufen"; s.nextTitle.contains("Pause", true) -> "Pause erledigt"; s.nextTitle.contains("Tank", true) -> "Vollgetankt"; else -> "Navigation öffnen" }
        Button(onClick = {
            when (action) {
                "Anrufen" -> activity.dial(if (s.stage == Stage.SUNDAY) "+33468732779" else "+33381901069")
                "Pause erledigt" -> activity.serviceAction(TripTrackingService.ACTION_BREAK_DONE)
                "Vollgetankt" -> activity.serviceAction(TripTrackingService.ACTION_REFUEL_FULL)
                else -> activity.openMaps(s.stage)
            }
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text(action) }
    }
}

@Composable
private fun AutomaticStatus(s: TripSnapshot) {
    AppCard {
        Text("Automatische Prüfung", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
        StatusLine("Ankunft", lightText(s.scheduleLight), s.scheduleLight)
        val trafficLight = when { s.trafficDelayMin == null -> Light.GREY; s.trafficDelayMin < 15 -> Light.GREEN; s.trafficDelayMin < 45 -> Light.YELLOW; else -> Light.RED }
        StatusLine("Verkehr", s.trafficDelayMin?.let { if (it == 0) "normal" else "+$it Min." } ?: "unbekannt", trafficLight)
        StatusLine("Pause", if (s.driveMinutes < 135) "im Rahmen" else minuteText(s.driveMinutes), s.pauseLight)
        StatusLine("Tank", "${"%.1f".format(s.fuelLitres)} Liter geschätzt", s.fuelLight)
        StatusLine("API", if (s.apiOk) "Live-Verkehr aktiv" else s.apiMessage, if (s.apiOk) Light.GREEN else Light.YELLOW)
    }
}
