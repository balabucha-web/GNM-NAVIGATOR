package de.balabucha.reisepilot

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a relevant Wikimedia photo and downloads it into the app cache.
 * Coil therefore reads a local file instead of making a second anonymous Wikimedia request.
 */
object WikiImageResolver {
    private val memory = ConcurrentHashMap<String, String>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(3)
    private const val RETRY_DELAY_MS = 5L * 60L * 1000L
    private const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L

    suspend fun resolve(context: Context, query: String, fallbackQuery: String): String? =
        withContext(Dispatchers.IO) {
            listOf(query, fallbackQuery)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .firstNotNullOfOrNull { resolveSingle(context.applicationContext, it) }
        }

    private suspend fun resolveSingle(context: Context, query: String): String? {
        memory[query]?.let { cached ->
            if (cached.startsWith("file:") && File(Uri.parse(cached).path.orEmpty()).isFile) return cached
            memory.remove(query)
        }

        val target = imageFile(context, query)
        if (target.isFile && target.length() > 4_096L) {
            return Uri.fromFile(target).toString().also { memory[query] = it }
        }

        val now = System.currentTimeMillis()
        if ((retryAfter[query] ?: 0L) > now) return null

        val prefs = context.getSharedPreferences("travel_images_v43", Context.MODE_PRIVATE)
        val sourceKey = "source_${query.hashCode()}"

        return gate.withPermit {
            if (target.isFile && target.length() > 4_096L) {
                return@withPermit Uri.fromFile(target).toString().also { memory[query] = it }
            }

            val rememberedSource = prefs.getString(sourceKey, null)?.takeIf(::usable)
            val source = rememberedSource ?: runCatching {
                wikipediaSearchThumbnail(query) ?: commonsThumbnail(query)
            }.getOrNull()

            val localUri = source?.let { runCatching { downloadImage(it, target) }.getOrNull() }
            if (localUri != null) {
                memory[query] = localUri
                retryAfter.remove(query)
                prefs.edit().putString(sourceKey, source).apply()
            } else {
                retryAfter[query] = System.currentTimeMillis() + RETRY_DELAY_MS
            }
            localUri
        }
    }

    private fun imageFile(context: Context, query: String): File {
        val directory = File(context.cacheDir, "travel_photos_v43").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(query.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.image")
    }

    private fun downloadImage(source: String, target: File): String? {
        val connection = URL(source).openConnection() as HttpURLConnection
        val temporary = File(target.parentFile, "${target.name}.part")
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.3 Android family travel app")
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty().lowercase()
            if (code !in 200..299 || !contentType.startsWith("image/")) return null

            var total = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) error("Destination photo is too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (total <= 4_096L) return null
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
            Uri.fromFile(target).toString()
        } finally {
            temporary.takeIf { it.exists() && (!target.exists() || target.length() <= 4_096L) }?.delete()
            connection.disconnect()
        }
    }

    private fun wikipediaSearchThumbnail(query: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://en.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=0&gsrlimit=8" +
            "&gsrsearch=$encoded&prop=pageimages&piprop=thumbnail" +
            "&pithumbsize=1200&format=json&formatversion=2&origin=*"
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
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=16" +
            "&gsrsearch=$encoded&prop=imageinfo&iiprop=url%7Cmime%7Csize" +
            "&iiurlwidth=1200&format=json&formatversion=2&origin=*"
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
            connection.setRequestProperty("User-Agent", "ReisePilot/4.3 Android family travel app")
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
