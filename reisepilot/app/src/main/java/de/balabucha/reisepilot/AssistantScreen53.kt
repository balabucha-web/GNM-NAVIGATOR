package de.balabucha.reisepilot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VisualLiveScreen53(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    if (assistantOpen) {
        BackHandler { assistantOpen = false }
        AssistantScreen53(activity, snapshot, modifier) { assistantOpen = false }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantScreen53(
    activity: MainActivity,
    snapshot: TripSnapshot,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val context = activity.applicationContext
    val prefs = remember { context.getSharedPreferences("assistant_settings_v1", Context.MODE_PRIVATE) }
    val secretStore = remember { SecureApiKeyStore53(context) }
    var mode by rememberSaveable { mutableStateOf(AssistantMode53.fromStored(prefs.getString("assistant_mode", null))) }
    var backendUrl by rememberSaveable { mutableStateOf(prefs.getString("backend_url", "").orEmpty()) }
    var accessToken by rememberSaveable { mutableStateOf(prefs.getString("access_token", "").orEmpty()) }
    var model by rememberSaveable { mutableStateOf(prefs.getString("direct_model", "gpt-5-mini").orEmpty()) }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(secretStore.hasKey()) }
    var keyHint by remember { mutableStateOf(secretStore.hint()) }
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIntent by rememberSaveable { mutableStateOf(AssistantIntent52.WHAT_TODAY) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf<AssistantAnswer52?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var connectionText by remember { mutableStateOf("") }
    var voiceText by remember { mutableStateOf("") }
    var pendingPacking by remember { mutableStateOf<List<String>>(emptyList()) }
    var packingMessage by remember { mutableStateOf("") }
    var autoSpeak by rememberSaveable { mutableStateOf(prefs.getBoolean("auto_speak", true)) }
    var history by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val proxyConfigured = AssistantClient52.isConfigured(backendUrl)

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageResult = engine?.setLanguage(Locale.GERMANY) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                engine?.setSpeechRate(1.02f)
            }
        }
        tts = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }

    fun speak(result: AssistantAnswer52?) {
        if (!ttsReady || result == null) return
        tts?.speak(result.spokenText53(), TextToSpeech.QUEUE_FLUSH, null, "reisepilot-answer")
    }

    fun runAssistant(questionOverride: String? = null, intentOverride: AssistantIntent52? = null) {
        if (loading) return
        val actualQuestion = questionOverride?.trim().orEmpty().ifBlank { question.trim() }
        val actualIntent = intentOverride ?: selectedIntent
        loading = true
        error = ""
        packingMessage = ""
        connectionText = ""
        scope.launch {
            val request = AssistantContext52.build(context, snapshot, actualIntent, actualQuestion, history)
            val result = when (mode) {
                AssistantMode53.DIRECT -> {
                    val apiKey = secretStore.load()
                    if (apiKey.isBlank()) {
                        error = "Kein API-Key gespeichert · lokale Auswertung verwendet."
                        AssistantOffline52.answer(context, snapshot, actualIntent, actualQuestion)
                    } else {
                        runCatching { DirectOpenAiClient53.ask(apiKey, model, request) }
                            .getOrElse { failure ->
                                error = "Direkte OpenAI-Anfrage fehlgeschlagen: ${failure.message.orEmpty().take(180)} · lokale Auswertung verwendet."
                                AssistantOffline52.answer(context, snapshot, actualIntent, actualQuestion)
                            }
                    }
                }
                AssistantMode53.PROXY -> {
                    if (!proxyConfigured) {
                        error = "Proxy nicht eingerichtet · lokale Auswertung verwendet."
                        AssistantOffline52.answer(context, snapshot, actualIntent, actualQuestion)
                    } else {
                        runCatching { AssistantClient52.ask(backendUrl, accessToken, request) }
                            .getOrElse { failure ->
                                error = "OpenAI-Proxy nicht erreichbar: ${failure.message.orEmpty().take(180)} · lokale Auswertung verwendet."
                                AssistantOffline52.answer(context, snapshot, actualIntent, actualQuestion)
                            }
                    }
                }
                AssistantMode53.LOCAL -> AssistantOffline52.answer(context, snapshot, actualIntent, actualQuestion)
            }
            answer = result
            val userEntry = actualQuestion.ifBlank { actualIntent.label }
            history = (history + (userEntry to "${result.headline}: ${result.summary}")).takeLast(6)
            loading = false
        }
    }

    LaunchedEffect(answer, autoSpeak, ttsReady) {
        if (autoSpeak && ttsReady) speak(answer)
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim()
            if (recognized.isNotBlank()) {
                question = recognized.take(900)
                selectedIntent = AssistantIntent52.CUSTOM
                voiceText = "Erkannt: $recognized"
                runAssistant(recognized, AssistantIntent52.CUSTOM)
            } else {
                voiceText = "Keine Sprache erkannt."
            }
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching { speechLauncher.launch(voiceIntent53()) }
                .onFailure { voiceText = "Spracherkennung ist auf diesem Gerät nicht verfügbar." }
        } else {
            voiceText = "Mikrofonberechtigung wurde nicht erteilt."
        }
    }

    fun startVoice() {
        voiceText = ""
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            runCatching { speechLauncher.launch(voiceIntent53()) }
                .onFailure { voiceText = "Spracherkennung ist auf diesem Gerät nicht verfügbar." }
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val ready = when (mode) {
        AssistantMode53.LOCAL -> true
        AssistantMode53.DIRECT -> keySaved
        AssistantMode53.PROXY -> proxyConfigured
    }
    val modeTitle = when (mode) {
        AssistantMode53.LOCAL -> "LOKALER MODUS"
        AssistantMode53.DIRECT -> if (keySaved) "OPENAI DIREKT BEREIT" else "API-KEY FEHLT"
        AssistantMode53.PROXY -> if (proxyConfigured) "OPENAI-PROXY BEREIT" else "PROXY FEHLT"
    }

    Page("Reise-Assistent", "Voice, Tagesplanung und konkrete Reiseaktionen", modifier) {
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Zurück zum Reise-Cockpit")
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (ready) Green.copy(alpha = .10f) else Yellow.copy(alpha = .10f)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).background(if (ready) Green else Yellow, RoundedCornerShape(99.dp)))
                        Spacer(Modifier.width(9.dp))
                        Text(modeTitle, fontWeight = FontWeight.Black, color = if (ready) Green else Yellow, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(mode.detail, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
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
                SectionTitle("Fragen oder sprechen")
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = {
                        question = it.take(900)
                        if (it.isNotBlank()) selectedIntent = AssistantIntent52.CUSTOM
                    },
                    placeholder = { Text("Zum Beispiel: Collioure oder Oniria – was ist heute entspannter?") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("assistant-question"),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = ::startVoice,
                        enabled = !loading,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("assistant-voice"),
                        shape = RoundedCornerShape(15.dp)
                    ) { Text("Sprechen & fragen") }
                    Button(
                        onClick = { runAssistant() },
                        enabled = !loading,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("assistant-run"),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(if (loading) "Läuft …" else "Auswerten")
                    }
                }
                if (voiceText.isNotBlank()) Text(voiceText, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                if (history.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${history.size} lokale Folgefragen im Kontext", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { history = emptyList() }) { Text("Verlauf löschen") }
                    }
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
                        SourceBadge53(result.source)
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { speak(result) },
                            enabled = ttsReady,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) { Text("Vorlesen") }
                        OutlinedButton(
                            onClick = { tts?.stop() },
                            enabled = ttsReady,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) { Text("Stopp") }
                    }
                }
            }
            items(result.suggestions.size) { index ->
                val suggestion = result.suggestions[index]
                AssistantSuggestionCard53(activity, snapshot, suggestion) {
                    pendingPacking = suggestion.packingItems
                }
            }
            result.warnings.forEach { warning -> item { WarningCard("Bitte beachten", warning, Light.YELLOW) } }
        }
        item {
            AppCard {
                TextButton(onClick = { setupExpanded = !setupExpanded }, modifier = Modifier.fillMaxWidth().testTag("assistant-settings")) {
                    Text(if (setupExpanded) "AI-Einstellungen ausblenden" else "AI-Modus und API-Key einrichten")
                }
                if (setupExpanded) {
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AssistantMode53.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = mode == item,
                                onClick = {
                                    mode = item
                                    prefs.edit().putString("assistant_mode", item.name).apply()
                                    connectionText = ""
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, AssistantMode53.entries.size),
                                label = { Text(item.label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    when (mode) {
                        AssistantMode53.LOCAL -> Text("Der lokale Assistent nutzt ausschließlich die in der App gespeicherten Daten und verursacht keine API-Kosten.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        AssistantMode53.DIRECT -> {
                            WarningCard(
                                "Persönlicher Testmodus",
                                "Der Schlüssel wird mit Android Keystore verschlüsselt gespeichert. Dies reduziert das Risiko, ersetzt aber keinen Proxy auf einem kompromittierten oder gerooteten Gerät.",
                                Light.YELLOW
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Gespeicherter Schlüssel: ${keyHint.ifBlank { "keiner" }}", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(7.dp))
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it.trim().take(300) },
                                label = { Text("OpenAI API-Key") },
                                placeholder = { Text("sk-…") },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { TextButton(onClick = { showApiKey = !showApiKey }) { Text(if (showApiKey) "Verbergen" else "Zeigen") } },
                                modifier = Modifier.fillMaxWidth().testTag("direct-api-key")
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it.trim().take(80) },
                                label = { Text("Modell") },
                                placeholder = { Text("gpt-5-mini") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        runCatching { secretStore.save(apiKeyInput) }
                                            .onSuccess {
                                                keySaved = true
                                                keyHint = secretStore.hint()
                                                apiKeyInput = ""
                                                prefs.edit().putString("direct_model", model.ifBlank { "gpt-5-mini" }).apply()
                                                connectionText = "API-Key verschlüsselt gespeichert"
                                            }
                                            .onFailure { connectionText = it.message.orEmpty() }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(13.dp)
                                ) { Text("Key speichern") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val key = secretStore.load()
                                            connectionText = if (key.isBlank()) "Kein Key gespeichert" else runCatching { DirectOpenAiClient53.health(key) }
                                                .fold({ "Verbunden · $it" }, { "Test fehlgeschlagen: ${it.message.orEmpty().take(120)}" })
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(13.dp)
                                ) { Text("Testen") }
                            }
                            TextButton(
                                onClick = {
                                    secretStore.clear()
                                    keySaved = false
                                    keyHint = ""
                                    apiKeyInput = ""
                                    connectionText = "API-Key vom Gerät gelöscht"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Gespeicherten API-Key löschen", color = Red) }
                        }
                        AssistantMode53.PROXY -> {
                            Text("Für dauerhafte Nutzung empfohlen. Hier gehört nur dein eigenes HTTPS-Backend hinein – niemals der OpenAI-Key.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
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
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        prefs.edit().putString("backend_url", backendUrl).putString("access_token", accessToken).apply()
                                        connectionText = "Proxy-Einstellungen gespeichert"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(13.dp)
                                ) { Text("Speichern") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            connectionText = if (!AssistantClient52.isConfigured(backendUrl)) {
                                                "Gültige HTTPS-Adresse erforderlich"
                                            } else {
                                                runCatching { AssistantClient52.health(backendUrl, accessToken) }
                                                    .fold({ "Verbunden · $it" }, { "Test fehlgeschlagen: ${it.message.orEmpty().take(100)}" })
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(13.dp)
                                ) { Text("Testen") }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Antworten automatisch vorlesen", fontWeight = FontWeight.Bold)
                            Text("Nur solange der Assistent geöffnet ist", color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoSpeak,
                            onCheckedChange = {
                                autoSpeak = it
                                prefs.edit().putBoolean("auto_speak", it).apply()
                                if (!it) tts?.stop()
                            }
                        )
                    }
                    if (connectionText.isNotBlank()) Text(connectionText, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
                    val added = addPackingSuggestions53(context, pendingPacking)
                    packingMessage = "$added neue Einträge wurden hinzugefügt."
                    pendingPacking = emptyList()
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { pendingPacking = emptyList() }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun SourceBadge53(source: AssistantSource52) {
    val label = when (source) {
        AssistantSource52.OPENAI_DIRECT -> "OpenAI direkt"
        AssistantSource52.OPENAI -> "OpenAI Proxy"
        AssistantSource52.LOCAL -> "Lokal"
    }
    val color = if (source == AssistantSource52.LOCAL) Blue else Green
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(11.dp)) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun AssistantSuggestionCard53(
    activity: MainActivity,
    snapshot: TripSnapshot,
    suggestion: AssistantSuggestion52,
    onPacking: () -> Unit
) {
    val destination = DestinationCatalog.places.firstOrNull { it.title.equals(suggestion.destinationTitle, ignoreCase = true) }
    val fuel = snapshot.fuelSuggestion?.takeIf { it.name.equals(suggestion.title, ignoreCase = true) }
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
        if (destination != null || fuel != null || suggestion.packingItems.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                destination?.let { place ->
                    Button(
                        onClick = { activity.openPointRoute(place.point, place.title) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Route öffnen") }
                } ?: fuel?.let { station ->
                    Button(
                        onClick = { activity.openPointRoute(station.point, station.name) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Tankstelle öffnen") }
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

private fun voiceIntent53(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Frage an den Reise-Assistenten")
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
}

private fun AssistantAnswer52.spokenText53(): String = buildString {
    append(headline).append(". ").append(summary)
    suggestions.take(4).forEach { suggestion ->
        append(". ").append(suggestion.title)
        if (suggestion.detail.isNotBlank()) append(": ").append(suggestion.detail)
        if (suggestion.reason.isNotBlank()) append(". ").append(suggestion.reason)
    }
    warnings.take(2).forEach { append(". Bitte beachten: ").append(it) }
}.take(3900)

private fun addPackingSuggestions53(context: Context, suggestions: List<String>): Int {
    val repository = PackingRepository(context)
    val existing = repository.state.items.map { it.name.lowercase() }.toMutableSet()
    val categoryId = repository.state.categories.firstOrNull { it.name.equals("KI-Vorschläge", ignoreCase = true) }?.id
        ?: repository.addCategory("KI-Vorschläge", "Vom Reise-Assistenten vorgeschlagen und von dir bestätigt")
    var added = 0
    suggestions.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase).take(12).forEach { name ->
        if (existing.none { it == name.lowercase() }) {
            repository.addItem(name, "1", categoryId, null, "Vom Reise-Assistenten bestätigt")
            existing += name.lowercase()
            added++
        }
    }
    return added
}