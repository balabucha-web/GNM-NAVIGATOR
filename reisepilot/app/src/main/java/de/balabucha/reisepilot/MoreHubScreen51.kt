package de.balabucha.reisepilot

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

private enum class MoreHubPage51 { HOME, TECHNICAL, DIAGNOSTICS }

@Composable
fun MoreHubScreen51(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var page by rememberSaveable { mutableStateOf(MoreHubPage51.HOME) }

    if (page == MoreHubPage51.TECHNICAL) {
        BackHandler { page = MoreHubPage51.HOME }
        MoreScreen(
            activity = activity,
            snapshot = snapshot,
            modifier = modifier,
            onBack = { page = MoreHubPage51.HOME }
        )
        return
    }

    if (page == MoreHubPage51.DIAGNOSTICS) {
        BackHandler { page = MoreHubPage51.HOME }
        CompactDiagnostics51(
            activity = activity,
            modifier = modifier,
            onBack = { page = MoreHubPage51.HOME },
            onOpenSettings = { page = MoreHubPage51.TECHNICAL }
        )
        return
    }

    Page("Mehr", "Reise, unterwegs und Technik", modifier) {
        item { SectionLabel51("Reise") }
        item { JourneyOverview51(activity) }
        item { BookingOverview51(activity) }
        item { PackingOverview51(activity) }

        item { SectionLabel51("Unterwegs") }
        item { FuelAndApps51(activity, snapshot) }

        item { SectionLabel51("Einstellungen und Technik") }
        item {
            AppCard {
                SectionTitle("System und Fahrzeug")
                Spacer(Modifier.height(5.dp))
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
                    onClick = { page = MoreHubPage51.DIAGNOSTICS },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Live-Daten kompakt prüfen") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { page = MoreHubPage51.TECHNICAL },
                    modifier = Modifier.fillMaxWidth().testTag("technical-settings-button"),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Technische Einstellungen") }
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
private fun SectionLabel51(text: String) {
    Text(text.uppercase(), color = Blue, fontWeight = FontWeight.Black, fontSize = 12.sp)
}

@Composable
private fun JourneyOverview51(activity: MainActivity) {
    AppCard {
        SectionTitle("Reiseplan", "4 Etappen")
        Spacer(Modifier.height(9.dp))
        JourneyLine51("Sa., 25.07.", "Schwerin → Montbéliard", "Abfahrt 09:00 · Pausen automatisch · Hotel am Abend")
        JourneyDivider51()
        JourneyLine51("So., 26.07.", "Montbéliard → Canet", "Frühstück 07:00 · Abfahrt 07:35–07:45 · Ankunft 17:00–19:00")
        JourneyDivider51()
        JourneyLine51("Mo., 03.08.", "Canet → Paris", "Rückreise mit Paris-Zwischenaufenthalt")
        JourneyDivider51()
        JourneyLine51("Do., 06.08.", "Paris → Schwerin", "Letzte Etappe nach Hause")
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { activity.openMaps(Stage.SATURDAY) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Hinweg 1") }
            OutlinedButton(
                onClick = { activity.openMaps(Stage.SUNDAY) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Hinweg 2") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { activity.openJourneyRoute51("Malibu Village Canet-en-Roussillon", "Paris") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Canet → Paris") }
            OutlinedButton(
                onClick = { activity.openJourneyRoute51("Paris", "19057 Schwerin") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Paris → Zuhause") }
        }
    }
}

@Composable
private fun JourneyLine51(date: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(color = SoftBlue, shape = RoundedCornerShape(10.dp)) {
            Text(
                date,
                color = Blue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun JourneyDivider51() {
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Line)
}

@Composable
private fun BookingOverview51(activity: MainActivity) {
    AppCard {
        SectionTitle("Buchungen")
        Spacer(Modifier.height(10.dp))
        BookingLine51(
            title = "greet Hôtel Montbéliard",
            detail = "25.–26.07. · Check-in ab 15:00 · 112,90 € gesamt",
            call = { activity.dial("+33381901069") },
            map = { activity.openMapSearch("greet Hôtel Montbéliard") }
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
        BookingLine51(
            title = "Malibu Village",
            detail = "26.07.–03.08. · Check-in 16:00–19:00 · nach Anruf bis 23:00",
            call = { activity.dial("+33468732779") },
            map = { activity.openMapSearch("Malibu Village Canet-en-Roussillon") }
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
        BookingLine51(
            title = "Paris-Aufenthalt",
            detail = "03.–06.08. · Hoteladresse und Parkplatz in den technischen Reisedaten ergänzen",
            call = null,
            map = { activity.openMapSearch("Paris") }
        )
    }
}

@Composable
private fun BookingLine51(
    title: String,
    detail: String,
    call: (() -> Unit)?,
    map: () -> Unit
) {
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            call?.let { action -> TextButton(onClick = action) { Text("Anrufen") } }
            TextButton(onClick = map) { Text("Google Maps") }
        }
    }
}

@Composable
private fun PackingOverview51(activity: MainActivity) {
    val repository = remember { PackingRepository(activity.applicationContext) }
    val progress = PackingLogic.progress(repository.state.items)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(45.dp).background(Blue.copy(alpha = .11f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = Blue, fontWeight = FontWeight.Black, fontSize = 23.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Packliste", style = MaterialTheme.typography.titleLarge)
                Text("${progress.done} von ${progress.total} eingepackt", color = Muted, fontSize = 12.sp)
            }
            Text("${progress.percent} %", color = Blue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = Green,
            trackColor = Line
        )
        Text(
            "Die vollständige Packliste ist direkt über die untere Navigation erreichbar.",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun FuelAndApps51(activity: MainActivity, snapshot: TripSnapshot) {
    AppCard {
        SectionTitle("Tanken und Reise-Apps")
        Spacer(Modifier.height(5.dp))
        StatusLine("Frankreich", "offizielle Dieselpreise ohne API-Key", Light.GREEN)
        StatusLine("Spanien", "offizielle Dieselpreise ohne API-Key", Light.GREEN)
        StatusLine("Deutschland", "Live-Preise mit Tankerkönig-Key · sonst geplante Stopps", Light.YELLOW)
        snapshot.fuelSuggestion?.let { fuel ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
            Text(fuel.name, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    fuel.pricePerLitre?.let { append("${"%.3f".format(it)} €/l · ") }
                    append("${"%.1f".format(fuel.distanceAheadKm)} km voraus · ${"%.1f".format(fuel.detourKm)} km Umweg")
                },
                color = Muted,
                fontSize = 12.sp
            )
            TextButton(onClick = { activity.openPointRoute(fuel.point, fuel.name) }) {
                Text("Zur Tankstelle navigieren")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { activity.openPackage("com.google.android.apps.maps") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Google Maps") }
            OutlinedButton(
                onClick = { activity.openBisonFute() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Bison Futé") }
        }
    }
}
