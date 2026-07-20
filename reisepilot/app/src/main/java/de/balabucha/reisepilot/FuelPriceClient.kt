package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import kotlin.math.max

internal enum class FuelCountry { GERMANY, FRANCE, SPAIN, ANDORRA }

internal object CountryResolver {
    private val andorra = listOf(
        GeoPoint(42.43, 1.40), GeoPoint(42.66, 1.40), GeoPoint(42.66, 1.80), GeoPoint(42.43, 1.80)
    )
    private val germany = listOf(
        GeoPoint(47.20, 5.75), GeoPoint(47.30, 10.50), GeoPoint(47.55, 13.90),
        GeoPoint(50.00, 14.95), GeoPoint(51.10, 15.05), GeoPoint(53.55, 14.55),
        GeoPoint(54.95, 10.00), GeoPoint(54.75, 8.30), GeoPoint(53.60, 6.70),
        GeoPoint(51.00, 5.75), GeoPoint(48.70, 7.55)
    )
    private val spain = listOf(
        GeoPoint(43.80, -9.50), GeoPoint(43.45, -1.70), GeoPoint(42.85, 0.70),
        GeoPoint(42.42, 3.30), GeoPoint(36.00, -5.20), GeoPoint(36.00, -7.50),
        GeoPoint(41.90, -9.50)
    )
    private val france = listOf(
        GeoPoint(51.15, 2.40), GeoPoint(49.70, -1.95), GeoPoint(48.45, -4.85),
        GeoPoint(43.00, -1.80), GeoPoint(42.30, 3.25), GeoPoint(43.70, 7.60),
        GeoPoint(46.30, 6.85), GeoPoint(48.70, 7.75), GeoPoint(49.20, 6.10)
    )

    fun country(point: GeoPoint): FuelCountry = when {
        inside(point, andorra) -> FuelCountry.ANDORRA
        inside(point, germany) -> FuelCountry.GERMANY
        inside(point, spain) -> FuelCountry.SPAIN
        inside(point, france) -> FuelCountry.FRANCE
        else -> FuelCountry.FRANCE
    }

    private fun inside(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val crosses = (current.lat > point.lat) != (previous.lat > point.lat) &&
                point.lon < (previous.lon - current.lon) * (point.lat - current.lat) /
                ((previous.lat - current.lat).takeIf { kotlin.math.abs(it) > 1e-12 } ?: 1e-12) + current.lon
            if (crosses) inside = !inside
            previous = current
        }
        return inside
    }
}

internal object MotorwayFilter {
    fun isMotorwayService(name: String, address: String): Boolean {
        val text = "$name $address".uppercase()
        if ("AUTOHOF" in text) return false
        return listOf(
            "RASTSTÄTTE", "RASTSTAETTE", "RASTANLAGE", "TANK & RAST", "TANK UND RAST",
            "AUTOBAHNTANKSTELLE", "MOTORWAY SERVICE", "SERVICE AREA", "AUTOPISTA",
            "ÁREA DE SERVICIO", "AREA DE SERVICIO"
        ).any(text::contains)
    }
}

internal data class FuelCandidate(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val dieselPrice: Double?,
    val motorway: Boolean,
    val source: String,
    val updatedAt: String = ""
)

internal data class FuelRouteMetrics(
    val detourKm: Double,
    val distanceAheadKm: Double
)

internal object FuelRanking {
    private fun key(point: GeoPoint) = "%.6f,%.6f".format(java.util.Locale.US, point.lat, point.lon)

    fun shortlist(
        current: GeoPoint,
        destination: GeoPoint,
        route: List<GeoPoint>,
        candidates: List<FuelCandidate>,
        limit: Int = 4
    ): List<FuelCandidate> {
        val currentToDestination = Geo.distanceM(current, destination) / 1000.0
        return candidates.asSequence()
            .filterNot { it.motorway }
            .mapNotNull { station ->
                val routeAhead = Geo.distanceAheadOnRouteM(
                    current, station.point, route,
                    maxCurrentOffsetM = 8_000.0,
                    maxTargetOffsetM = 8_000.0,
                    includeTargetOffset = true
                )?.div(1000.0) ?: return@mapNotNull null
                if (routeAhead !in 0.3..85.0) return@mapNotNull null
                val stationToDestination = Geo.distanceM(station.point, destination) / 1000.0
                if (stationToDestination > currentToDestination + 18.0) return@mapNotNull null
                val offset = Geo.closestToPolylineM(station.point, route) / 1000.0
                val heuristic = (station.dieselPrice ?: 9.0) * 35.0 + offset * 0.7
                station to heuristic
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun best(
        current: GeoPoint,
        destination: GeoPoint,
        route: List<GeoPoint>,
        candidates: List<FuelCandidate>,
        consumption: Double,
        plannedLitres: Double = 35.0,
        exactMetrics: Map<String, FuelRouteMetrics> = emptyMap()
    ): FuelSuggestion? {
        val currentToDestination = Geo.distanceM(current, destination) / 1000.0
        val usable = candidates.mapNotNull { station ->
            if (station.motorway) return@mapNotNull null
            val exact = exactMetrics[key(station.point)]
            val routeAhead = exact?.distanceAheadKm ?: Geo.distanceAheadOnRouteM(
                current, station.point, route,
                maxCurrentOffsetM = 8_000.0,
                maxTargetOffsetM = 8_000.0,
                includeTargetOffset = true
            )?.div(1000.0) ?: return@mapNotNull null
            if (routeAhead !in 0.3..85.0) return@mapNotNull null

            val stationToDestination = Geo.distanceM(station.point, destination) / 1000.0
            if (stationToDestination > currentToDestination + 18.0) return@mapNotNull null
            val routeOffsetKm = Geo.closestToPolylineM(station.point, route) / 1000.0
            if (routeOffsetKm > 8.0) return@mapNotNull null

            val detourKm = exact?.detourKm ?: max(0.4, routeOffsetKm * 2.25)
            if (detourKm > 18.0) return@mapNotNull null
            val price = station.dieselPrice
            val detourFuelCost = if (price != null) detourKm * consumption / 100.0 * price else 0.0
            val timePenalty = detourKm * 0.30
            val score = if (price != null) price * plannedLitres + detourFuelCost + timePenalty
                else 1_000.0 + detourKm

            Triple(station, FuelSuggestion(
                name = station.name,
                address = station.address,
                point = station.point,
                pricePerLitre = price,
                detourKm = detourKm,
                distanceAheadKm = routeAhead,
                source = if (exact != null) "${station.source} · Fahrumweg geprüft" else station.source,
                updatedAt = station.updatedAt
            ), score)
        }

        val pricedAvailable = usable.any { it.first.dieselPrice != null }
        return usable
            .filter { !pricedAvailable || it.first.dieselPrice != null }
            .minByOrNull { it.third }
            ?.second
    }

    fun metricKey(point: GeoPoint): String = key(point)
}

internal object FuelPriceClient {
    fun query(
        current: GeoPoint,
        route: List<GeoPoint>,
        destination: GeoPoint,
        consumption: Double,
        tankerKoenigKey: String,
        mapboxToken: String = "",
        baseRouteDistanceM: Int? = null
    ): FuelSuggestion? {
        val candidates = when (CountryResolver.country(current)) {
            FuelCountry.ANDORRA -> andorraFallback()
            FuelCountry.SPAIN -> querySpain(current)
            FuelCountry.GERMANY -> queryGermany(current, tankerKoenigKey)
            FuelCountry.FRANCE -> queryFrance(current)
        }
        val exact = mutableMapOf<String, FuelRouteMetrics>()
        if (mapboxTokenLooksValid(mapboxToken) && baseRouteDistanceM != null && baseRouteDistanceM > 0) {
            FuelRanking.shortlist(current, destination, route, candidates).forEach { station ->
                runCatching {
                    MapboxClient.detourRoute(mapboxToken, current, station.point, destination)
                }.getOrNull()?.let { detour ->
                    exact[FuelRanking.metricKey(station.point)] = FuelRouteMetrics(
                        detourKm = ((detour.totalDistanceM - baseRouteDistanceM).coerceAtLeast(0)) / 1000.0,
                        distanceAheadKm = detour.distanceToStopM / 1000.0
                    )
                }
            }
        }
        return FuelRanking.best(current, destination, route, candidates, consumption, exactMetrics = exact)
    }

    private fun queryFrance(current: GeoPoint): List<FuelCandidate> {
        val where = "within_distance(geom, geom'POINT(${current.lon} ${current.lat})', '40km') " +
            "and pop='R' and gazole_prix is not null"
        val endpoint = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
            "prix-des-carburants-en-france-flux-instantane-v2/records" +
            "?where=${URLEncoder.encode(where, "UTF-8")}" +
            "&order_by=${URLEncoder.encode("gazole_prix asc", "UTF-8")}&limit=100"
        val root = JSONObject(http(endpoint))
        val results = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until results.length()) {
                val row = results.optJSONObject(i) ?: continue
                val geom = row.optJSONObject("geom") ?: continue
                val lat = geom.optDouble("lat", Double.NaN)
                val lon = geom.optDouble("lon", Double.NaN)
                val price = row.optDouble("gazole_prix", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || !price.isFinite()) continue
                val town = row.optString("ville").trim()
                val address = row.optString("adresse").trim()
                add(FuelCandidate(
                    name = if (town.isNotBlank()) "Tankstelle $town" else "Tankstelle Frankreich",
                    address = listOf(address, town).filter { it.isNotBlank() }.joinToString(", "),
                    point = GeoPoint(lat, lon, town, "fuel"),
                    dieselPrice = price,
                    motorway = row.optString("pop") == "A",
                    source = "Frankreich · offizieller Preis",
                    updatedAt = row.optString("gazole_maj")
                ))
            }
        }
    }

    private fun queryGermany(current: GeoPoint, key: String): List<FuelCandidate> {
        val clean = key.trim()
        if (clean.length < 30) return emptyList()
        val endpoint = "https://creativecommons.tankerkoenig.de/json/list.php" +
            "?lat=${current.lat}&lng=${current.lon}&rad=25&type=diesel&sort=price" +
            "&apikey=${URLEncoder.encode(clean, "UTF-8")}"
        val root = JSONObject(http(endpoint))
        if (!root.optBoolean("ok")) return emptyList()
        val stations = root.optJSONArray("stations") ?: JSONArray()
        return buildList {
            for (i in 0 until stations.length()) {
                val row = stations.optJSONObject(i) ?: continue
                val price = row.optDouble("diesel", Double.NaN)
                val lat = row.optDouble("lat", Double.NaN)
                val lon = row.optDouble("lng", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || !price.isFinite()) continue
                val name = row.optString("name").ifBlank { row.optString("brand", "Tankstelle") }
                val address = listOf(row.optString("street"), row.optString("houseNumber"), row.optString("place"))
                    .filter { it.isNotBlank() }.joinToString(" ")
                add(FuelCandidate(
                    name = name,
                    address = address,
                    point = GeoPoint(lat, lon, name, "fuel"),
                    dieselPrice = price,
                    motorway = MotorwayFilter.isMotorwayService(name, address),
                    source = "Deutschland · Tankerkönig",
                    updatedAt = LocalDateTime.now().withNano(0).toString()
                ))
            }
        }
    }

    private fun querySpain(current: GeoPoint): List<FuelCandidate> {
        val endpoint = "https://energia.serviciosmin.gob.es/ServiciosRestCarburantes/" +
            "PreciosCarburantes/EstacionesTerrestres/"
        val root = JSONObject(http(endpoint))
        val date = root.optString("Fecha")
        val stations = root.optJSONArray("ListaEESSPrecio") ?: JSONArray()
        return buildList {
            for (i in 0 until stations.length()) {
                val row = stations.optJSONObject(i) ?: continue
                val lat = decimal(row.optString("Latitud")) ?: continue
                val lon = decimal(row.optString("Longitud (WGS84)")) ?: continue
                if (Geo.distanceM(current, GeoPoint(lat, lon)) > 45_000) continue
                val price = decimal(row.optString("Precio Gasoleo A")) ?: continue
                val brand = row.optString("Rótulo").ifBlank { "Tankstelle Spanien" }
                val address = listOf(row.optString("Dirección"), row.optString("Municipio"))
                    .filter { it.isNotBlank() }.joinToString(", ")
                add(FuelCandidate(
                    name = brand,
                    address = address,
                    point = GeoPoint(lat, lon, brand, "fuel"),
                    dieselPrice = price,
                    motorway = MotorwayFilter.isMotorwayService(brand, address),
                    source = "Spanien · offizieller Preis",
                    updatedAt = date
                ))
            }
        }
    }

    private fun andorraFallback(): List<FuelCandidate> = listOf(
        FuelCandidate("Tankstelle Santa Coloma", "Avinguda d'Enclar, Santa Coloma", GeoPoint(42.4955, 1.4976, "Santa Coloma", "fuel"), null, false, "Andorra · Preis vor Ort"),
        FuelCandidate("Tankstelle Andorra la Vella", "Avinguda de Tarragona, Andorra la Vella", GeoPoint(42.5038, 1.5138, "Andorra la Vella", "fuel"), null, false, "Andorra · Preis vor Ort"),
        FuelCandidate("Tankstelle Encamp", "Avinguda de Joan Martí, Encamp", GeoPoint(42.5351, 1.5831, "Encamp", "fuel"), null, false, "Andorra · Preis vor Ort")
    )

    private fun decimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "ReisePilot/4.1 Android")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Tankdaten HTTP $code")
        return body
    }
}
