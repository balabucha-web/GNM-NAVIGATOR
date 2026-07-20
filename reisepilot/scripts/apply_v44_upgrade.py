#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/de/balabucha/reisepilot"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Missing expected block in {path}: {old[:140]!r}")
    path.write_text(text.replace(old, new, 1))


# Exact POI photo resolver with local multi-photo gallery cache.
(SRC / "WikiImage.kt").write_text(r'''package de.balabucha.reisepilot

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

    private val memory = ConcurrentHashMap<String, List<String>>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(2)
    private const val RETRY_DELAY_MS = 5L * 60L * 1000L
    private const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L

    suspend fun resolve(context: Context, place: TravelPlace): String? =
        resolveGallery(context, place, 1).firstOrNull()

    suspend fun resolveGallery(context: Context, place: TravelPlace, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            val wanted = limit.coerceIn(1, 5)
            val key = cacheKey(place)
            val memoryKey = "$key:$wanted"
            memory[memoryKey]?.filter(::localFileExists)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

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

                val candidates = runCatching { collectCandidates(place) }.getOrDefault(emptyList())
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

    private fun collectCandidates(place: TravelPlace): List<Candidate> {
        val queries = exactQueries(place)
        val all = mutableListOf<Candidate>()
        queries.forEachIndexed { index, query ->
            val queryBonus = (10 - index * 2).coerceAtLeast(2)
            all += wikipediaCandidates("de", query, place, queryBonus)
            all += wikipediaCandidates("en", query, place, queryBonus)
            all += commonsSearchCandidates(query, place, queryBonus)
        }
        all += commonsGeoCandidates(place)

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
                candidate.copy(score = candidate.score - alienLandmarks.size * 40)
            }
            .filter { it.score >= 10 }
            .sortedByDescending { it.score }
            .distinctBy { it.url.substringBefore('?') }
            .distinctBy { normalizedStem(it.label) }
            .take(18)
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
        val titleParts = place.title
            .split(" & ", " / ", ",")
            .map { it.trim() }
            .filter { it.length >= 4 }
            .map { "$it $city" }
        return (aliases + place.imageQuery + "${place.title} $city" + titleParts)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private val curatedAliases = mapOf(
        "Seine-Fahrt" to listOf("Bateaux Mouches Seine river cruise Paris"),
        "Louvre & Tuilerien" to listOf("Louvre Palace pyramid Paris", "Tuileries Garden Paris"),
        "Notre-Dame & Île de la Cité" to listOf("Notre Dame de Paris cathedral", "Ile de la Cite Paris"),
        "Montmartre & Sacré-Cœur" to listOf("Sacre Coeur basilica Montmartre", "Montmartre streets Paris"),
        "Galeries Lafayette Dachterrasse" to listOf("Galeries Lafayette Paris rooftop terrace"),
        "Strand & Promenade Canet" to listOf("Canet Plage beach promenade France"),
        "Fischerdorf & Étang" to listOf("Village de pecheurs etang de Canet Saint Nazaire"),
        "Banyuls & Biodiversarium" to listOf("Biodiversarium Banyuls sur Mer", "Banyuls sur Mer coast"),
        "Camí de les Pardines & Engolasters" to listOf("Cami de les Pardines Andorra", "Lake Engolasters Andorra"),
        "Tristaina-Seen & Solar-Aussichtspunkt" to listOf("Mirador Solar de Tristaina", "Estanys de Tristaina Andorra"),
        "Montjuïc, Seilbahn & Burg" to listOf("Montjuic cable car Barcelona", "Montjuic Castle Barcelona"),
        "Gotisches Viertel & Kathedrale" to listOf("Barcelona Cathedral Gothic Quarter"),
        "Parc de la Ciutadella & Arc de Triomf" to listOf("Parc de la Ciutadella Barcelona", "Arc de Triomf Barcelona")
    )

    private fun wikipediaCandidates(language: String, query: String, place: TravelPlace, bonus: Int): List<Candidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://$language.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=0&gsrlimit=8" +
            "&gsrsearch=$encoded&prop=pageimages&piprop=thumbnail" +
            "&pithumbsize=1280&format=json&formatversion=2&origin=*"
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
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=24" +
            "&gsrsearch=$encoded&prop=imageinfo&iiprop=url%7Cmime%7Csize" +
            "&iiurlwidth=1280&format=json&formatversion=2&origin=*"
        return parseCommons(endpoint, place, query, bonus)
    }

    private fun commonsGeoCandidates(place: TravelPlace): List<Candidate> {
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=geosearch&ggsprimary=all&ggsnamespace=6" +
            "&ggsradius=2500&ggslimit=36&ggscoord=${place.point.lat}%7C${place.point.lon}" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=1280" +
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
            connection.connectTimeout = 10_000
            connection.readTimeout = 22_000
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
            connection.connectTimeout = 8_000
            connection.readTimeout = 14_000
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
''')

# German Autobahn App traffic feed.
(SRC / "GermanTrafficClient.kt").write_text(r'''package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors

internal data class GermanTrafficEvent(
    val severity: Int,
    val road: String,
    val title: String,
    val detail: String
)

internal data class GermanTrafficSummary(
    val updated: String = "",
    val roads: List<String> = emptyList(),
    val events: List<GermanTrafficEvent> = emptyList(),
    val error: String? = null
)

/** Reads current warnings, closures and roadworks from the Autobahn App feed. */
internal object GermanTrafficClient {
    private const val BASE = "https://verkehr.autobahn.de/o/autobahn"
    private val saturdayRoads = listOf("A24", "A10", "A9", "A6", "A5")

    fun load(stage: Stage): GermanTrafficSummary {
        if (stage != Stage.SATURDAY) return GermanTrafficSummary(roads = emptyList())
        return runCatching {
            val executor = Executors.newFixedThreadPool(5)
            try {
                val tasks = saturdayRoads.flatMap { road ->
                    listOf("warning", "closure", "roadworks").map { kind ->
                        Callable { fetch(road, kind) }
                    }
                }
                val events = executor.invokeAll(tasks)
                    .flatMap { runCatching { it.get() }.getOrDefault(emptyList()) }
                    .distinctBy { "${it.road}:${it.title}:${it.detail.take(100)}" }
                    .sortedWith(compareByDescending<GermanTrafficEvent> { it.severity }.thenBy { it.road })
                    .take(8)
                GermanTrafficSummary(
                    updated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")),
                    roads = saturdayRoads,
                    events = events
                )
            } finally {
                executor.shutdownNow()
            }
        }.getOrElse { error ->
            GermanTrafficSummary(
                roads = saturdayRoads,
                error = error.message?.take(150) ?: "Deutsche Verkehrsdaten nicht erreichbar"
            )
        }
    }

    private fun fetch(road: String, kind: String): List<GermanTrafficEvent> {
        val root = JSONObject(http("$BASE/$road/services/$kind"))
        val array = root.optJSONArray(kind) ?: root.optJSONArray(kind.removeSuffix("s")) ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val rawTitle = row.optString("title").trim()
                val description = text(row.opt("description"))
                val subtitle = text(row.opt("subtitle"))
                val combined = listOf(rawTitle, subtitle, description).filter(String::isNotBlank).joinToString(" · ")
                if (combined.isBlank()) continue
                val blocked = row.optString("isBlocked").equals("true", true) || row.optBoolean("isBlocked")
                val title = when {
                    kind == "closure" || blocked -> "Sperrung"
                    kind == "roadworks" -> "Baustelle"
                    combined.contains("Unfall", true) -> "Unfall"
                    combined.contains("Stau", true) -> "Stau"
                    combined.contains("Gefahr", true) -> "Gefahrenstelle"
                    combined.contains("Panne", true) -> "Pannenfahrzeug"
                    else -> "Verkehrsmeldung"
                }
                val severity = when {
                    kind == "closure" || blocked -> 3
                    title in setOf("Unfall", "Stau", "Gefahrenstelle") -> 2
                    else -> 1
                }
                add(GermanTrafficEvent(severity, road, title, compact(combined, road)))
            }
        }
    }

    private fun text(value: Any?): String = when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) value.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" · ")
        is String -> value.trim()
        else -> ""
    }

    private fun compact(raw: String, road: String): String {
        var text = raw.replace(Regex("\\s+"), " ").trim(' ', '·')
        if (!text.contains(road)) text = "$road · $text"
        return text.take(230)
    }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "de")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.4 Android family travel app")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Autobahn-App HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }
}
''')

# Destination thumbnail: exact first POI image only. Detail view: 3-5 image gallery.
discover = SRC / "DiscoverScreen.kt"
text = discover.read_text()
text = text.replace("import androidx.compose.foundation.lazy.items\n", "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.itemsIndexed\n", 1)
old_thumbnail = r'''@Composable
private fun PlaceThumbnail(place: TravelPlace, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var finished by remember(place.region, place.title) { mutableStateOf(false) }
    val imageUrl by produceState<String?>(initialValue = null, place.imageQuery, place.region.imageFallback) {
        value = runCatching { WikiImageResolver.resolve(context, place.imageQuery, place.region.imageFallback) }.getOrNull()
        finished = true
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = regionColor(place.region)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DestinationArtwork(place.region, Modifier.fillMaxSize())
            Text(kindSymbol(place.kind), color = Navy.copy(alpha = .62f), fontWeight = FontWeight.Black, fontSize = 20.sp)
            imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = place.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (!finished) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy.copy(alpha = .55f))
            }
        }
    }
}
'''
new_thumbnail = r'''@Composable
private fun PlaceThumbnail(place: TravelPlace, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var finished by remember(place.region, place.title) { mutableStateOf(false) }
    val imageUrl by produceState<String?>(initialValue = null, place.region, place.title, place.imageQuery) {
        value = runCatching { WikiImageResolver.resolve(context, place) }.getOrNull()
        finished = true
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = regionColor(place.region)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DestinationArtwork(place.region, Modifier.fillMaxSize())
            Text(kindSymbol(place.kind), color = Navy.copy(alpha = .62f), fontWeight = FontWeight.Black, fontSize = 20.sp)
            imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = place.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (!finished) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy.copy(alpha = .55f))
            }
        }
    }
}
'''
if old_thumbnail not in text:
    raise SystemExit("PlaceThumbnail block not found")
text = text.replace(old_thumbnail, new_thumbnail, 1)

old_state = r'''    var imageFinished by remember(place.title) { mutableStateOf(false) }
    val imageUrl by produceState<String?>(
        initialValue = null,
        place.imageQuery,
        place.region.imageFallback
    ) {
        value = runCatching {
            WikiImageResolver.resolve(context, place.imageQuery, place.region.imageFallback)
        }.getOrNull()
        imageFinished = true
    }
'''
new_state = r'''    var imageFinished by remember(place.title) { mutableStateOf(false) }
    val gallery by produceState<List<String>>(
        initialValue = emptyList(),
        place.region,
        place.title,
        place.imageQuery
    ) {
        value = runCatching { WikiImageResolver.resolveGallery(context, place, 5) }.getOrDefault(emptyList())
        imageFinished = true
    }
'''
if old_state not in text:
    raise SystemExit("Place detail image state not found")
text = text.replace(old_state, new_state, 1)

old_header = r'''            Box(
                Modifier.fillMaxWidth().height(230.dp).background(
                    Brush.linearGradient(listOf(regionColor(place.region), Color(0xFFE7EEF2)))
                )
            ) {
                DestinationArtwork(place.region, Modifier.fillMaxSize())
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(kindSymbol(place.kind), color = Navy.copy(alpha = .55f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (imageFinished) place.region.label else "Bild wird geladen …",
                        color = Navy.copy(alpha = .65f),
                        fontWeight = FontWeight.Bold
                    )
                }
                imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = place.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = Navy.copy(alpha = .88f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                ) {
                    Text(
                        place.kind.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
'''
new_header = r'''            Box(
                Modifier.fillMaxWidth().height(250.dp).background(
                    Brush.linearGradient(listOf(regionColor(place.region), Color(0xFFE7EEF2)))
                )
            ) {
                DestinationArtwork(place.region, Modifier.fillMaxSize())
                if (gallery.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(gallery, key = { index, url -> "$index:$url" }) { index, url ->
                            Box(Modifier.width(350.dp).fillMaxHeight()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "${place.title} · Foto ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Navy.copy(alpha = .82f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                ) {
                                    Text(
                                        "${index + 1} / ${gallery.size}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(kindSymbol(place.kind), color = Navy.copy(alpha = .55f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text(
                            when {
                                !imageFinished -> "Passende Fotos werden geladen …"
                                else -> "Kein eindeutig passendes Foto gefunden"
                            },
                            color = Navy.copy(alpha = .65f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    color = Navy.copy(alpha = .88f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                ) {
                    Text(
                        place.kind.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
'''
if old_header not in text:
    raise SystemExit("Place detail header not found")
text = text.replace(old_header, new_header, 1)
discover.write_text(text)

# Start page naming and Germany live traffic card.
live = SRC / "LiveScreen.kt"
text = live.read_text()
old_bison_state = r'''    val bisonSummary by produceState(
        initialValue = BisonTrafficSummary(),
        chosen
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) { BisonTrafficClient.load(chosen) }
            delay(5L * 60L * 1000L)
        }
    }
'''
new_bison_state = old_bison_state + r'''

    val germanTraffic by produceState(
        initialValue = GermanTrafficSummary(),
        chosen
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) { GermanTrafficClient.load(chosen) }
            delay(5L * 60L * 1000L)
        }
    }
'''
if old_bison_state not in text:
    raise SystemExit("Bison state not found")
text = text.replace(old_bison_state, new_bison_state, 1)
text = text.replace('                    "ETA",', '                    "Ankunftszeit",', 1)
text = text.replace('        item { BisonTrafficCard(bisonSummary, chosen) }', '        item { GermanyTrafficCard(germanTraffic, chosen) }\n        item { BisonTrafficCard(bisonSummary, chosen) }', 1)
marker = '''@Composable
private fun BisonTrafficCard(summary: BisonTrafficSummary, stage: Stage) {'''
germany_card = r'''@Composable
private fun GermanyTrafficCard(summary: GermanTrafficSummary, stage: Stage) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Deutschland-Verkehr live", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (stage == Stage.SATURDAY) "Autobahn-App-Daten · A24, A10, A9, A6 und A5" else "Deutsche Etappe ist am Sonntag nicht aktiv",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            AssistChip(onClick = {}, label = { Text(if (stage == Stage.SATURDAY) "LIVE" else "–") })
        }
        if (summary.updated.isNotBlank()) {
            Text("Stand: ${summary.updated}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        when {
            stage != Stage.SATURDAY -> StatusLine("Deutschland", "Für die Sonntagsroute sind die französischen Meldungen relevant", Light.GREY)
            summary.error != null -> WarningCard("Deutschland-Verkehr nicht erreichbar", summary.error, Light.YELLOW)
            summary.events.isEmpty() -> StatusLine("Route", "Keine aktuelle Warnung, Sperrung oder Baustellenmeldung auf den vorgesehenen Autobahnen", Light.GREEN)
            else -> summary.events.take(5).forEach { event ->
                val light = when (event.severity) {
                    3 -> Light.RED
                    2 -> Light.YELLOW
                    else -> Light.GREY
                }
                StatusLine("${event.road} · ${event.title}", event.detail, light)
            }
        }
        Text(
            "Aktualisierung alle fünf Minuten. Mapbox berechnet zusätzlich die konkrete Verzögerung und Ankunftszeit auf deiner Route.",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

'''
if marker not in text:
    raise SystemExit("Bison card marker not found")
text = text.replace(marker, germany_card + marker, 1)
live.write_text(text)

# Version 4.4 build 13.
build = ROOT / "app/build.gradle.kts"
text = build.read_text().replace('versionCode = 12', 'versionCode = 13').replace('versionName = "4.3"', 'versionName = "4.4"')
build.write_text(text)

# Device test must prove that a multi-photo gallery is cached.
test = ROOT / "app/src/androidTest/java/de/balabucha/reisepilot/ReisePilotUserFlowTest.kt"
text = test.read_text()
text = text.replace('File(compose.activity.cacheDir, "travel_photos_v43")', 'File(compose.activity.cacheDir, "travel_photos_v44")')
text = text.replace(
    'photoDirectory.listFiles()?.any { it.isFile && it.length() > 4_096L } == true',
    '(photoDirectory.listFiles()?.count { it.isFile && it.length() > 4_096L } ?: 0) >= 2'
)
test.write_text(text)

print("Applied ReisePilot 4.4 exact-gallery and German-traffic upgrade")
