package de.balabucha.reisepilot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.ceil

enum class ParkingKind(val label: String) {
    UNDERGROUND("Tiefgarage"),
    MULTI_STOREY("Parkhaus"),
    PARK_RIDE("Park & Ride"),
    SURFACE("Parkplatz"),
    UNKNOWN("Parkplatz")
}

enum class ParkingFee(val label: String) {
    FREE("kostenlos gemeldet"),
    PAID("gebührenpflichtig"),
    UNKNOWN("Gebühr nicht gemeldet")
}

data class ParkingSpot(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val kind: ParkingKind,
    val distanceToDestinationM: Int,
    val fee: ParkingFee = ParkingFee.UNKNOWN,
    val capacity: Int? = null,
    val openingHours: String = "",
    val access: String = "",
    val operator: String = "",
    val maxHeight: String = "",
    val supervised: Boolean? = null,
    val recommended: Boolean = false,
    val note: String = "",
    val source: String = "OpenStreetMap"
) {
    val walkingMinutes: Int
        get() = ceil(distanceToDestinationM.coerceAtLeast(1) / 75.0).toInt().coerceAtLeast(1)

    fun json(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("point", point.json())
        .put("kind", kind.name)
        .put("distanceToDestinationM", distanceToDestinationM)
        .put("fee", fee.name)
        .put("capacity", capacity ?: JSONObject.NULL)
        .put("openingHours", openingHours)
        .put("access", access)
        .put("operator", operator)
        .put("maxHeight", maxHeight)
        .put("supervised", supervised ?: JSONObject.NULL)
        .put("recommended", recommended)
        .put("note", note)
        .put("source", source)

    companion object {
        fun fromJson(json: JSONObject): ParkingSpot = ParkingSpot(
            id = json.getString("id"),
            name = json.optString("name", "Parkplatz"),
            point = GeoPoint.fromJson(json.getJSONObject("point")),
            kind = runCatching { ParkingKind.valueOf(json.optString("kind")) }
                .getOrDefault(ParkingKind.UNKNOWN),
            distanceToDestinationM = json.optInt("distanceToDestinationM"),
            fee = runCatching { ParkingFee.valueOf(json.optString("fee")) }
                .getOrDefault(ParkingFee.UNKNOWN),
            capacity = if (json.isNull("capacity")) null else json.optInt("capacity"),
            openingHours = json.optString("openingHours"),
            access = json.optString("access"),
            operator = json.optString("operator"),
            maxHeight = json.optString("maxHeight"),
            supervised = if (json.isNull("supervised")) null else json.optBoolean("supervised"),
            recommended = json.optBoolean("recommended"),
            note = json.optString("note"),
            source = json.optString("source", "OpenStreetMap")
        )
    }
}

data class ParkingSearchResult(
    val spots: List<ParkingSpot>,
    val updatedAt: Long,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val message: String = ""
)

data class SavedParking(
    val placeKey: String,
    val placeTitle: String,
    val spot: ParkingSpot
) {
    fun json(): JSONObject = JSONObject()
        .put("placeKey", placeKey)
        .put("placeTitle", placeTitle)
        .put("spot", spot.json())

    companion object {
        fun fromJson(json: JSONObject) = SavedParking(
            placeKey = json.getString("placeKey"),
            placeTitle = json.optString("placeTitle", "Ziel"),
            spot = ParkingSpot.fromJson(json.getJSONObject("spot"))
        )
    }
}

object ParkingCatalog {
    private val centralBarcelona = setOf(
        "Gotisches Viertel & Kathedrale",
        "L’Aquàrium Barcelona",
        "Barceloneta & Strandpromenade",
        "Mercat de la Boqueria",
        "Maremagnum",
        "Museu Marítim",
        "Schokoladenmuseum"
    )

    fun recommendations(place: TravelPlace): List<ParkingSpot> = buildList {
        if (place.title in centralBarcelona) {
            add(
                curated(
                    id = "curated:bsm-moll-de-la-fusta",
                    name = "BSM Moll de la Fusta",
                    point = GeoPoint(41.3805997, 2.1820121, "BSM Moll de la Fusta", "parking"),
                    kind = ParkingKind.UNDERGROUND,
                    fee = ParkingFee.PAID,
                    capacity = 215,
                    openingHours = "24/7",
                    maxHeight = "1.95",
                    supervised = true,
                    note = "Empfohlener zentraler Ausgangspunkt: Auto einmal abstellen, Hafen und Altstadt zu Fuß erreichen.",
                    place = place
                )
            )
        }
        if (place.title == "Collioure") {
            add(
                curated(
                    id = "curated:collioure-cap-dourats",
                    name = "Parking du Cap Dourats",
                    point = GeoPoint(42.5261364, 3.0689640, "Parking du Cap Dourats", "parking"),
                    kind = ParkingKind.SURFACE,
                    fee = ParkingFee.PAID,
                    capacity = 230,
                    note = "Außerhalb des engen Zentrums; in der Hauptsaison vernünftiger als die kleinen Altstadtparkplätze.",
                    place = place
                )
            )
        }
    }

    fun advice(place: TravelPlace): String = when (place.region) {
        TravelRegion.BARCELONA -> "Parkhaus vor Straßenparken. Wertsachen nicht sichtbar im Auto lassen."
        TravelRegion.PARIS -> "Für Paris bleibt die beste Lösung: Auto am Hotel lassen und Metro nutzen. Die Suche ist nur als Reserve gedacht."
        TravelRegion.ANDORRA -> "Bei Bergzielen Zufahrt, Saisonregelung und letzten Rückweg am Besuchstag prüfen."
        TravelRegion.CANET -> "An Strand- und Küstenzielen früh ankommen; Belegung und Saisonpreise können sich kurzfristig ändern."
    }

    private fun curated(
        id: String,
        name: String,
        point: GeoPoint,
        kind: ParkingKind,
        fee: ParkingFee,
        capacity: Int? = null,
        openingHours: String = "",
        maxHeight: String = "",
        supervised: Boolean? = null,
        note: String,
        place: TravelPlace
    ) = ParkingSpot(
        id = id,
        name = name,
        point = point,
        kind = kind,
        distanceToDestinationM = Geo.distanceM(point, place.point).toInt(),
        fee = fee,
        capacity = capacity,
        openingHours = openingHours,
        maxHeight = maxHeight,
        supervised = supervised,
        recommended = true,
        note = note,
        source = "kuratierte OSM-Angaben"
    )
}

object ParkingLogic {
    fun parseOverpass(raw: String, place: TravelPlace): List<ParkingSpot> {
        val elements = JSONObject(raw).optJSONArray("elements") ?: JSONArray()
        return buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: JSONObject()
                val access = tags.optString("access").lowercase(Locale.ROOT)
                val motorVehicle = tags.optString("motor_vehicle").lowercase(Locale.ROOT)
                if (access in setOf("private", "no") || motorVehicle == "no") continue

                val parkingValue = tags.optString("parking").lowercase(Locale.ROOT)
                if (parkingValue in setOf("lane", "street_side", "on_kerb", "half_on_kerb", "shoulder")) continue

                val center = element.optJSONObject("center")
                val lat = if (element.has("lat")) element.optDouble("lat") else center?.optDouble("lat")
                val lon = if (element.has("lon")) element.optDouble("lon") else center?.optDouble("lon")
                if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) continue
                val point = GeoPoint(lat, lon, tags.optString("name"), "parking")
                val distance = Geo.distanceM(point, place.point).toInt()
                val operator = tags.optString("operator")
                val rawName = tags.optString("name").ifBlank { operator }
                val kind = kind(tags)
                val customerOnly = access in setOf("customers", "customer")
                if (customerOnly && !customerParkingFits(place, rawName, operator)) continue

                val type = element.optString("type", "osm")
                val id = element.optLong("id", index.toLong())
                add(
                    ParkingSpot(
                        id = "osm:$type:$id",
                        name = rawName.ifBlank { kind.label },
                        point = point,
                        kind = kind,
                        distanceToDestinationM = distance,
                        fee = when (tags.optString("fee").lowercase(Locale.ROOT)) {
                            "yes" -> ParkingFee.PAID
                            "no" -> ParkingFee.FREE
                            else -> ParkingFee.UNKNOWN
                        },
                        capacity = tags.optString("capacity").toIntOrNull(),
                        openingHours = tags.optString("opening_hours"),
                        access = access,
                        operator = operator,
                        maxHeight = tags.optString("maxheight"),
                        supervised = when (tags.optString("supervised").lowercase(Locale.ROOT)) {
                            "yes" -> true
                            "no" -> false
                            else -> null
                        }
                    )
                )
            }
        }
    }

    fun rank(place: TravelPlace, candidates: List<ParkingSpot>): List<ParkingSpot> {
        val preferredGarage = place.region in setOf(TravelRegion.BARCELONA, TravelRegion.PARIS)
        return candidates.asSequence()
            .filter { it.distanceToDestinationM <= searchRadiusM(place) + 250 }
            .distinctBy {
                "${(it.point.lat * 10_000).toInt()}:${(it.point.lon * 10_000).toInt()}:${normalize(it.name)}"
            }
            .sortedByDescending { spot ->
                var score = 220 - spot.distanceToDestinationM / 12
                if (spot.recommended) score += 1_000
                if (spot.name != spot.kind.label) score += 28
                if (spot.supervised == true) score += 45
                if (spot.capacity != null) score += 12
                if (spot.openingHours == "24/7") score += 10
                if (spot.kind == ParkingKind.PARK_RIDE) score += 24
                if (preferredGarage && spot.kind in setOf(ParkingKind.UNDERGROUND, ParkingKind.MULTI_STOREY)) score += 55
                if (place.kind == PlaceKind.SHOPPING && spot.access in setOf("customers", "customer")) score += 45
                score
            }
            .take(6)
            .toList()
    }

    fun searchRadiusM(place: TravelPlace): Int = when {
        place.region in setOf(TravelRegion.BARCELONA, TravelRegion.PARIS) -> 1_100
        place.kind == PlaceKind.NATURE -> 2_400
        else -> 1_700
    }

    private fun kind(tags: JSONObject): ParkingKind {
        if (tags.optString("park_ride").lowercase(Locale.ROOT) in setOf("yes", "train", "bus", "subway")) {
            return ParkingKind.PARK_RIDE
        }
        return when (tags.optString("parking").lowercase(Locale.ROOT)) {
            "underground" -> ParkingKind.UNDERGROUND
            "multi-storey", "multistorey" -> ParkingKind.MULTI_STOREY
            "surface" -> ParkingKind.SURFACE
            else -> ParkingKind.UNKNOWN
        }
    }

    private fun customerParkingFits(place: TravelPlace, name: String, operator: String): Boolean {
        if (place.kind == PlaceKind.SHOPPING) return true
        val placeTokens = tokens(place.title) + tokens(place.imageQuery)
        return tokens("$name $operator").intersect(placeTokens).isNotEmpty()
    }

    private fun tokens(value: String): Set<String> = normalize(value)
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(' ')
        .filter { it.length >= 4 }
        .toSet()

    private fun normalize(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
}

object ParkingResolver {
    private const val PREFS = "destination_parking_v1"
    private const val CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    private val mutex = Mutex()

    @Volatile
    internal var debugOverride: ((TravelPlace) -> ParkingSearchResult)? = null

    suspend fun nearby(context: Context, place: TravelPlace, forceRefresh: Boolean = false): ParkingSearchResult =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) debugOverride?.invoke(place)?.let { return@withContext it }
            mutex.withLock {
                val cached = readCache(context.applicationContext, place)
                val now = System.currentTimeMillis()
                if (!forceRefresh && cached != null && now - cached.updatedAt <= CACHE_MAX_AGE_MS) {
                    return@withLock combine(place, cached.copy(fromCache = true))
                }

                val fetched = runCatching { fetch(place) }
                fetched.fold(
                    onSuccess = { spots ->
                        val result = ParkingSearchResult(spots, now)
                        writeCache(context.applicationContext, place, result)
                        combine(place, result)
                    },
                    onFailure = { failure ->
                        if (cached != null) {
                            combine(
                                place,
                                cached.copy(
                                    fromCache = true,
                                    stale = true,
                                    message = "Live-Aktualisierung nicht erreichbar · letzter gespeicherter Stand"
                                )
                            )
                        } else {
                            combine(
                                place,
                                ParkingSearchResult(
                                    spots = emptyList(),
                                    updatedAt = now,
                                    message = failure.message?.take(120) ?: "Parkplätze konnten nicht geladen werden"
                                )
                            )
                        }
                    }
                )
            }
        }

    private fun combine(place: TravelPlace, live: ParkingSearchResult): ParkingSearchResult = live.copy(
        spots = ParkingLogic.rank(place, ParkingCatalog.recommendations(place) + live.spots)
    )

    private fun fetch(place: TravelPlace): List<ParkingSpot> {
        val radius = ParkingLogic.searchRadiusM(place)
        val query = "[out:json][timeout:16];" +
            "nwr(around:$radius,${place.point.lat},${place.point.lon})[\"amenity\"=\"parking\"];" +
            "out center tags 80;"
        val failures = mutableListOf<String>()
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        endpoints.forEach { endpoint ->
            runCatching { request(endpoint, query) }
                .onSuccess { return ParkingLogic.parseOverpass(it, place) }
                .onFailure { failures += it.message.orEmpty() }
        }
        error(failures.firstOrNull { it.isNotBlank() } ?: "OpenStreetMap-Parkdaten nicht erreichbar")
    }

    private fun request(endpoint: String, query: String): String {
        val body = "data=" + URLEncoder.encode(query, "UTF-8")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 7_000
            connection.readTimeout = 11_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.7 Android family travel app")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("OpenStreetMap HTTP $code")
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheKey(place: TravelPlace) = "${place.region.name}:${place.title}"

    private fun readCache(context: Context, place: TravelPlace): ParkingSearchResult? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(cacheKey(place), null) ?: return null
        val json = JSONObject(raw)
        val array = json.optJSONArray("spots") ?: JSONArray()
        ParkingSearchResult(
            spots = buildList {
                for (index in 0 until array.length()) add(ParkingSpot.fromJson(array.getJSONObject(index)))
            },
            updatedAt = json.optLong("updatedAt")
        )
    }.getOrNull()

    private fun writeCache(context: Context, place: TravelPlace, result: ParkingSearchResult) {
        val spots = JSONArray().apply { result.spots.forEach { put(it.json()) } }
        val json = JSONObject().put("updatedAt", result.updatedAt).put("spots", spots)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(cacheKey(place), json.toString()).apply()
    }
}

object ParkingSelectionStore {
    private const val PREFS = "selected_parking_v1"
    private const val KEY = "selected"

    fun placeKey(place: TravelPlace) = "${place.region.name}:${place.title}"

    fun all(context: Context): List<SavedParking> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) add(SavedParking.fromJson(array.getJSONObject(index)))
        }
    }.getOrDefault(emptyList())

    fun selectedFor(context: Context, place: TravelPlace): SavedParking? =
        all(context).firstOrNull { it.placeKey == placeKey(place) }

    fun save(context: Context, place: TravelPlace, spot: ParkingSpot) {
        val key = placeKey(place)
        val updated = (all(context).filterNot { it.placeKey == key } + SavedParking(key, place.title, spot))
            .takeLast(12)
        write(context, updated)
    }

    fun remove(context: Context, place: TravelPlace) {
        write(context, all(context).filterNot { it.placeKey == placeKey(place) })
    }

    private fun write(context: Context, values: List<SavedParking>) {
        val array = JSONArray().apply { values.forEach { put(it.json()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }
}
