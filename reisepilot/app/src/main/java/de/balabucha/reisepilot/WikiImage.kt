package de.balabucha.reisepilot

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves photos for the exact POI and stores them as local files.
 * Generic city fallbacks are deliberately not used: a neutral local artwork is
 * preferable to a wrong Eiffel Tower, Sagrada Familia or regional stock photo.
 */
object WikiImageResolver {
    private data class Candidate(val label: String, val url: String, val score: Int)

    @Volatile
    internal var debugGalleryOverride: ((TravelPlace, Int) -> List<String>)? = null

    private val memory = ConcurrentHashMap<String, List<String>>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(2)
    private const val RETRY_DELAY_MS = 5L * 60L * 1000L
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L

    suspend fun resolve(context: Context, place: TravelPlace): String? =
        resolveGallery(context, place, 1).firstOrNull()

    suspend fun resolveGallery(context: Context, place: TravelPlace, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            val wanted = limit.coerceIn(1, 5)
            if (BuildConfig.DEBUG) {
                debugGalleryOverride?.invoke(place, wanted)?.take(wanted)?.let {
                    return@withContext it
                }
            }
            val key = cacheKey(place)
            val memoryKey = "$key:$wanted"
            memory[memoryKey]
                ?.filter(::localFileExists)
                ?.takeIf { it.isNotEmpty() }
                ?.let { return@withContext it }

            val existing = existingFiles(context.applicationContext, key, wanted)
            if (existing.size >= wanted) {
                memory[memoryKey] = existing
                return@withContext existing
            }

            val now = System.currentTimeMillis()
            if ((retryAfter[memoryKey] ?: 0L) > now) return@withContext existing

            gate.withPermit {
                val afterWait = existingFiles(context.applicationContext, key, wanted)
                if (afterWait.size >= wanted) {
                    memory[memoryKey] = afterWait
                    return@withPermit afterWait
                }

                val candidates = runCatching { collectCandidates(place, wanted) }.getOrDefault(emptyList())
                val usedSources = sourceUrls(context.applicationContext, key).toMutableSet()
                val local = afterWait.toMutableList()

                for (candidate in candidates) {
                    if (local.size >= wanted) break
                    if (!usedSources.add(candidate.url)) continue
                    val target = imageFile(context.applicationContext, key, local.size)
                    val downloaded = runCatching { downloadImage(candidate.url, target) }.getOrNull()
                    if (downloaded != null) local += downloaded
                }

                if (local.isNotEmpty()) {
                    saveSourceUrls(context.applicationContext, key, usedSources)
                    memory[memoryKey] = local
                    retryAfter.remove(memoryKey)
                } else {
                    retryAfter[memoryKey] = System.currentTimeMillis() + RETRY_DELAY_MS
                }
                local
            }
        }

    /** Used by the device audit to verify the selected POI, not unrelated list thumbnails. */
    fun cachedPhotoCount(context: Context, place: TravelPlace): Int {
        val key = cacheKey(place)
        return existingFiles(context.applicationContext, key, 5).size
    }

    /**
     * List thumbnails perform only one exact Commons search. The detail gallery
     * adds a coordinate search and one Wikipedia request in parallel. This keeps
     * scrolling responsive while still providing several exact POI photos.
     */
    private suspend fun collectCandidates(place: TravelPlace, wanted: Int): List<Candidate> {
        val queries = exactQueries(place)
        val primary = queries.first()

        if (wanted == 1) {
            val commons = runCatching {
                commonsSearchCandidates(primary, place, 13)
            }.getOrDefault(emptyList())
            val rankedCommons = rankCandidates(commons, place)
            if (rankedCommons.isNotEmpty()) return rankedCommons
            return rankCandidates(
                runCatching { wikipediaCandidates("en", primary, place, 12) }.getOrDefault(emptyList()),
                place
            )
        }

        val initial = supervisorScope {
            listOf(
                async(Dispatchers.IO) {
                    runCatching { commonsSearchCandidates(primary, place, 13) }.getOrDefault(emptyList())
                },
                async(Dispatchers.IO) {
                    runCatching { commonsGeoCandidates(place) }.getOrDefault(emptyList())
                },
                async(Dispatchers.IO) {
                    runCatching { wikipediaCandidates("en", primary, place, 11) }.getOrDefault(emptyList())
                }
            ).awaitAll().flatten()
        }

        var ranked = rankCandidates(initial, place)
        if (ranked.size < wanted && queries.size > 1) {
            val secondary = runCatching {
                commonsSearchCandidates(queries[1], place, 8)
            }.getOrDefault(emptyList())
            ranked = rankCandidates(initial + secondary, place)
        }
        return ranked
    }

    private fun rankCandidates(all: List<Candidate>, place: TravelPlace): List<Candidate> {
        val landmarkTokens = setOf(
            "eiffel", "trocadero", "louvre", "sacre", "montmartre", "versailles", "disneyland",
            "sagrada", "guell", "batllo", "pedrera", "caldea", "tristaina", "collioure"
        )
        val wanted = wantedTokens(place)

        return all.asSequence()
            .filter { usable(it.url) }
            .map { candidate ->
                val labelTokens = tokens(candidate.label)
                val alienLandmarks = labelTokens.intersect(landmarkTokens - wanted)
                candidate.copy(score = candidate.score - alienLandmarks.size * 45)
            }
            .filter { it.score >= 10 }
            .sortedByDescending { it.score }
            .distinctBy { it.url.substringBefore('?') }
            .distinctBy { normalizedStem(it.label) }
            .take(14)
            .toList()
    }

    private fun exactQueries(place: TravelPlace): List<String> {
        val city = when (place.region) {
            TravelRegion.CANET -> "Pyrénées-Orientales France"
            TravelRegion.BARCELONA -> "Barcelona"
            TravelRegion.ANDORRA -> "Andorra"
            TravelRegion.PARIS -> "Paris"
        }
        val aliases = curatedAliases[place.title].orEmpty()
        return (aliases + place.imageQuery + "${place.title} $city")
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(3)
    }

    private val curatedAliases = mapOf(
        "Seine-Fahrt" to listOf("Bateaux Mouches Seine river cruise Paris"),
        "Louvre & Tuilerien" to listOf("Louvre Palace pyramid Paris", "Tuileries Garden Paris"),
        "Notre-Dame & Île de la Cité" to listOf("Notre Dame de Paris cathedral", "Ile de la Cite Paris"),
        "Montmartre & Sacré-Cœur" to listOf("Sacre Coeur basilica Montmartre", "Montmartre streets Paris"),
        "Galeries Lafayette Dachterrasse" to listOf("Galeries Lafayette Paris rooftop terrace"),
        "Strand & Promenade Canet" to listOf("Canet Plage beach promenade France"),
        "Fischerdorf & Étang" to listOf("Village de pecheurs etang de Canet Saint Nazaire"),
        "Banyuls & Biodiversarium" to listOf("Biodiversarium Banyuls sur Mer", "Banyuls Sur Mer coast"),
        "Camí de les Pardines & Engolasters" to listOf("Cami de les Pardines Andorra", "Lake Engolasters Andorra"),
        "Tristaina-Seen & Solar-Aussichtspunkt" to listOf("Mirador Solar de Tristaina", "Estanys de Tristaina Andorra"),
        "Montjuïc, Seilbahn & Burg" to listOf("Montjuic cable car Barcelona", "Montjuic Castle Barcelona"),
        "Gotisches Viertel & Kathedrale" to listOf("Barcelona Cathedral Gothic Quarter"),
        "Parc de la Ciutadella & Arc de Triomf" to listOf("Parc de la Ciutadella Barcelona", "Arc de Triomf Barcelona")
    )

    private fun wikipediaCandidates(language: String, query: String, place: TravelPlace, bonus: Int): List<Candidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://$language.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=0&gsrlimit=6" +
            "&gsrsearch=$encoded&prop=pageimages&piprop=thumbnail" +
            "&pithumbsize=900&format=json&formatversion=2&origin=*"
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        return buildList {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val label = page.optString("title")
                val url = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                if (usable(url)) add(Candidate(label, url, relevance(label, place, query) + bonus + 12))
            }
        }
    }

    private fun commonsSearchCandidates(query: String, place: TravelPlace, bonus: Int): List<Candidate> {
        val search = "$query -logo -flag -map -icon -diagram -coat of arms"
        val encoded = URLEncoder.encode(search, "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=18" +
            "&gsrsearch=$encoded&prop=imageinfo&iiprop=url%7Cmime%7Csize" +
            "&iiurlwidth=900&format=json&formatversion=2&origin=*"
        return parseCommons(endpoint, place, query, bonus)
    }

    private fun commonsGeoCandidates(place: TravelPlace): List<Candidate> {
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=geosearch&ggsprimary=all&ggsnamespace=6" +
            "&ggsradius=1600&ggslimit=24&ggscoord=${place.point.lat}%7C${place.point.lon}" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=900" +
            "&format=json&formatversion=2&origin=*"
        return parseCommons(endpoint, place, place.imageQuery, 9)
    }

    private fun parseCommons(endpoint: String, place: TravelPlace, query: String, bonus: Int): List<Candidate> {
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        return buildList {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime").lowercase(Locale.ROOT)
                if (mime !in setOf("image/jpeg", "image/png", "image/webp")) continue
                if (info.optInt("width", 0) in 1..499) continue
                val label = page.optString("title")
                val thumbnail = info.optString("thumburl")
                val original = info.optString("url")
                val url = thumbnail.takeIf(::usable) ?: original.takeIf(::usable) ?: continue
                add(Candidate(label, url, relevance(label, place, query) + bonus))
            }
        }
    }

    private fun relevance(label: String, place: TravelPlace, query: String): Int {
        val labelTokens = tokens(label)
        val titleTokens = tokens(place.title)
        val queryTokens = tokens(query)
        val wanted = wantedTokens(place)
        var score = wanted.sumOf { if (it in labelTokens) 7 else 0 }
        score += titleTokens.sumOf { if (it in labelTokens) 8 else 0 }
        score += queryTokens.sumOf { if (it in labelTokens) 3 else 0 }
        if (titleTokens.isNotEmpty() && titleTokens.count { it in labelTokens } >= (titleTokens.size + 1) / 2) score += 18
        val lower = normalize(label)
        if (listOf("logo", "map", "karte", "plan", "icon", "flag", "coat of arms", "blason", "plaque").any(lower::contains)) score -= 60
        return score
    }

    private fun wantedTokens(place: TravelPlace): Set<String> =
        (tokens(place.title) + tokens(place.imageQuery)).toSet()

    private val stopWords = setOf(
        "und", "the", "des", "der", "die", "das", "de", "du", "la", "le", "les", "del", "della",
        "paris", "barcelona", "andorra", "france", "frankreich", "canet", "roussillon", "museum", "musee"
    )

    private fun tokens(value: String): Set<String> = normalize(value)
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(' ')
        .map(String::trim)
        .filter { it.length >= 4 && it !in stopWords }
        .toSet()

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

    private fun normalizedStem(label: String): String = normalize(label)
        .removePrefix("file ")
        .replace(Regex("\\b(19|20)\\d{2}\\b"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .take(80)

    private fun cacheKey(place: TravelPlace): String = sha256("${place.region.name}|${place.title}|${place.imageQuery}")

    private fun existingFiles(context: Context, key: String, limit: Int): List<String> =
        (0 until limit).mapNotNull { index ->
            imageFile(context, key, index).takeIf { it.isFile && it.length() > 4_096L }
                ?.let { Uri.fromFile(it).toString() }
        }

    private fun imageFile(context: Context, key: String, index: Int): File {
        val directory = File(context.cacheDir, "travel_photos_v44").apply { mkdirs() }
        return File(directory, "$key-$index.image")
    }

    private fun sourceUrls(context: Context, key: String): Set<String> {
        val prefs = context.getSharedPreferences("travel_images_v44", Context.MODE_PRIVATE)
        return prefs.getString("sources_$key", "").orEmpty().lineSequence().filter(String::isNotBlank).toSet()
    }

    private fun saveSourceUrls(context: Context, key: String, sources: Set<String>) {
        context.getSharedPreferences("travel_images_v44", Context.MODE_PRIVATE)
            .edit().putString("sources_$key", sources.joinToString("\n")).apply()
    }

    private fun localFileExists(uri: String): Boolean =
        uri.startsWith("file:") && File(Uri.parse(uri).path.orEmpty()).let { it.isFile && it.length() > 4_096L }

    private fun downloadImage(source: String, target: File): String? {
        val connection = URL(source).openConnection() as HttpURLConnection
        val temporary = File(target.parentFile, "${target.name}.part")
        return try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.4 Android family travel app")
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
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

    private fun usable(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val lower = url.substringBefore('?').lowercase(Locale.ROOT)
        return listOf(".pdf", ".djvu", ".tif", ".tiff", ".svg", ".gif").none { lower.endsWith(it) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.lowercase(Locale.ROOT).toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.4 Android family travel app")
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
