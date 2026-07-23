package de.balabucha.reisepilot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CompactDiagnostics51(
    activity: MainActivity,
    modifier: Modifier,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var statuses by remember { mutableStateOf<List<LiveSourceStatus>>(emptyList()) }
    var checking by remember { mutableStateOf(true) }
    var showWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        checking = true
        scope.launch {
            statuses = LiveDataDiagnostics.check(activity.applicationContext)
            checking = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val working = statuses.filter { it.light == Light.GREEN }
    val issues = statuses.filter { it.light != Light.GREEN }

    Page("Live-Datenstatus", "Nur Probleme werden hervorgehoben", modifier) {
        item {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Zurück zu Mehr") }
        }

        item {
            AppCard {
                SectionTitle(
                    "${working.size} von ${statuses.size.coerceAtLeast(9)} Quellen verfügbar",
                    if (checking) "Prüfung läuft" else "aktuell"
                )
                Spacer(Modifier.height(8.dp))
                if (checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Verkehr, Preise, Bilder und Karte werden geprüft.", color = Muted, fontSize = 13.sp)
                } else {
                    val light = when {
                        issues.any { it.light == Light.RED } -> Light.RED
                        issues.isNotEmpty() -> Light.YELLOW
                        else -> Light.GREEN
                    }
                    StatusLine(
                        "Gesamtstatus",
                        when {
                            issues.isEmpty() -> "Alle geprüften Quellen funktionieren."
                            issues.size == 1 -> "Eine Quelle arbeitet mit Einschränkung oder Fallback."
                            else -> "${issues.size} Quellen arbeiten mit Einschränkung oder Fallback."
                        },
                        light
                    )
                }
            }
        }

        if (!checking && issues.isNotEmpty()) {
            item {
                AppCard {
                    SectionTitle("Hinweise und Einschränkungen")
                    Spacer(Modifier.height(4.dp))
                    issues.forEach { source ->
                        StatusLine(source.name, source.detail, source.light)
                        if (source.name == "Deutschland-Diesel") {
                            TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) {
                                Text("API-Key und Tankdaten einrichten")
                            }
                        }
                    }
                }
            }
        }

        if (!checking && working.isNotEmpty()) {
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Funktionierende Quellen", fontWeight = FontWeight.Bold)
                            Text("${working.size} Dienste antworten normal", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showWorking = !showWorking }) {
                            Text(if (showWorking) "Ausblenden" else "Anzeigen")
                        }
                    }
                    if (showWorking) {
                        Spacer(Modifier.height(6.dp))
                        working.forEach { StatusLine(it.name, it.detail, Light.GREEN) }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { refresh() },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (checking) "Prüfung läuft …" else "Live-Daten erneut prüfen") }
        }
    }
}
