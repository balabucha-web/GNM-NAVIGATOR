package de.balabucha.reisepilot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AssistantClient52 {
    suspend fun ask(
        baseUrl: String,
        accessToken: String,
        context: JSONObject
    ): AssistantAnswer52 = withContext(Dispatchers.IO) {
        val endpoint = endpoint(baseUrl, "assistant")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/5.2 Android")
            accessToken.trim().takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.outputStream.use { output ->
                output.write(context.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) {
                runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty().ifBlank { "Backend HTTP $code" }
            }
            assistantAnswerFromJson52(JSONObject(body), AssistantSource52.OPENAI)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun health(baseUrl: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val connection = URL(endpoint(baseUrl, "health")).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            accessToken.trim().takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "Backend HTTP $code" }
            JSONObject(body).optString("status", "bereit")
        } finally {
            connection.disconnect()
        }
    }

    fun isConfigured(baseUrl: String): Boolean = runCatching {
        val url = URL(baseUrl.trim())
        url.protocol == "https" && url.host.isNotBlank()
    }.getOrDefault(false)

    private fun endpoint(baseUrl: String, path: String): String =
        "${baseUrl.trim().trimEnd('/')}/$path"
}
