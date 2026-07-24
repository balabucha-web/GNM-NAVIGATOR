package de.balabucha.reisepilot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DirectOpenAiClient53 {
    private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
    private const val MODELS_URL = "https://api.openai.com/v1/models"

    suspend fun ask(apiKey: String, model: String, context: JSONObject): AssistantAnswer52 = withContext(Dispatchers.IO) {
        require(apiKey.startsWith("sk-") && apiKey.length >= 20) { "Kein gültiger API-Key gespeichert." }
        val connection = URL(RESPONSES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 65_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/5.3 Android direct-test")

            val body = JSONObject().apply {
                put("model", cleanModel(model))
                put("store", false)
                put("max_output_tokens", 1400)
                put(
                    "instructions",
                    """
                    Du bist der ReisePilot-Copilot für eine Familie mit zwei Erwachsenen und zwei Kindern.
                    Verwende ausschließlich die übergebenen Reisedaten. Erfinde niemals Wetter, Preise,
                    Öffnungszeiten, Verkehr oder Parkplätze. Formuliere knapp, praktisch und auf Deutsch.
                    destinationTitle muss leer bleiben oder exakt einem Titel aus destinations entsprechen.
                    Änderungen an Packliste, Route oder Tagesplan werden nur vorgeschlagen und nie automatisch ausgeführt.
                    """.trimIndent()
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
            val response = JSONObject(responseBody)
            val outputText = extractOutputText(response)
            require(outputText.isNotBlank()) { "OpenAI hat keinen auswertbaren Text geliefert." }
            assistantAnswerFromJson52(JSONObject(outputText), AssistantSource52.OPENAI_DIRECT)
        } finally {
            connection.disconnect()
        }
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
            connection.setRequestProperty("User-Agent", "ReisePilot/5.3 Android direct-test")
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

    private fun responseFormat(): JSONObject = JSONObject().apply {
        put("type", "json_schema")
        put("name", "reisepilot_answer")
        put("strict", true)
        put("schema", JSONObject().apply {
            put("type", "object")
            put("additionalProperties", false)
            put("required", JSONArray(listOf("headline", "summary", "suggestions", "warnings")))
            put("properties", JSONObject().apply {
                put("headline", JSONObject().put("type", "string"))
                put("summary", JSONObject().put("type", "string"))
                put("suggestions", JSONObject().apply {
                    put("type", "array")
                    put("maxItems", 6)
                    put("items", JSONObject().apply {
                        put("type", "object")
                        put("additionalProperties", false)
                        put("required", JSONArray(listOf("title", "detail", "reason", "destinationTitle", "packingItems")))
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().put("type", "string"))
                            put("detail", JSONObject().put("type", "string"))
                            put("reason", JSONObject().put("type", "string"))
                            put("destinationTitle", JSONObject().put("type", "string"))
                            put("packingItems", JSONObject().apply {
                                put("type", "array")
                                put("maxItems", 12)
                                put("items", JSONObject().put("type", "string"))
                            })
                        })
                    })
                })
                put("warnings", JSONObject().apply {
                    put("type", "array")
                    put("maxItems", 5)
                    put("items", JSONObject().put("type", "string"))
                })
            })
        })
    }
}