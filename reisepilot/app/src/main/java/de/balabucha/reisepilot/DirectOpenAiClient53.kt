package de.balabucha.reisepilot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DirectOpenAiClient53 {
    private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
    private const val MODELS_URL = "https://api.openai.com/v1/models"
    private const val FIRST_OUTPUT_LIMIT = 2_200
    private const val RETRY_OUTPUT_LIMIT = 3_800

    private class RetryableStructuredResponse(message: String) : IllegalStateException(message)

    suspend fun ask(apiKey: String, model: String, context: JSONObject): AssistantAnswer52 = withContext(Dispatchers.IO) {
        require(apiKey.startsWith("sk-") && apiKey.length >= 20) { "Kein gültiger API-Key gespeichert." }
        val cleanModel = cleanModel(model)

        try {
            parseAnswer(
                executeRequest(
                    apiKey = apiKey,
                    model = cleanModel,
                    context = context,
                    maxOutputTokens = FIRST_OUTPUT_LIMIT,
                    retry = false
                )
            )
        } catch (_: RetryableStructuredResponse) {
            // Structured outputs can still be incomplete when the output-token
            // budget is exhausted. Retry once with a smaller context, stricter
            // length instructions and a larger output budget.
            try {
                parseAnswer(
                    executeRequest(
                        apiKey = apiKey,
                        model = cleanModel,
                        context = compactRetryContext(context),
                        maxOutputTokens = RETRY_OUTPUT_LIMIT,
                        retry = true
                    )
                )
            } catch (error: RetryableStructuredResponse) {
                throw IllegalStateException(
                    "OpenAI konnte die strukturierte Antwort nicht vollständig abschließen. Bitte die Anfrage kürzer formulieren oder erneut versuchen."
                )
            }
        }
    }

    private fun executeRequest(
        apiKey: String,
        model: String,
        context: JSONObject,
        maxOutputTokens: Int,
        retry: Boolean
    ): JSONObject {
        val connection = URL(RESPONSES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 75_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/5.5.1 Android direct-test")

            val body = JSONObject().apply {
                put("model", model)
                put("store", false)
                put("max_output_tokens", maxOutputTokens)
                if (supportsReasoning(model)) {
                    put("reasoning", JSONObject().put("effort", "minimal"))
                }
                put(
                    "instructions",
                    buildString {
                        appendLine("Du bist der ReisePilot-Copilot für eine Familie mit zwei Erwachsenen und zwei Kindern.")
                        appendLine("Verwende ausschließlich die übergebenen Reisedaten. Erfinde niemals Wetter, Preise, Öffnungszeiten, Verkehr oder Parkplätze.")
                        appendLine("Formuliere knapp, praktisch und auf Deutsch. Maximal fünf Vorschläge.")
                        appendLine("headline maximal 100 Zeichen, summary maximal 360 Zeichen.")
                        appendLine("Pro Vorschlag: title maximal 90 Zeichen, detail und reason jeweils maximal 260 Zeichen.")
                        appendLine("destinationTitle muss leer bleiben oder exakt einem Titel aus destinations entsprechen.")
                        appendLine("Änderungen an Packliste, Route oder Tagesplan werden nur vorgeschlagen und nie automatisch ausgeführt.")
                        if (retry) append("Dies ist ein Wiederholungsversuch: besonders kurz antworten und alle JSON-Strings sicher abschließen.")
                    }.trim()
                )
                put("input", "REISEPILOT-KONTEXT:\n${context}\n\nErstelle eine konkrete, nachvollziehbare Antwort im vorgegebenen Schema.")
                put("text", JSONObject().apply {
                    put("verbosity", "low")
                    put("format", responseFormat())
                })
            }
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val responseBody = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(apiError(responseBody, code))
            return runCatching { JSONObject(responseBody) }
                .getOrElse { throw IllegalStateException("OpenAI hat eine ungültige Serverantwort geliefert.") }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAnswer(response: JSONObject): AssistantAnswer52 {
        val status = response.optString("status")
        val incompleteReason = response.optJSONObject("incomplete_details")?.optString("reason").orEmpty()
        val outputText = extractOutputText(response)
        if (status == "incomplete" || outputText.isBlank()) {
            throw RetryableStructuredResponse(
                if (incompleteReason.isBlank()) "OpenAI-Antwort unvollständig" else "OpenAI-Antwort unvollständig: $incompleteReason"
            )
        }
        val parsed = try {
            JSONObject(outputText)
        } catch (_: JSONException) {
            throw RetryableStructuredResponse("OpenAI-JSON wurde abgeschnitten")
        }
        return assistantAnswerFromJson52(parsed, AssistantSource52.OPENAI_DIRECT)
    }

    suspend fun health(apiKey: String): String = withContext(Dispatchers.IO) {
        require(apiKey.startsWith("sk-") && apiKey.length >= 20) { "Kein gültiger API-Key gespeichert." }
        val connection = URL(MODELS_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/5.5.1 Android direct-test")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(apiError(body, code))
            "API-Key gültig"
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanModel(value: String): String = value.trim()
        .takeIf { it.matches(Regex("[A-Za-z0-9._:-]{2,80}")) }
        ?: "gpt-5-mini"

    private fun supportsReasoning(model: String): Boolean =
        model.startsWith("gpt-5", ignoreCase = true) || model.startsWith("o", ignoreCase = true)

    private fun apiError(raw: String, code: Int): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message")
    }.getOrNull().orEmpty().ifBlank { "OpenAI HTTP $code" }.take(240)

    private fun extractOutputText(root: JSONObject): String {
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = root.optJSONArray("output") ?: JSONArray()
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val part = content.optJSONObject(contentIndex) ?: continue
                if (part.optString("type") == "output_text") {
                    part.optString("text").takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return ""
    }

    private fun compactRetryContext(original: JSONObject): JSONObject {
        val copy = JSONObject(original.toString())
        copy.optJSONArray("destinations")?.let { values ->
            copy.put("destinations", JSONArray().apply {
                for (index in 0 until minOf(values.length(), 16)) put(values.opt(index))
            })
        }
        copy.optJSONObject("packing")?.optJSONArray("openItems")?.let { values ->
            copy.getJSONObject("packing").put("openItems", JSONArray().apply {
                for (index in 0 until minOf(values.length(), 24)) put(values.opt(index))
            })
        }
        copy.optJSONArray("recentConversation")?.let { values ->
            val start = (values.length() - 2).coerceAtLeast(0)
            copy.put("recentConversation", JSONArray().apply {
                for (index in start until values.length()) put(values.opt(index))
            })
        }
        return copy
    }

    private fun stringSchema(maxLength: Int): JSONObject = JSONObject()
        .put("type", "string")
        .put("maxLength", maxLength)

    private fun responseFormat(): JSONObject = JSONObject().apply {
        put("type", "json_schema")
        put("name", "reisepilot_answer")
        put("strict", true)
        put("schema", JSONObject().apply {
            put("type", "object")
            put("additionalProperties", false)
            put("required", JSONArray(listOf("headline", "summary", "suggestions", "warnings")))
            put("properties", JSONObject().apply {
                put("headline", stringSchema(100))
                put("summary", stringSchema(360))
                put("suggestions", JSONObject().apply {
                    put("type", "array")
                    put("maxItems", 5)
                    put("items", JSONObject().apply {
                        put("type", "object")
                        put("additionalProperties", false)
                        put("required", JSONArray(listOf("title", "detail", "reason", "destinationTitle", "packingItems")))
                        put("properties", JSONObject().apply {
                            put("title", stringSchema(90))
                            put("detail", stringSchema(260))
                            put("reason", stringSchema(260))
                            put("destinationTitle", stringSchema(120))
                            put("packingItems", JSONObject().apply {
                                put("type", "array")
                                put("maxItems", 10)
                                put("items", stringSchema(80))
                            })
                        })
                    })
                })
                put("warnings", JSONObject().apply {
                    put("type", "array")
                    put("maxItems", 4)
                    put("items", stringSchema(180))
                })
            })
        })
    }
}
