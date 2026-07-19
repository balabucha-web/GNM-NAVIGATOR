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
 * Resolves a compact, relevant Wikimedia image. Failures are deliberately harmless:
 * no null is ever inserted into ConcurrentHashMap and a failed image never crashes the UI.
 */
object WikiImageResolver {
    private val memory = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(2)

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
        if (missing.contains(query)) return null

        val prefs = context.getSharedPreferences("travel_images_v34", Context.MODE_PRIVATE)
        val cacheKey = "url_${query.hashCode()}"
        prefs.getString(cacheKey, null)?.takeIf { it.startsWith("https://") }?.let {
            memory[query] = it
            return it
        }

        return gate.withPermit {
            memory[query]?.let { return@withPermit it }
            val result = runCatching {
                wikipediaThumbnail(query) ?: commonsThumbnail(query)
            }.getOrNull()

            if (result != null) {
                memory[query] = result
                prefs.edit().putString(cacheKey, result).apply()
            } else {
                missing.add(query)
            }
            result
        }
    }

    private fun wikipediaThumbnail(query: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://en.wikipedia.org/w/api.php" +
            "?action=query&prop=pageimages&piprop=thumbnail&pithumbsize=1200" +
            "&redirects=1&format=json&formatversion=2&titles=$encoded"
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
        val search = "$query -logo -flag -map"
        val encoded = URLEncoder.encode(search, "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=8" +
            "&gsrsearch=$encoded&prop=imageinfo&iiprop=url%7Cmime" +
            "&iiurlwidth=1200&format=json&formatversion=2"
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        for (index in 0 until pages.length()) {
            val info = pages.optJSONObject(index)
                ?.optJSONArray("imageinfo")
                ?.optJSONObject(0)
                ?: continue
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
        return listOf(".pdf", ".djvu", ".tif", ".tiff").none { lower.endsWith(it) }
    }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/3.4 Android (family travel app)")
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
