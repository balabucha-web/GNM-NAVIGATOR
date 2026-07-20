package de.balabucha.reisepilot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a relevant Wikimedia thumbnail for destination cards and details.
 * Requests are throttled, successful URLs are cached and transient failures are retried later.
 */
object WikiImageResolver {
    private val memory = ConcurrentHashMap<String, String>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(3)
    private const val RETRY_DELAY_MS = 10L * 60L * 1000L

    suspend fun resolve(context: Context, query: String, fallbackQuery: String): String? =
        withContext(Dispatchers.IO) {
            listOf(query, fallbackQuery)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .firstNotNullOfOrNull { resolveSingle(context.applicationContext, it) }
        }

    private suspend fun resolveSingle(context: Context, query: String): String? {
        memory[query]?.let { return it }
        val now = System.currentTimeMillis()
        if ((retryAfter[query] ?: 0L) > now) return null

        val prefs = context.getSharedPreferences("travel_images_v42", Context.MODE_PRIVATE)
        val cacheKey = "url_${query.hashCode()}"
        prefs.getString(cacheKey, null)?.takeIf(::usable)?.let {
            memory[query] = it
            return it
        }

        return gate.withPermit {
            memory[query]?.let { return@withPermit it }
            val result = runCatching {
                wikipediaSearchThumbnail(query) ?: commonsThumbnail(query)
            }.getOrNull()

            if (result != null) {
                memory[query] = result
                retryAfter.remove(query)
                prefs.edit().putString(cacheKey, result).apply()
            } else {
                retryAfter[query] = System.currentTimeMillis() + RETRY_DELAY_MS
            }
            result
        }
    }

    private fun wikipediaSearchThumbnail(query: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://en.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=0&gsrlimit=6" +
            "&gsrsearch=$encoded&prop=pageimages&piprop=thumbnail" +
            "&pithumbsize=1000&format=json&formatversion=2&origin=*"
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        for (index in 0 until pages.length()) {
            val url = pages.optJSONObject(index)
                ?.optJSONObject("thumbnail")
                ?.optString("source")
                .orEmpty()
            if (usable(url)) return url
        }
        return null
    }

    private fun commonsThumbnail(query: String): String? {
        val search = "$query -logo -flag -map -icon"
        val encoded = URLEncoder.encode(search, "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=12" +
            "&gsrsearch=$encoded&prop=imageinfo&iiprop=url%7Cmime%7Csize" +
            "&iiurlwidth=1000&format=json&formatversion=2&origin=*"
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        for (index in 0 until pages.length()) {
            val info = pages.optJSONObject(index)
                ?.optJSONArray("imageinfo")
                ?.optJSONObject(0)
                ?: continue
            val mime = info.optString("mime").lowercase()
            if (mime !in setOf("image/jpeg", "image/png", "image/webp")) continue
            if (info.optInt("width", 0) in 1..399) continue
            val thumbnail = info.optString("thumburl")
            val original = info.optString("url")
            val candidate = thumbnail.takeIf(::usable) ?: original.takeIf(::usable)
            if (candidate != null) return candidate
        }
        return null
    }

    private fun usable(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val lower = url.substringBefore('?').lowercase()
        return listOf(".pdf", ".djvu", ".tif", ".tiff", ".svg", ".gif").none { lower.endsWith(it) }
    }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.2 Android family travel app")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Wikimedia HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }
}
