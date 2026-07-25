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
    Page("Reiseplan", "Etappen, Reserven und Buchungen", modifier) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Stage.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = stage == value,
                        onClick = { stage = value },
                        shape = SegmentedButtonDefaults.itemShape(index, Stage.entries.size),
                        label = { Text(if (value == Stage.SATURDAY) "Samstag" else "Sonntag") }
                    )
                }
            }
        }

        if (stage == Stage.SATURDAY) {
            item { Timeline("09:00", "Abfahrt Schwerin", "Freitagabend Hoyer volltanken", Light.GREEN) }
            item { Timeline("nach 2:15 Std.", "Pause vorbereiten", "Die App warnt gelb, ab 2:30 Stunden rot.", Light.GREEN) }
            item { Timeline("automatisch", "Günstig tanken", "Live-Preis plus Umweg; Autobahntankstellen werden ausgeschlossen. BayWa Schwabach bleibt nur Fallback.", Light.YELLOW) }
            item { Timeline("ca. 16:30", "Zweite Pause", "Zeitpunkt wird nach echter Fahrzeit angepasst.", Light.GREEN) }
            item { Timeline("21:00–23:00", "greet Hôtel", "Bei ETA nach 21:00 vorsorglich anrufen.", Light.YELLOW) }
        } else {
            item { Timeline("07:00", "Frühstück", "Bis etwa 07:25", Light.GREEN) }
            item { Timeline("07:35–07:45", "Abfahrt", "Live-ETA automatisch prüfen.", Light.GREEN) }
            item { Timeline("nach 2:15 Std.", "Pause um Lyon", "Die App passt den Hinweis an Verkehr und echte Fahrzeit an.", Light.YELLOW) }
            item { Timeline("automatisch", "Günstig tanken", "Französische Live-Preise, nur Stationen abseits der Autobahn. Orange bleibt Fallback.", Light.YELLOW) }
            item { Timeline("17:00–19:00", "Malibu Village", "Nach Anruf ist Check-in bis 23:00 möglich.", Light.GREEN) }
        }

        item {
            Button(
                onClick = { activity.openMaps(stage) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Route in Google Maps öffnen") }
        }

        item {
            Text("Buchungen", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            BookingCard(
                "greet Hôtel Montbéliard",
                "25.–26.07.2026",
                listOf(
                    "Tribu-Zimmer für 4",
                    "110,70 € + 2,20 € Steuer",
                    "Check-in ab 15:00 · Check-out bis 11:00",
                    "Frühstück Sonntag ab 07:00"
                ),
                { activity.dial("+33381901069") },
                { activity.openMapSearch("greet Hôtel Montbéliard") }
            )
        }
        item {
            BookingCard(
                "Malibu Village",
                "26.07.–03.08.2026",
                listOf(
                    "1.320,12 €",
                    "Check-in 16:00–19:00",
                    "Nach vorherigem Anruf bis 23:00",
                    "100 € Kaution per Kreditkarte",
                    "Bettwäsche/Handtücher nicht inklusive"
                ),
                { activity.dial("+33468732779") },
                { activity.openMapSearch("Malibu Village Canet-en-Roussillon") }
            )
        }
    }
}

@Composable
private fun Timeline(time: String, title: String, detail: String, light: Light) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.width(100.dp)) {
                Lamp(light, 14.dp)
                Spacer(Modifier.height(7.dp))
                Text(time, color = statusColor(light), fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = Muted)
            }
        }
    }
}

@Composable
fun BookingScreen(activity: MainActivity, modifier: Modifier) {
    Page("Buchungen", "Nur die wichtigen Daten", modifier) {
        item {
            BookingCard(
                "greet Hôtel Montbéliard",
                "25.–26.07.2026",
                listOf(
                    "Tribu-Zimmer für 4",
                    "110,70 € + 2,20 € Steuer",
                    "Check-in ab 15:00 · Check-out bis 11:00",
                    "Frühstück Sonntag ab 07:00"
                ),
                { activity.dial("+33381901069") },
                { activity.openMapSearch("greet Hôtel Montbéliard") }
            )
        }
        item {
            BookingCard(
                "Malibu Village",
                "26.07.–03.08.2026",
                listOf(
                    "1.320,12 €",
                    "Check-in 16:00–19:00",
                    "Nach vorherigem Anruf bis 23:00",
                    "100 € Kaution per Kreditkarte",
                    "Bettwäsche/Handtücher nicht inklusive"
                ),
                { activity.dial("+33468732779") },
                { activity.openMapSearch("Malibu Village Canet-en-Roussillon") }
            )
        }
    }
}

@Composable
private fun BookingCard(
    title: String,
    date: String,
    rows: List<String>,
    call: () -> Unit,
    map: () -> Unit
) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(date, color = Blue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        rows.forEach { Text("• $it", modifier = Modifier.padding(vertical = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = call,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Anrufen") }
            OutlinedButton(
                onClick = map,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Karte") }
        }
    }
}
