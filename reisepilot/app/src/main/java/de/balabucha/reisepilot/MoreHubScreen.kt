package de.balabucha.reisepilot

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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

private enum class MoreHubPage { HOME, PACKING, TECHNICAL }

@Composable
fun MoreHubScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var page by rememberSaveable { mutableStateOf(MoreHubPage.HOME) }
    if (page == MoreHubPage.PACKING) {
        PackingListScreen(
            activity = activity,
            modifier = modifier,
            onBack = { page = MoreHubPage.HOME }
        )
        return
    }

    if (page == MoreHubPage.TECHNICAL) {
        BackHandler { page = MoreHubPage.HOME }
        MoreScreen(
            activity = activity,
            snapshot = snapshot,
            modifier = modifier,
            onBack = { page = MoreHubPage.HOME }
        )
        return
    }

    Page("Mehr", "", modifier) {
        item { JourneyOverview(activity, snapshot.stage) }
        item { BookingOverview(activity) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).background(Blue.copy(alpha = .11f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Blue, fontWeight = FontWeight.Black, fontSize = 25.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Packliste", style = MaterialTheme.typography.titleLarge)
                    }
                }
                val packing = remember { PackingRepository(activity.applicationContext) }.state
                val progress = PackingLogic.progress(packing.items)
                Spacer(Modifier.height(11.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = Green,
                    trackColor = Line
                )
                Text(
                    "${progress.done} von ${progress.total} eingepackt · ${progress.percent} %",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { page = MoreHubPage.PACKING },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("open-packing-list"),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Packliste öffnen") }
            }
        }
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
                    onClick = { page = MoreHubPage.TECHNICAL },
                    modifier = Modifier.fillMaxWidth().testTag("technical-settings-button"),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Einstellungen öffnen") }
            }
        }

        item {
            AppCard {
                Text("Tankstellen & Preise", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                StatusLine("Frankreich", "offizielle Dieselpreise ohne API-Key", Light.GREEN)
                StatusLine("Spanien", "offizielle Dieselpreise ohne API-Key", Light.GREEN)
                StatusLine("Deutschland", "Live-Preise mit Tankerkönig-Key unter Einstellungen", Light.YELLOW)
                snapshot.fuelSuggestion?.let { fuel ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(fuel.name, fontWeight = FontWeight.Bold)
                    Text(buildString {
                        fuel.pricePerLitre?.let { append("${"%.3f".format(it)} €/l · ") }
                        append("${"%.1f".format(fuel.distanceAheadKm)} km voraus · ca. ${"%.1f".format(fuel.detourKm)} km Umweg")
                    }, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { activity.openPointRoute(fuel.point, fuel.name) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Route zur Tankstelle") }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { activity.serviceAction(TripTrackingService.ACTION_RELOAD_CONFIG) }, enabled = snapshot.active, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Text(if (snapshot.active) "Tankstellen neu suchen" else "Suche nach Fahrtstart verfügbar")
                }
            }
        }
        item {
            AppCard {
                Text("Reise-Apps", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
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
        Text("Abfahrt 09:00 · Pausen automatisch · Hotel am Abend", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("Sonntag · Montbéliard → Canet", fontWeight = FontWeight.Bold)
        Text("Frühstück 07:00 · Abfahrt 07:35–07:45 · Canet 17:00–19:00", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = { activity.openMaps(Stage.SATURDAY) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Route Samstag") }
            OutlinedButton(
                onClick = { activity.openMaps(Stage.SUNDAY) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Route Sonntag") }
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
            TextButton(onClick = map) { Text("Google Maps") }
        }
    }
}
