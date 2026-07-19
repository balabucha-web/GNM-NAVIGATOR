package de.balabucha.reisepilot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object WikiImageResolver {
    private val memory = ConcurrentHashMap<String, String?>()

    suspend fun resolve(context: Context, title: String): String? = withContext(Dispatchers.IO) {
        if (memory.containsKey(title)) return@withContext memory[title]
        val prefs = context.getSharedPreferences("wiki_images", Context.MODE_PRIVATE)
        val key = "image_${title.hashCode()}"
        if (prefs.contains(key)) {
            val cached = prefs.getString(key, "").orEmpty().ifBlank { null }
            memory[title] = cached
            return@withContext cached
        }

        val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val result = runCatching {
            val connection = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/3.3 Android")
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                json.optJSONObject("thumbnail")?.optString("source")
                    ?.takeIf { it.startsWith("https://") }
                    ?: json.optJSONObject("originalimage")?.optString("source")
                        ?.takeIf { it.startsWith("https://") }
            }
        }.getOrNull()

        prefs.edit().putString(key, result.orEmpty()).apply()
        memory[title] = result
        result
    }
}
