package de.balabucha.reisepilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Curated second-pass image resolver for destinations whose original offline
 * image is missing or visually weak. It never falls back to a generic city
 * landmark and keeps the existing labelled illustration if no exact result is
 * defensible.
 */
internal object EnhancedDestinationImage56 {
    private const val PREFS = "enhanced_destination_images_v56"
    private const val MAX_AGE_MS = 21L * 24L * 60L * 60L * 1_000L
    private const val EMPTY_RETRY_MS = 6L * 60L * 60L * 1_000L
    private val memory = ConcurrentHashMap<String, String>()
    private val emptyUntil = ConcurrentHashMap<String, Long>()

    private val queries = mapOf(
        "Aqualand Saint-Cyprien" to listOf("Aqualand Saint Cyprien water park", "Aqualand Saint-Cyprien Pyrénées Orientales"),
        "Markt Canet-Plage" to listOf("marché Canet Plage", "market Canet-en-Roussillon"),
        "Intermarché Canet" to listOf("Intermarché Canet-en-Roussillon"),
        "Lidl Canet" to listOf("Lidl Canet-en-Roussillon"),
        "Carrefour Claira / Salanca" to listOf("Centre Commercial Salanca Claira", "Carrefour Claira Salanca"),
        "Luna Park Argelès" to listOf("Luna Park Argelès-sur-Mer", "Luna Park Argeles amusement park"),
        "La Roca Village" to listOf("La Roca Village outlet Barcelona"),
        "Camí de les Pardines & Engolasters" to listOf("Cami de les Pardines Andorra", "Lake Engolasters path"),
        "Ruta del Ferro" to listOf("Ruta del Ferro Andorra", "Camí Ral Ruta del Ferro Ordino"),
        "Mon(t) Magic Canillo" to listOf("Mon Magic Canillo Grandvalira", "Family Park Canillo Andorra"),
        "Palau de Gel Canillo" to listOf("Palau de Gel Canillo", "ice rink Canillo Andorra"),
        "Epizen" to listOf("Epizen Andorra shopping centre"),
        "Mirador de la Comella" to listOf("Mirador de la Comella Andorra", "La Comella viewpoint Andorra"),
        "Estanys de Juclà" to listOf("Estanys de Juclar Andorra", "Juclar lakes Andorra"),
        "Bici Lab Andorra" to listOf("Bici Lab Andorra museum"),
        "Disneyland Paris" to listOf("Disneyland Paris castle", "Disneyland Park Paris entrance"),
        "Anse de Paulilles" to listOf("Anse de Paulilles beach bay", "Plage de Paulilles"),
        "Naturland" to listOf("Naturland Andorra Tobotronc", "Naturlandia Andorra park"),
        "Santuari de Meritxell" to listOf("Santuari nou de Meritxell exterior", "Meritxell sanctuary Andorra"),
        "Sitges" to listOf("Sitges church beach panorama", "Sitges seafront Sant Bartomeu"),
        "Westfield Glòries" to listOf("Centre Comercial Glòries exterior Barcelona"),
        "Museu Marítim" to listOf("Museu Maritim Barcelona Drassanes interior", "Royal Shipyards Barcelona museum"),
        "CosmoCaixa" to listOf("CosmoCaixa Barcelona exterior", "CosmoCaixa Barcelona museum building"),
        "Musée Grévin" to listOf("Musée Grévin Paris facade", "Musée Grévin entrance Paris"),
        "Opéra Garnier" to listOf("Palais Garnier exterior Paris", "Opéra Garnier facade"),
        "Jardin d’Acclimatation" to listOf("Jardin d'Acclimatation attractions Paris", "Jardin d'Acclimatation amusement park")
    )

    private val blockedByPlace = mapOf(
        "Anse de Paulilles" to setOf("usine", "factory", "industrial"),
        "Sitges" to setOf("driftwood", "bois flotte"),
        "Westfield Glòries" to setOf("sostre", "ceiling", "roof detail"),
        "Museu Marítim" to setOf("vista a peu de carrer", "street view"),
        "CosmoCaixa" to setOf("experiment", "exhibit detail"),
        "Musée Grévin" to setOf("1959", "roland", "historic"),
        "Opéra Garnier" to setOf("postcard", "carte postale", "1909"),
        "Jardin d’Acclimatation" to setOf("riviere enchantee")
    )

    fun shouldEnhance(place: TravelPlace): Boolean = place.title in queries

    fun resolve(context: Context, place: TravelPlace): String? {
        if (!shouldEnhance(place)) return null
        memory[place.title]?.let { return it }
        val now = System.currentTimeMillis()
        if ((emptyUntil[place.title] ?: 0L) > now) return null

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong("time:${place.title}", 0L)
        val savedUrl = prefs.getString("url:${place.title}", null)
        if (!savedUrl.isNullOrBlank() && now - savedAt <= MAX_AGE_MS) {
            memory[place.title] = savedUrl
            emptyUntil.remove(place.title)
            return savedUrl
        }

        val result = runCatching { find(place) }.getOrNull()
        if (!result.isNullOrBlank()) {
            memory[place.title] = result
            emptyUntil.remove(place.title)
            prefs.edit()
                .putString("url:${place.title}", result)
                .putLong("time:${place.title}", now)
                .apply()
        } else {
            // ConcurrentHashMap rejects null values. Remember an empty lookup in
            // a separate timestamp map so the safe offline illustration remains
            // visible without repeatedly hitting Wikimedia while scrolling.
            emptyUntil[place.title] = now + EMPTY_RETRY_MS
        }
        return result
    }

    private fun find(place: TravelPlace): String? {
        val textCandidates = queries.getValue(place.title).flatMapIndexed { index, query ->
            commonsSearch(query, place, 220 - index * 20)
        }
        val bestText = rank(place, textCandidates).firstOrNull()
        if (bestText != null) return bestText.url

        if (place.kind == PlaceKind.SHOPPING) return null
        return rank(place, commonsGeo(place)).firstOrNull()?.url
    }

    private data class Candidate(val label: String, val url: String, val score: Int, val distanceM: Double? = null)

    private fun commonsSearch(query: String, place: TravelPlace, bonus: Int): List<Candidate> {
        val encoded = URLEncoder.encode("$query -logo -flag -map -poster -advertisement", "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=24&gsrsearch=$encoded" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=1200" +
            "&format=json&formatversion=2&origin=*"
        return parse(JSONObject(http(endpoint)), place, bonus)
    }

    private fun commonsGeo(place: TravelPlace): List<Candidate> {
        val radius = when (place.kind) {
            PlaceKind.NATURE -> 12_000
            PlaceKind.FAMILY -> 6_000
            else -> 4_000
        }
        val coordinate = URLEncoder.encode("${place.point.lat}|${place.point.lon}", "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=geosearch&ggsprimary=all&ggsnamespace=6" +
            "&ggsradius=$radius&ggslimit=36&ggscoord=$coordinate" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=1200" +
            "&format=json&formatversion=2&origin=*"
        return parse(JSONObject(http(endpoint)), place, 45)
    }

    private fun parse(root: JSONObject, place: TravelPlace, bonus: Int): List<Candidate> {
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        return buildList {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime")
                val width = info.optInt("width")
                val height = info.optInt("height")
                val url = info.optString("thumburl").ifBlank { info.optString("url") }
                if (mime !in setOf("image/jpeg", "image/png", "image/webp") || minOf(width, height) < 500 || !url.startsWith("https://")) continue
                val label = page.optString("title")
                val overlap = tokens(label).intersect(wantedTokens(place)).size
                val landscape = if (width >= height) 12 else 0
                val distance = page.optDouble("dist", Double.NaN).takeIf(Double::isFinite)
                add(Candidate(label, url, bonus + overlap * 55 + landscape - ((distance ?: 0.0) / 500).toInt(), distance))
            }
        }
    }

    private fun rank(place: TravelPlace, candidates: List<Candidate>): List<Candidate> {
        val wanted = wantedTokens(place)
        val blocked = blockedByPlace[place.title].orEmpty().map(::normalize)
        val genericBlocked = setOf("logo", "flag", "map", "poster", "advertisement", "brochure", "ticket", "portrait")
        return candidates.asSequence()
            .filterNot { candidate ->
                val label = normalize(candidate.label)
                genericBlocked.any(label::contains) || blocked.any(label::contains)
            }
            .filter { candidate ->
                val overlap = tokens(candidate.label).intersect(wanted)
                overlap.isNotEmpty() || (candidate.distanceM != null && candidate.distanceM <= 1_800.0 && place.kind != PlaceKind.SHOPPING)
            }
            .sortedByDescending { it.score }
            .distinctBy { it.url.substringBefore('?') }
            .toList()
    }

    private fun wantedTokens(place: TravelPlace): Set<String> =
        (listOf(place.title, place.imageQuery) + queries.getValue(place.title))
            .flatMap { tokens(it) }
            .toSet() - setOf("andorra", "barcelona", "paris", "france", "canet", "view", "exterior", "interior")

    private fun tokens(value: String): Set<String> = normalize(value)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 4 }
        .toSet()

    private fun normalize(value: String): String {
        val plain = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return plain.lowercase(Locale.ROOT)
    }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 24_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/5.6 curated destination image resolver")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) error("Wikimedia HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
