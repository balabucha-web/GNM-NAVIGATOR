package de.balabucha.reisepilot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CompactDiagnostics51(
    activity: MainActivity,
    modifier: Modifier,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var statuses by remember { mutableStateOf<List<LiveSourceStatus>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var showWorking by remember { mutableStateOf(false) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val total = LiveDataDiagnostics.SOURCE_COUNT

    fun refresh() {
        checkJob?.cancel()
        statuses = emptyList()
        checking = true
        checkJob = scope.launch {
            statuses = LiveDataDiagnostics.check(activity.applicationContext) { partial ->
                statuses = partial
            }
            checking = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(Unit) { onDispose { checkJob?.cancel() } }

    val working = statuses.filter { it.light == Light.GREEN }
    val issues = statuses.filter { it.light != Light.GREEN }
    val completed = statuses.size
    val remaining = (total - completed).coerceAtLeast(0)

    Page("Live-Datenstatus", "Nur echte Probleme werden hervorgehoben", modifier) {
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
                    if (checking) "$completed von $total Prüfungen abgeschlossen" else "${working.size} von $total Quellen verfügbar",
                    if (checking) "$remaining offen" else "Prüfung beendet"
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { completed.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (checking) {
                        "Die Quellen werden parallel geprüft. Jede Quelle endet mit Ergebnis oder Zeitüberschreitung."
                    } else {
                        when {
                            issues.isEmpty() -> "Alle geprüften Live-Datenquellen arbeiten normal."
                            issues.size == 1 -> "Eine Quelle arbeitet eingeschränkt oder mit Fallback."
                            else -> "${issues.size} Quellen arbeiten eingeschränkt oder mit Fallback."
                        }
                    },
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        if (issues.isNotEmpty()) {
            item {
                AppCard {
                    SectionTitle(if (checking) "Bereits erkannte Hinweise" else "Hinweise und Einschränkungen")
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

        if (working.isNotEmpty()) {
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Funktionierende Quellen", fontWeight = FontWeight.Bold)
                            Text("${working.size} Dienste haben bereits erfolgreich geantwortet", color = Muted, fontSize = 12.sp)
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
                onClick = {
                    if (checking) checkJob?.cancel()
                    refresh()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (checking) "Prüfung neu starten" else "Live-Daten erneut prüfen")
            }
        }
    }
}
