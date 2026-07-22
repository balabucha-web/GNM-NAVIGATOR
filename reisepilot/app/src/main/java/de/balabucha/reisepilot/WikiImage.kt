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
    private data class Candidate(
        val label: String,
        val url: String,
        val score: Int,
        val trustedCategory: Boolean = false
    )

    @Volatile
    internal var debugGalleryOverride: ((TravelPlace, Int) -> List<String>)? = null

    private val memory = ConcurrentHashMap<String, List<String>>()
    private val identityCandidateMemory = ConcurrentHashMap<String, List<Candidate>>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(2)
    private const val RETRY_DELAY_MS = 5L * 60L * 1000L
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L

    suspend fun resolve(context: Context, place: TravelPlace): String? =
        resolveGallery(context, place, 1).firstOrNull()

    suspend fun resolveGallery(context: Context, place: TravelPlace, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            val wanted = limit.coerceIn(1, 8)
            debugGalleryOverride?.invoke(place, wanted)?.take(wanted)?.let {
                return@withContext it
            }
            val key = cacheKey(place)
            val memoryKey = "$key:$wanted"
            memory[memoryKey]
                ?.filter(::localFileExists)
                ?.takeIf { it.size >= wanted }
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
                    if (candidate.url in usedSources) continue
                    val targetIndex = (0 until wanted).firstOrNull { index ->
                        val file = imageFile(context.applicationContext, key, index)
                        !file.isFile || file.length() <= 4_096L
                    } ?: break
                    val target = imageFile(context.applicationContext, key, targetIndex)
                    val downloaded = runCatching { downloadImage(candidate.url, target) }.getOrNull()
                    if (downloaded != null) {
                        usedSources += candidate.url
                        local += downloaded
                    }
                }

                if (local.isNotEmpty()) {
                    saveSourceUrls(context.applicationContext, key, usedSources)
                    val complete = existingFiles(context.applicationContext, key, wanted)
                    memory[memoryKey] = complete
                    if (complete.size >= wanted) {
                        retryAfter.remove(memoryKey)
                    } else {
                        retryAfter[memoryKey] = System.currentTimeMillis() + 75_000L
                    }
                } else {
                    retryAfter[memoryKey] = System.currentTimeMillis() + RETRY_DELAY_MS
                }
                existingFiles(context.applicationContext, key, wanted)
            }
        }

    /** Used by the device audit to verify the selected POI, not unrelated list thumbnails. */
    fun cachedPhotoCount(context: Context, place: TravelPlace): Int {
        val key = cacheKey(place)
        return existingFiles(context.applicationContext, key, 8).size
    }

    /**
     * List thumbnails start with a lightweight exact search. Detail galleries
     * additionally resolve exact Commons categories. Files from such a category
     * remain attached to the POI even when their camera filename has no useful
     * words; broad coordinate results still have to pass the strict label check.
     */
    private suspend fun collectCandidates(place: TravelPlace, wanted: Int): List<Candidate> {
        val queries = exactQueries(place)
        val primary = queries.first()
        val languages = wikipediaLanguages(place.region)

        if (wanted == 1) {
            val identity = runCatching {
                wikidataCandidates(place, includeCategory = false)
            }.getOrDefault(emptyList())
            val rankedIdentity = rankCandidates(identity, place)
            if (rankedIdentity.isNotEmpty()) return rankedIdentity
            val commons = runCatching {
                commonsSearchCandidates(primary, place, 13)
            }.getOrDefault(emptyList())
            val rankedCommons = rankCandidates(commons, place)
            if (rankedCommons.isNotEmpty()) return rankedCommons
            val category = runCatching {
                commonsCategoryCandidates(primary, place)
            }.getOrDefault(emptyList())
            if (category.isNotEmpty()) return rankCandidates(category, place)
            return rankCandidates(languages.flatMap { language ->
                runCatching { wikipediaCandidates(language, primary, place, 12) }.getOrDefault(emptyList())
            }, place)
        }

        val exactIdentity = runCatching {
            wikidataCandidates(place, includeCategory = true)
        }.getOrDefault(emptyList())
        val rankedIdentity = rankCandidates(exactIdentity, place)
        if (rankedIdentity.size >= wanted) return rankedIdentity

        val initial = supervisorScope {
            listOf(
                async(Dispatchers.IO) {
                    runCatching { commonsCategoryCandidates(primary, place) }.getOrDefault(emptyList())
                },
                async(Dispatchers.IO) {
                    runCatching { commonsSearchCandidates(primary, place, 13) }.getOrDefault(emptyList())
                },
                async(Dispatchers.IO) {
                    runCatching { commonsGeoCandidates(place) }.getOrDefault(emptyList())
                },
                async(Dispatchers.IO) {
                    runCatching { wikipediaCandidates(languages.first(), primary, place, 11) }.getOrDefault(emptyList())
                }
            ).awaitAll().flatten()
        }

        var ranked = rankCandidates(exactIdentity + initial, place)
        if (ranked.size < wanted && queries.size > 1) {
            val secondary = supervisorScope {
                queries.drop(1).take(3).flatMap { query ->
                    listOf(
                        async(Dispatchers.IO) {
                            runCatching { commonsCategoryCandidates(query, place) }.getOrDefault(emptyList())
                        },
                        async(Dispatchers.IO) {
                            runCatching { commonsSearchCandidates(query, place, 8) }.getOrDefault(emptyList())
                        }
                    )
                }.plus(
                    languages.drop(1).take(2).map { language ->
                        async(Dispatchers.IO) {
                            runCatching { wikipediaCandidates(language, primary, place, 9) }.getOrDefault(emptyList())
                        }
                    }
                ).awaitAll().flatten()
            }
            ranked = rankCandidates(exactIdentity + initial + secondary, place)
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
            .filterNot { obviouslyWrongMedia(it.label) }
            .filter { candidate -> shoppingCandidateFits(place, candidate.label) }
            .filter { candidate ->
                candidate.trustedCategory || tokens(candidate.label).intersect(wanted).isNotEmpty()
            }
            .map { candidate ->
                val labelTokens = tokens(candidate.label)
                val alienLandmarks = labelTokens.intersect(landmarkTokens - wanted)
                candidate.copy(score = candidate.score - alienLandmarks.size * 45)
            }
            .filter { it.trustedCategory || it.score >= 18 }
            .sortedByDescending { it.score }
            .distinctBy { it.url.substringBefore('?') }
            .distinctBy { normalizedStem(it.label) }
            .take(20)
            .toList()
    }

    private fun obviouslyWrongMedia(label: String): Boolean {
        val normalized = normalize(label)
        val blockedWords = setOf(
            "logo", "flag", "blason", "escutcheon", "diagram", "pictogram", "poster",
            "advertisement", "ticket", "brochure"
        )
        val words = normalized.replace(Regex("[^a-z0-9]+"), " ").split(' ').filter(String::isNotBlank)
        if (words.any(blockedWords::contains)) return true
        return listOf(
            " logo", "logo ", " flag", "flag ", " coat of arms", "blason", "escut",
            " map of", "location map", "plan de", "site plan", "diagram", "pictogram",
            "poster", "advertisement", "ticket", "brochure", "metrostation", "railway station"
        ).any(normalized::contains)
    }

    private fun shoppingCandidateFits(place: TravelPlace, label: String): Boolean {
        val requiredLocation = when (place.title) {
            "Intermarché Canet", "Lidl Canet" -> listOf("canet", "roussillon")
            "Carrefour Claira / Salanca" -> listOf("claira", "salanca")
            else -> return true
        }
        val normalizedLabel = normalize(label)
        return requiredLocation.any(normalizedLabel::contains)
    }

    private fun exactQueries(place: TravelPlace): List<String> {
        val city = when (place.region) {
            TravelRegion.CANET -> "Pyrénées-Orientales France"
            TravelRegion.BARCELONA -> "Barcelona"
            TravelRegion.ANDORRA -> "Andorra"
            TravelRegion.PARIS -> "Paris"
        }
        val detailTerm = when (place.kind) {
            PlaceKind.HIGHLIGHT -> "architecture view"
            PlaceKind.FAMILY -> "visitor site"
            PlaceKind.NATURE -> "landscape"
            PlaceKind.QUICK -> "street view"
            PlaceKind.RAIN -> "interior"
            PlaceKind.SHOPPING -> "exterior"
        }
        val identities = DestinationMediaCatalog.identities(place)
        val aliases = curatedAliases[place.title].orEmpty()
        return (identities + aliases + place.imageQuery + "${place.title} $city" + "${place.imageQuery} $detailTerm")
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(7)
    }

    /**
     * Resolves the real-world Wikidata item first and validates it against the
     * destination coordinates. P18 is the identity image; P373 points to the
     * exact Commons category and provides the remainder of a trustworthy gallery.
     */
    private fun wikidataCandidates(place: TravelPlace, includeCategory: Boolean): List<Candidate> {
        val memoryKey = "${place.region.name}:${place.title}:$includeCategory"
        identityCandidateMemory[memoryKey]?.let { return it }
        val identities = DestinationMediaCatalog.identities(place).take(if (includeCategory) 2 else 1)
        val pinnedIds = DestinationMediaCatalog.wikidataIds(place)
        val resolved = identities.flatMapIndexed { index, identity ->
            val ids = pinnedIds.getOrNull(index)?.let { listOf(it) }
                ?: searchWikidataIds(identity, place.region)
            if (ids.isEmpty()) return@flatMapIndexed emptyList()

            val entities = wikidataEntities(ids)
            val entity = entities.mapNotNull { entityCandidate(it, identity, place) }
                .maxByOrNull { it.first }
                ?.second
                ?: return@flatMapIndexed emptyList()

            val label = entity.optJSONObject("labels")?.let { labels ->
                listOf("de", wikipediaLanguages(place.region).first(), "en")
                    .firstNotNullOfOrNull { language -> labels.optJSONObject(language)?.optString("value")?.takeIf(String::isNotBlank) }
            }.orEmpty().ifBlank { identity }
            val claims = entity.optJSONObject("claims") ?: JSONObject()
            val photos = claimStrings(claims, "P18").take(2).map { filename ->
                val encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
                Candidate(
                    label = "$label · $filename",
                    url = "https://commons.wikimedia.org/wiki/Special:Redirect/file/$encoded?width=1000",
                    score = 180 - index * 8,
                    trustedCategory = true
                )
            }
            val categoryPhotos = if (includeCategory) {
                claimStrings(claims, "P373").take(1).flatMap { category ->
                    runCatching { commonsExactCategoryCandidates(category, place, 145 - index * 8) }
                        .getOrDefault(emptyList())
                }
            } else emptyList()
            photos + categoryPhotos
        }
        if (resolved.isNotEmpty()) identityCandidateMemory[memoryKey] = resolved
        return resolved
    }

    private fun searchWikidataIds(identity: String, region: TravelRegion): List<String> {
        val compactTerms = buildList {
            add(identity)
            val words = identity.split(Regex("\\s+")).filter(String::isNotBlank)
            if (words.size > 3) add(words.take(3).joinToString(" "))
            if (words.size > 1) add(words.take(2).joinToString(" "))
            if (words.isNotEmpty()) add(words.first())
        }.distinct().filter { it.length >= 4 }

        val languages = (listOf(wikipediaLanguages(region).first(), "en") + wikipediaLanguages(region))
            .distinct()
            .take(3)
        languages.forEach { language ->
            compactTerms.take(3).forEach { term ->
                val encoded = URLEncoder.encode(term, "UTF-8")
                val endpoint = "https://www.wikidata.org/w/api.php" +
                    "?action=wbsearchentities&search=$encoded&language=$language&uselang=$language" +
                    "&type=item&limit=7&format=json&origin=*"
                val root = runCatching { JSONObject(http(endpoint)) }.getOrNull() ?: return@forEach
                val results = root.optJSONArray("search") ?: JSONArray()
                val ids = buildList {
                    for (index in 0 until results.length()) {
                        results.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                if (ids.isNotEmpty()) return ids
            }
        }
        return emptyList()
    }

    private fun wikidataEntities(ids: List<String>): List<JSONObject> {
        val endpoint = "https://www.wikidata.org/w/api.php" +
            "?action=wbgetentities&ids=${ids.take(8).joinToString("%7C")}" +
            "&props=claims%7Clabels&languages=de%7Cen%7Cfr%7Cca%7Ces" +
            "&format=json&origin=*"
        val entities = JSONObject(http(endpoint)).optJSONObject("entities") ?: JSONObject()
        return ids.mapNotNull { entities.optJSONObject(it) }
    }

    private fun entityCandidate(entity: JSONObject, identity: String, place: TravelPlace): Pair<Int, JSONObject>? {
        val claims = entity.optJSONObject("claims") ?: return null
        if (claimStrings(claims, "P18").isEmpty() && claimStrings(claims, "P373").isEmpty()) return null
        val coordinates = claims.optJSONArray("P625") ?: return null
        var closest = Double.MAX_VALUE
        for (index in 0 until coordinates.length()) {
            val value = coordinates.optJSONObject(index)
                ?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")
                ?.optJSONObject("value") ?: continue
            val lat = value.optDouble("latitude", Double.NaN)
            val lon = value.optDouble("longitude", Double.NaN)
            if (lat.isFinite() && lon.isFinite()) {
                closest = minOf(closest, Geo.distanceM(place.point, GeoPoint(lat, lon)))
            }
        }
        val maximum = when (place.kind) {
            PlaceKind.NATURE -> 30_000.0
            PlaceKind.SHOPPING -> 12_000.0
            else -> 18_000.0
        }
        if (!closest.isFinite() || closest > maximum) return null

        val labels = entity.optJSONObject("labels") ?: JSONObject()
        val labelText = labels.keys().asSequence()
            .mapNotNull { labels.optJSONObject(it)?.optString("value") }
            .joinToString(" ")
        val overlap = tokens(labelText).intersect(tokens(identity)).size
        val score = 260 - (closest / 120.0).toInt() + overlap * 35
        return score to entity
    }

    private fun claimStrings(claims: JSONObject, property: String): List<String> {
        val array = claims.optJSONArray(property) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)
                    ?.optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")
                    ?.optString("value")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }

    private fun commonsExactCategoryCandidates(categoryName: String, place: TravelPlace, bonus: Int): List<Candidate> {
        val category = URLEncoder.encode("Category:$categoryName", "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=categorymembers&gcmtitle=$category" +
            "&gcmnamespace=6&gcmtype=file&gcmlimit=42" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=1000" +
            "&format=json&formatversion=2&origin=*"
        return parseCommons(endpoint, place, categoryName, bonus, trustedCategory = true)
    }

    private val curatedAliases = mapOf(
        "Seine-Fahrt" to listOf("Bateaux Mouches Seine river cruise Paris"),
        "Louvre & Tuilerien" to listOf("Louvre Palace pyramid Paris", "Tuileries Garden Paris"),
        "Notre-Dame & Île de la Cité" to listOf("Notre Dame de Paris cathedral", "Ile de la Cite Paris"),
        "Montmartre & Sacré-Cœur" to listOf("Sacre Coeur basilica Montmartre", "Montmartre streets Paris"),
        "Galeries Lafayette Dachterrasse" to listOf("Galeries Lafayette Paris rooftop terrace"),
        "Strand & Promenade Canet" to listOf("Canet Plage beach promenade France", "Plage de Canet-en-Roussillon"),
        "Fischerdorf & Étang" to listOf("Village de pecheurs etang de Canet Saint Nazaire"),
        "Banyuls & Biodiversarium" to listOf("Biodiversarium Banyuls sur Mer", "Banyuls Sur Mer coast"),
        "Camí de les Pardines & Engolasters" to listOf("Cami de les Pardines Andorra", "Lake Engolasters Andorra"),
        "Tristaina-Seen & Solar-Aussichtspunkt" to listOf("Mirador Solar de Tristaina", "Estanys de Tristaina Andorra"),
        "Montjuïc, Seilbahn & Burg" to listOf("Montjuic cable car Barcelona", "Montjuic Castle Barcelona"),
        "Gotisches Viertel & Kathedrale" to listOf("Barcelona Cathedral Gothic Quarter", "Barri Gòtic Barcelona"),
        "Parc de la Ciutadella & Arc de Triomf" to listOf("Parc de la Ciutadella Barcelona", "Arc de Triomf Barcelona"),
        "Perpignan Altstadt & Castillet" to listOf("Le Castillet Perpignan", "Historic centre Perpignan"),
        "Port-Vendres & Cap Béar" to listOf("Port-Vendres harbour", "Cap Béar lighthouse"),
        "Carrefour Claira / Salanca" to listOf("Centre Commercial Salanca Claira"),
        "Barceloneta & Strandpromenade" to listOf("Barceloneta beach promenade"),
        "Westfield Glòries" to listOf("Centre Comercial Glòries Barcelona"),
        "Diagonal Mar" to listOf("Centre Comercial Diagonal Mar"),
        "Mon(t) Magic Canillo" to listOf("Mon Magic Family Park Canillo", "Grandvalira Canillo summer"),
        "Naturpark Sorteny" to listOf("Parc Natural de la Vall de Sorteny"),
        "Tibetische Brücke Canillo" to listOf("Pont Tibetà Canillo"),
        "Incles-Tal" to listOf("Vall d'Incles Andorra"),
        "Automobilmuseum Encamp" to listOf("Museu Nacional de l'Automòbil Encamp"),
        "Altstadt Andorra la Vella" to listOf("Barri Antic Andorra la Vella", "Casa de la Vall Andorra"),
        "Pyrénées Andorra" to listOf("Grans Magatzems Pyrénées Andorra"),
        "Estanys de Juclà" to listOf("Estanys de Juclar Andorra"),
        "Eiffelturm & Trocadéro" to listOf("Eiffel Tower from Trocadéro", "Place du Trocadéro"),
        "Arc de Triomphe" to listOf("Arc de Triomphe de l'Étoile"),
        "Cité des Sciences" to listOf("Cité des sciences et de l'industrie Paris"),
        "Grande Galerie de l’Évolution" to listOf("Grande galerie de l'Évolution interior"),
        "Aquarium de Paris" to listOf("Aquarium de Paris Cinéaqua"),
        "Musée de l’Air et de l’Espace" to listOf("Musée de l'Air et de l'Espace Le Bourget"),
        "Westfield Les 4 Temps" to listOf("Les Quatre Temps La Défense"),
        "Ménagerie im Jardin des Plantes" to listOf("Ménagerie du Jardin des plantes Paris")
    )

    private fun wikipediaLanguages(region: TravelRegion): List<String> = when (region) {
        TravelRegion.CANET -> listOf("fr", "en", "ca")
        TravelRegion.BARCELONA -> listOf("ca", "es", "en")
        TravelRegion.ANDORRA -> listOf("ca", "es", "fr", "en")
        TravelRegion.PARIS -> listOf("fr", "en")
    }

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

    private fun commonsCategoryCandidates(query: String, place: TravelPlace): List<Candidate> {
        // A broad brand category (for example every Lidl worldwide) is worse than
        // a neutral placeholder for shopping destinations.
        if (place.kind == PlaceKind.SHOPPING) return emptyList()

        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchEndpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=14&gsrlimit=8" +
            "&gsrsearch=$encoded&prop=categoryinfo&format=json&formatversion=2&origin=*"
        val root = JSONObject(http(searchEndpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        val categories = buildList {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val title = page.optString("title")
                val fileCount = page.optJSONObject("categoryinfo")?.optInt("files", 0) ?: 0
                if (fileCount <= 0) continue
                val lowerTitle = normalize(title)
                if (listOf("metrostation", "railway station", "gare de", "war memorial", " aoc").any(lowerTitle::contains)) {
                    continue
                }
                val score = relevance(title, place, query)
                val categoryTokens = tokens(title)
                val wantedTokens = wantedTokens(place)
                val overlap = categoryTokens.intersect(wantedTokens).size
                val harmlessCategoryWords = setOf(
                    "category", "interior", "interiors", "exterior", "exteriors",
                    "views", "details", "architecture", "buildings", "gardens", "night"
                )
                val extraIdentityWords = categoryTokens - wantedTokens - harmlessCategoryWords
                val precisionScore = score + (fileCount / 8).coerceAtMost(10) - extraIdentityWords.size * 24
                if (score >= 28 && overlap > 0) add(title to precisionScore)
            }
        }.sortedByDescending { it.second }.take(2)

        return categories.flatMap { (categoryTitle, score) ->
            val category = URLEncoder.encode(categoryTitle, "UTF-8")
            val endpoint = "https://commons.wikimedia.org/w/api.php" +
                "?action=query&generator=categorymembers&gcmtitle=$category" +
                "&gcmnamespace=6&gcmtype=file&gcmlimit=36" +
                "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=900" +
                "&format=json&formatversion=2&origin=*"
            runCatching {
                parseCommons(endpoint, place, query, score + 20, trustedCategory = true)
            }.getOrDefault(emptyList())
        }
    }

    private fun commonsGeoCandidates(place: TravelPlace): List<Candidate> {
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=geosearch&ggsprimary=all&ggsnamespace=6" +
            "&ggsradius=1600&ggslimit=24&ggscoord=${place.point.lat}%7C${place.point.lon}" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=900" +
            "&format=json&formatversion=2&origin=*"
        return parseCommons(endpoint, place, place.imageQuery, 9)
    }

    private fun parseCommons(
        endpoint: String,
        place: TravelPlace,
        query: String,
        bonus: Int,
        trustedCategory: Boolean = false
    ): List<Candidate> {
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
                add(
                    Candidate(
                        label = label,
                        url = url,
                        score = relevance(label, place, query) + bonus,
                        trustedCategory = trustedCategory
                    )
                )
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

    internal fun exactQueriesForTest(place: TravelPlace): List<String> = exactQueries(place)

    private fun cacheKey(place: TravelPlace): String =
        sha256("v46-wikidata|${place.region.name}|${place.title}|${DestinationMediaCatalog.identities(place)}")

    private fun existingFiles(context: Context, key: String, limit: Int): List<String> =
        (0 until limit).mapNotNull { index ->
            imageFile(context, key, index).takeIf { it.isFile && it.length() > 4_096L }
                ?.let { Uri.fromFile(it).toString() }
        }

    private fun imageFile(context: Context, key: String, index: Int): File {
        val directory = File(context.cacheDir, "travel_photos_v46").apply { mkdirs() }
        return File(directory, "$key-$index.image")
    }

    private fun sourceUrls(context: Context, key: String): Set<String> {
        val prefs = context.getSharedPreferences("travel_images_v46", Context.MODE_PRIVATE)
        return prefs.getString("sources_$key", "").orEmpty().lineSequence().filter(String::isNotBlank).toSet()
    }

    private fun saveSourceUrls(context: Context, key: String, sources: Set<String>) {
        context.getSharedPreferences("travel_images_v46", Context.MODE_PRIVATE)
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
            connection.setRequestProperty("User-Agent", "ReisePilot/4.7 Android family travel app")
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
        var lastCode = 0
        repeat(3) { attempt ->
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 6_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
                connection.setRequestProperty("User-Agent", "ReisePilot/4.7 Android family travel app")
                val code = connection.responseCode
                lastCode = code
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299) return body
                if (code !in setOf(429, 500, 502, 503, 504) || attempt == 2) {
                    error("Wikimedia HTTP $code")
                }
                val retrySeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                Thread.sleep((retrySeconds?.times(1_000L) ?: (750L * (attempt + 1))).coerceAtMost(4_000L))
            } finally {
                connection.disconnect()
            }
        }
        error("Wikimedia HTTP $lastCode")
    }
}
