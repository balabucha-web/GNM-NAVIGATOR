package de.balabucha.reisepilot

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreHubScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var technical by rememberSaveable { mutableStateOf(false) }
    if (technical) {
        BackHandler { technical = false }
        Box(modifier.fillMaxSize()) {
            MoreScreen(activity, snapshot, Modifier.fillMaxSize())
            SmallFloatingActionButton(
                onClick = { technical = false },
                containerColor = Navy,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp)
            ) { Text("‹", fontSize = 26.sp) }
        }
        return
    }

    Page("Mehr", "Reiseplan, Buchungen und Einstellungen", modifier) {
        item { JourneyOverview(activity, snapshot.stage) }
        item { BookingOverview(activity) }
        item {
            AppCard {
                Text("System und Fahrzeug", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                StatusLine(
                    "Tracking",
                    if (snapshot.active) "läuft im Hintergrund" else "nicht gestartet",
                    if (snapshot.active) Light.GREEN else Light.GREY
                )
                StatusLine(
                    "Live-Verkehr",
                    if (snapshot.apiOk) "verbunden" else snapshot.apiMessage,
                    if (snapshot.apiOk) Light.GREEN else Light.YELLOW
                )
                StatusLine(
                    "Tankmodell",
                    "${"%.1f".format(snapshot.fuelLitres)} Liter geschätzt",
                    snapshot.fuelLight
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { technical = true },
                    modifier = Modifier.fillMaxWidth().testTag("technical-settings-button"),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Technische Einstellungen") }
            }
        }
        item {
            AppCard {
                Text("Reise-Apps", style = MaterialTheme.typography.titleLarge)
                Text("Google Maps für Navigation, Bison Futé ergänzend für Frankreich.", color = Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = { activity.openPackage("com.google.android.apps.maps") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Maps") }
                    OutlinedButton(
                        onClick = { activity.openBisonFute() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Bison Futé") }
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
}

@Composable
private fun JourneyOverview(activity: MainActivity, stage: Stage) {
    AppCard {
        Text("Reiseplan", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Samstag · Schwerin → Montbéliard", fontWeight = FontWeight.Bold)
        Text("Abfahrt 07:30–08:00 · Pausen automatisch · Hotel am Abend", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("Sonntag · Montbéliard → Canet", fontWeight = FontWeight.Bold)
        Text("Frühstück 07:00 · Abfahrt 07:35–07:45 · Canet 17:00–19:00", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = { activity.openMaps(Stage.SATURDAY) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Samstag") }
            OutlinedButton(
                onClick = { activity.openMaps(Stage.SUNDAY) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Sonntag") }
        }
    }
}

@Composable
private fun BookingOverview(activity: MainActivity) {
    AppCard {
        Text("Buchungen", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        BookingLine(
            title = "greet Hôtel Montbéliard",
            detail = "25.–26.07. · Check-in ab 15:00 · 112,90 € gesamt",
            call = { activity.dial("+33381901069") },
            map = { activity.openMapSearch("greet Hôtel Montbéliard") }
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        BookingLine(
            title = "Malibu Village",
            detail = "26.07.–03.08. · Check-in 16:00–19:00 · nach Anruf bis 23:00",
            call = { activity.dial("+33468732779") },
            map = { activity.openMapSearch("Malibu Village Canet-en-Roussillon") }
        )
    }
}

@Composable
private fun BookingLine(
    title: String,
    detail: String,
    call: () -> Unit,
    map: () -> Unit
) {
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = call) { Text("Anrufen") }
            TextButton(onClick = map) { Text("Karte") }
        }
    }
}

