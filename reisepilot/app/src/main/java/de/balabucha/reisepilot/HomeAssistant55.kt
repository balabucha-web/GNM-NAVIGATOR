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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAssistant55(
    activity: MainActivity,
    snapshot: TripSnapshot,
    weather: RouteWeather55,
    modifier: Modifier,
    initialIntent: AssistantIntent52 = AssistantIntent52.WHAT_TODAY,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = activity.applicationContext
    val prefs = remember { context.getSharedPreferences("assistant_settings_v1", Context.MODE_PRIVATE) }
    val secretStore = remember { SecureApiKeyStore53(context) }
    var mode by rememberSaveable { mutableStateOf(AssistantMode53.fromStored(prefs.getString("assistant_mode", null))) }
    var selectedIntent by rememberSaveable { mutableStateOf(initialIntent) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf<AssistantAnswer52?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var keyHint by remember { mutableStateOf(secretStore.hint()) }
    var model by rememberSaveable { mutableStateOf(prefs.getString("direct_model", "gpt-5-mini").orEmpty()) }
    var backendUrl by rememberSaveable { mutableStateOf(prefs.getString("backend_url", "").orEmpty()) }
    var accessToken by rememberSaveable { mutableStateOf(prefs.getString("access_token", "").orEmpty()) }
    var pendingPacking by remember { mutableStateOf<List<String>>(emptyList()) }
    var history by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.GERMANY) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
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

    fun spokenText(result: AssistantAnswer52): String = buildString {
        append(result.headline).append(". ").append(result.summary)
        result.suggestions.take(3).forEach { append(". ").append(it.title).append(": ").append(it.detail) }
    }

    fun runAssistant(overrideQuestion: String? = null) {
        if (loading) return
        val actualQuestion = overrideQuestion?.trim().orEmpty().ifBlank { question.trim() }
        loading = true
        message = ""
        scope.launch {
            val request = AssistantContext52.build(context, snapshot, selectedIntent, actualQuestion, history).apply {
                put("weather", weather.json())
                put("weatherNote", if (weather.points.isEmpty()) "Keine Wetterdaten verfügbar; nichts erfinden." else "Open-Meteo-Daten verwenden; Öffnungszeiten weiterhin nicht erfinden.")
            }
            val result = runCatching {
                when (mode) {
                    AssistantMode53.LOCAL -> AssistantOffline52.answer(context, snapshot, selectedIntent, actualQuestion)
                    AssistantMode53.DIRECT -> DirectOpenAiClient53.ask(secretStore.load(), model, request)
                    AssistantMode53.PROXY -> AssistantClient52.ask(backendUrl, accessToken, request)
                }
            }.getOrElse { error ->
                message = "AI nicht erreichbar: ${error.message.orEmpty().take(150)} · lokale Auswertung verwendet."
                AssistantOffline52.answer(context, snapshot, selectedIntent, actualQuestion)
            }
            answer = result
            history = (history + (actualQuestion to result.summary)).takeLast(4)
            loading = false
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                question = spoken
                selectedIntent = AssistantIntent52.CUSTOM
                runAssistant(spoken)
            }
        }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Frage an ReisePilot")
                }
            )
        } else message = "Mikrofonberechtigung wurde nicht erteilt."
    }

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Frage an ReisePilot")
                }
            )
        } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Page("Reise-Assistent", "OpenAI, Sprache und lokale Reiseauswertung", modifier) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Zurück")
                }
                Button(onClick = ::startVoice, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Sprechen & fragen")
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AssistantIntent52.entries, key = { it.name }) { intent ->
                    FilterChip(
                        selected = selectedIntent == intent,
                        onClick = { selectedIntent = intent },
                        label = { Text(intent.label) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Deine Frage") },
                placeholder = { Text("Was passt heute, wo tanken oder wann Pause machen?") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("assistant-question-55"),
                shape = RoundedCornerShape(18.dp)
            )
        }
        item {
            Button(
                onClick = { runAssistant() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "ReisePilot denkt …" else selectedIntent.label)
            }
        }
        if (message.isNotBlank()) item { WarningCard("Hinweis", message, Light.YELLOW) }
        answer?.let { result ->
            item {
                AppCard {
                    SectionTitle(result.headline, result.source.name)
                    Spacer(Modifier.height(7.dp))
                    Text(result.summary)
                    if (ttsReady) {
                        TextButton(onClick = { tts?.speak(spokenText(result), TextToSpeech.QUEUE_FLUSH, null, "home-ai-55") }) {
                            Text("Antwort vorlesen")
                        }
                    }
                }
            }
            items(result.suggestions, key = { "ai55:${it.title}:${it.destinationTitle}" }) { suggestion ->
                AppCard {
                    Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
                    if (suggestion.detail.isNotBlank()) Text(suggestion.detail, color = Muted)
                    if (suggestion.reason.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(suggestion.reason, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                    val place = DestinationCatalog.places.firstOrNull { it.title == suggestion.destinationTitle }
                    if (place != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { activity.openPointRoute(place.point, place.title) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Route öffnen") }
                    }
                    if (suggestion.packingItems.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { pendingPacking = suggestion.packingItems },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Zur Packliste hinzufügen") }
                    }
                }
            }
            result.warnings.forEach { warning -> item { WarningCard("Beachten", warning, Light.YELLOW) } }
        }
        item {
            AppCard {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("AI-Modus und API-Key", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (mode) {
                                AssistantMode53.LOCAL -> "Lokale Auswertung"
                                AssistantMode53.DIRECT -> "OpenAI direkt ${keyHint.ifBlank { "ohne gespeicherten Key" }}"
                                AssistantMode53.PROXY -> "Eigener Proxy"
                            },
                            color = Muted
                        )
                    }
                    TextButton(onClick = { setupExpanded = !setupExpanded }) {
                        Text(if (setupExpanded) "Schließen" else "Einrichten")
                    }
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
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, AssistantMode53.entries.size),
                                label = { Text(item.label) }
                            )
                        }
                    }
                    if (mode == AssistantMode53.DIRECT) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            label = { Text("OpenAI API-Key") },
                            trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Verbergen" else "Zeigen") } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Modell") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    runCatching { secretStore.save(apiKeyInput) }
                                        .onSuccess {
                                            keyHint = secretStore.hint()
                                            apiKeyInput = ""
                                            prefs.edit().putString("direct_model", model.trim()).apply()
                                            message = "API-Key verschlüsselt gespeichert."
                                        }
                                        .onFailure { message = it.message.orEmpty() }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Key speichern") }
                            OutlinedButton(
                                onClick = {
                                    secretStore.clear()
                                    keyHint = ""
                                    message = "API-Key vom Gerät gelöscht."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Löschen") }
                        }
                    }
                    if (mode == AssistantMode53.PROXY) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = { backendUrl = it },
                            label = { Text("HTTPS-Proxy-Adresse") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("Zugangscode") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                prefs.edit().putString("backend_url", backendUrl.trim()).putString("access_token", accessToken.trim()).apply()
                                message = "Proxy-Einstellungen gespeichert."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Proxy speichern") }
                    }
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
                TextButton(onClick = {
                    val repository = PackingRepository(context)
                    val category = repository.state.categories.firstOrNull()
                    if (category != null) {
                        pendingPacking.distinct().take(12).forEach { item ->
                            repository.addItem(item, "1", category.id, null, "Vorschlag Reise-Assistent")
                        }
                        message = "${pendingPacking.distinct().size} Einträge hinzugefügt."
                    }
                    pendingPacking = emptyList()
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { pendingPacking = emptyList() }) { Text("Abbrechen") } }
        )
    }
}
