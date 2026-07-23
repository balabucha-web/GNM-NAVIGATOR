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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun VisualLiveScreen52(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    if (assistantOpen) {
        BackHandler { assistantOpen = false }
        AssistantScreen52(activity, snapshot, modifier) { assistantOpen = false }
        return
    }

    Box(modifier.fillMaxSize()) {
        VisualLiveScreen51(activity, snapshot, Modifier.fillMaxSize())
        ExtendedFloatingActionButton(
            onClick = { assistantOpen = true },
            icon = {
                Surface(color = Color.White.copy(alpha = .18f), shape = RoundedCornerShape(8.dp)) {
                    Text("AI", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                }
            },
            text = { Text("Reise-Assistent", fontWeight = FontWeight.Bold) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).testTag("open-reise-assistant"),
            containerColor = Navy,
            contentColor = Color.White
        )
    }
}

@Composable
private fun AssistantScreen52(
    activity: MainActivity,
    snapshot: TripSnapshot,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val context = activity.applicationContext
    val prefs = remember { context.getSharedPreferences("assistant_settings_v1", Context.MODE_PRIVATE) }
    var backendUrl by rememberSaveable { mutableStateOf(prefs.getString("backend_url", "").orEmpty()) }
    var accessToken by rememberSaveable { mutableStateOf(prefs.getString("access_token", "").orEmpty()) }
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIntent by rememberSaveable { mutableStateOf(AssistantIntent52.WHAT_TODAY) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf<AssistantAnswer52?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var healthText by remember { mutableStateOf("") }
    var pendingPacking by remember { mutableStateOf<List<String>>(emptyList()) }
    var packingMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val configured = AssistantClient52.isConfigured(backendUrl)

    fun runAssistant() {
        if (loading) return
        loading = true
        error = ""
        packingMessage = ""
        scope.launch {
            val request = AssistantContext52.build(context, snapshot, selectedIntent, question)
            answer = if (configured) {
                runCatching { AssistantClient52.ask(backendUrl, accessToken, request) }
                    .getOrElse { failure ->
                        error = "OpenAI-Backend nicht erreichbar: ${failure.message.orEmpty().take(140)} · lokale Auswertung verwendet."
                        AssistantOffline52.answer(context, snapshot, selectedIntent, question)
                    }
            } else {
                AssistantOffline52.answer(context, snapshot, selectedIntent, question)
            }
            loading = false
        }
    }

    Page("Reise-Assistent", "Konkrete Vorschläge aus deinen Reisedaten", modifier) {
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Zurück zum Reise-Cockpit")
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (configured) Green.copy(alpha = .10f) else SoftBlue),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).background(if (configured) Green else Blue, RoundedCornerShape(99.dp)))
                        Spacer(Modifier.width(9.dp))
                        Text(if (configured) "OPENAI-BACKEND BEREIT" else "LOKALER MODUS", fontWeight = FontWeight.Black, color = if (configured) Green else Blue, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (configured) "Anfragen laufen über deinen eigenen Proxy. Der OpenAI-Schlüssel ist nicht in der APK gespeichert."
                        else "Alle Funktionen sind sofort testbar. Für echte OpenAI-Auswertung später eine HTTPS-Backend-Adresse eintragen.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        item {
            AppCard {
                SectionTitle("Schnellaktionen")
                Spacer(Modifier.height(10.dp))
                AssistantIntent52.entries.filter { it != AssistantIntent52.CUSTOM }.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { intent ->
                            FilterChip(
                                selected = selectedIntent == intent,
                                onClick = { selectedIntent = intent },
                                label = { Text(intent.label, maxLines = 2) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(5.dp))
                }
                Text(selectedIntent.hint, color = Muted, fontSize = 12.sp)
            }
        }
        item {
            AppCard {
                SectionTitle("Deine Frage")
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = {
                        question = it.take(700)
                        if (it.isNotBlank()) selectedIntent = AssistantIntent52.CUSTOM
                    },
                    placeholder = { Text("Zum Beispiel: Etwas für drei Stunden mit Kindern und wenig Parkplatzstress") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("assistant-question"),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = ::runAssistant,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("assistant-run"),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(if (loading) "Auswertung läuft …" else "Vorschläge erstellen")
                }
            }
        }
        if (error.isNotBlank()) item { WarningCard("Fallback aktiv", error, Light.YELLOW) }
        if (packingMessage.isNotBlank()) item { WarningCard("Packliste", packingMessage, Light.GREEN) }
        answer?.let { result ->
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(result.headline, style = MaterialTheme.typography.titleLarge)
                            Text(result.summary, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        Surface(
                            color = if (result.source == AssistantSource52.OPENAI) Green.copy(alpha = .12f) else SoftBlue,
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Text(
                                if (result.source == AssistantSource52.OPENAI) "OpenAI" else "Lokal",
                                color = if (result.source == AssistantSource52.OPENAI) Green else Blue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            items(result.suggestions.size) { index ->
                val suggestion = result.suggestions[index]
                AssistantSuggestionCard52(activity, suggestion) {
                    pendingPacking = suggestion.packingItems
                }
            }
            result.warnings.forEach { warning -> item { WarningCard("Bitte beachten", warning, Light.YELLOW) } }
        }
        item {
            AppCard {
                TextButton(onClick = { setupExpanded = !setupExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (setupExpanded) "Backend-Einrichtung ausblenden" else "OpenAI-Backend einrichten")
                }
                if (setupExpanded) {
                    Spacer(Modifier.height(7.dp))
                    Text("Hier gehört nur die Adresse deines eigenen Proxys hinein – niemals ein OpenAI-API-Schlüssel.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it.trim() },
                        label = { Text("HTTPS-Backend-Adresse") },
                        placeholder = { Text("https://reise-assistent.example.de") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it.trim() },
                        label = { Text("Proxy-Zugangscode · optional") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                prefs.edit().putString("backend_url", backendUrl).putString("access_token", accessToken).apply()
                                healthText = "Gespeichert"
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) { Text("Speichern") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    healthText = if (!AssistantClient52.isConfigured(backendUrl)) {
                                        "Gültige HTTPS-Adresse erforderlich"
                                    } else {
                                        runCatching { AssistantClient52.health(backendUrl, accessToken) }
                                            .fold({ "Verbunden · $it" }, { "Test fehlgeschlagen: ${it.message.orEmpty().take(90)}" })
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) { Text("Testen") }
                    }
                    if (healthText.isNotBlank()) Text(healthText, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }
    }

    if (pendingPacking.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingPacking = emptyList() },
            title = { Text("Zur Packliste hinzufügen?") },
            text = { Text(pendingPacking.joinToString("\n") { "• $it" }) },
            confirmButton = {
                Button(onClick = {
                    val added = addPackingSuggestions52(context, pendingPacking)
                    packingMessage = "$added neue Einträge wurden hinzugefügt."
                    pendingPacking = emptyList()
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { pendingPacking = emptyList() }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun AssistantSuggestionCard52(
    activity: MainActivity,
    suggestion: AssistantSuggestion52,
    onPacking: () -> Unit
) {
    AppCard {
        Text(suggestion.title, style = MaterialTheme.typography.titleLarge)
        if (suggestion.detail.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(suggestion.detail, color = Navy, lineHeight = 20.sp)
        }
        if (suggestion.reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(suggestion.reason, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        val destination = DestinationCatalog.places.firstOrNull { it.title.equals(suggestion.destinationTitle, ignoreCase = true) }
        if (destination != null || suggestion.packingItems.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                destination?.let { place ->
                    Button(
                        onClick = { activity.openPointRoute(place.point, place.title) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Route öffnen") }
                }
                if (suggestion.packingItems.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onPacking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Packliste ergänzen") }
                }
            }
        }
    }
}

private fun addPackingSuggestions52(context: Context, suggestions: List<String>): Int {
    val repository = PackingRepository(context)
    val existing = repository.state.items.map { it.name.lowercase() }.toMutableSet()
    val categoryId = repository.state.categories.firstOrNull { it.name.equals("KI-Vorschläge", ignoreCase = true) }?.id
        ?: repository.addCategory("KI-Vorschläge", "Vom Reise-Assistenten vorgeschlagen und von dir bestätigt")
    var added = 0
    suggestions.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase).forEach { name ->
        if (existing.none { it == name.lowercase() }) {
            repository.addItem(name, "1", categoryId, null, "Vom Reise-Assistenten bestätigt")
            existing += name.lowercase()
            added++
        }
    }
    return added
}
