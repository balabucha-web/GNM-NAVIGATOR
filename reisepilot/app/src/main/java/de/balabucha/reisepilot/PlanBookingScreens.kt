package de.balabucha.reisepilot

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(activity: MainActivity, selected: Stage, modifier: Modifier) {
    var stage by rememberSaveable { mutableStateOf(selected) }
    Page("Reiseplan", "Entspannt, aber mit klaren Reserven", modifier) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Stage.entries.forEachIndexed { index, value ->
                    SegmentedButton(selected = stage == value, onClick = { stage = value }, shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size), label = { Text(if (value == Stage.SATURDAY) "Samstag" else "Sonntag") })
                }
            }
        }
        if (stage == Stage.SATURDAY) {
            item { Timeline("07:30–08:00", "Abfahrt Schwerin", "Standard 07:45 · Freitagabend Hoyer volltanken", Light.GREEN) }
            item { Timeline("10:15", "Erste Pause", "15–20 Minuten", Light.GREEN) }
            item { Timeline("ca. 13:00", "Pause + Tanken", "BayWa Schwabach nur bei passender Live-Route", Light.YELLOW) }
            item { Timeline("ca. 16:30", "Zweite Pause", "20–25 Minuten", Light.GREEN) }
            item { Timeline("21:00–23:00", "greet Hôtel", "Bei ETA nach 21:00 vorsorglich anrufen", Light.YELLOW) }
        } else {
            item { Timeline("07:00", "Frühstück", "Bis etwa 07:25", Light.GREEN) }
            item { Timeline("07:35–07:45", "Abfahrt", "Live-ETA automatisch prüfen", Light.GREEN) }
            item { Timeline("ca. 10:20", "Pause um Lyon", "Je nach Verkehr davor oder danach", Light.YELLOW) }
            item { Timeline("ca. 12:45", "Tanken + Mittag", "Intermarché Orange · keine Autobahntankstelle", Light.YELLOW) }
            item { Timeline("17:00–19:00", "Malibu Village", "Nach Anruf ist Check-in bis 23:00 möglich", Light.GREEN) }
        }
        item { Button(onClick = { activity.openMaps(stage) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Route in Google Maps öffnen") } }
    }
}

@Composable
private fun Timeline(time: String, title: String, detail: String, light: Light) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.width(96.dp)) { Lamp(light, 14.dp); Spacer(Modifier.height(7.dp)); Text(time, color = statusColor(light), fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted) }
        }
    }
}

@Composable
fun BookingScreen(activity: MainActivity, modifier: Modifier) {
    Page("Buchungen", "Nur die wichtigen Daten", modifier) {
        item {
            BookingCard("greet Hôtel Montbéliard", "25.–26.07.2026", listOf(
                "Tribu-Zimmer für 4", "110,70 € + 2,20 € Steuer", "Check-in ab 15:00 · Check-out bis 11:00", "Frühstück Sonntag ab 07:00"
            ), { activity.dial("+33381901069") }, {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=greet+H%C3%B4tel+Montb%C3%A9liard")))
            })
        }
        item {
            BookingCard("Malibu Village", "26.07.–03.08.2026", listOf(
                "1.320,12 €", "Check-in 16:00–19:00", "Nach vorherigem Anruf bis 23:00", "100 € Kaution per Kreditkarte", "Bettwäsche/Handtücher nicht inklusive"
            ), { activity.dial("+33468732779") }, {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=Malibu+Village+Canet-en-Roussillon")))
            })
        }
    }
}

@Composable
private fun BookingCard(title: String, date: String, rows: List<String>, call: () -> Unit, map: () -> Unit) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(date, color = Blue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        rows.forEach { Text("• $it", modifier = Modifier.padding(vertical = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = call, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Anrufen") }
            OutlinedButton(onClick = map, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Karte") }
        }
    }
}
