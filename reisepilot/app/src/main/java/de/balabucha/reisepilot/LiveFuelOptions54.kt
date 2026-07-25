package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.max

internal object LiveFuelOptions54 {
    private data class Ranked(
        val candidate: FuelCandidate,
        val aheadKm: Double,
        val offsetKm: Double,
        val detourKm: Double,
        val exact: Boolean,
        val score: Double
    )

    fun query(
        current: GeoPoint,
        route: List<GeoPoint>,
        destination: GeoPoint,
        consumption: Double,
        tankerKoenigKey: String,
        mapboxToken: String,
        baseRouteDistanceM: Int?
    ): List<FuelSuggestion> {
        if (route.size < 2) return emptyList()
        val centers = buildList {
            add(current)
            RouteAheadTools54.pointAhead(current, route, 30_000.0)?.let(::add)
            RouteAheadTools54.pointAhead(current, route, 65_000.0)?.let(::add)
        }.distinctBy { "%.3f,%.3f".format(Locale.US, it.lat, it.lon) }

        val raw = when (CountryResolver.country(current)) {
            FuelCountry.GERMANY -> centers.flatMap { queryGermany(it, tankerKoenigKey) }
            FuelCountry.FRANCE -> centers.take(2).flatMap(::queryFrance)
            FuelCountry.SPAIN -> querySpain(centers)
            FuelCountry.ANDORRA -> andorraFallback()
        }.distinctBy { "%.5f,%.5f".format(Locale.US, it.point.lat, it.point.lon) }

        val rough = raw.asSequence()
            .filterNot { it.motorway }
            .mapNotNull { station ->
                val ahead = RouteAheadTools54.forwardDistanceKm(current, station.point, route, 8_000.0)
                    ?: return@mapNotNull null
                if (ahead !in 0.2..110.0) return@mapNotNull null
                val offset = Geo.closestToPolylineM(station.point, route) / 1_000.0
                if (offset > 8.0) return@mapNotNull null
                Triple(station, ahead, offset)
            }
            .sortedWith(
                compareBy<Triple<FuelCandidate, Double, Double>> { it.first.dieselPrice ?: 9.0 }
                    .thenBy { it.third }
                    .thenBy { it.second }
            )
            .take(8)
            .toList()

        val exactEnabled = mapboxTokenLooksValid(mapboxToken) && (baseRouteDistanceM ?: 0) > 0
        val ranked = rough.mapNotNull { (station, roughAhead, offset) ->
            var ahead = roughAhead
            var detour = max(0.4, offset * 2.25)
            var exact = false
            if (exactEnabled) {
                runCatching { MapboxClient.detourRoute(mapboxToken, current, station.point, destination) }
                    .getOrNull()
                    ?.let { result ->
                        val checkedDetour = (result.totalDistanceM - baseRouteDistanceM!!).coerceAtLeast(0) / 1_000.0
                        val checkedAhead = result.distanceToStopM / 1_000.0
                        if (checkedDetour > 22.0 || checkedAhead > roughAhead + 18.0) return@mapNotNull null
                        detour = checkedDetour
                        ahead = checkedAhead
                        exact = true
                    }
            }
            if (detour > 22.0) return@mapNotNull null
            val price = station.dieselPrice
            val plannedLitres = 35.0
            val priceCost = price?.times(plannedLitres) ?: 1_000.0
            val detourFuel = if (price != null) detour * consumption / 100.0 * price else 0.0
            val score = priceCost + detourFuel + detour * 0.35 + ahead * 0.008
            Ranked(station, ahead, offset, detour, exact, score)
        }.sortedBy { it.score }

        val priced = ranked.any { it.candidate.dieselPrice != null }
        return ranked.asSequence()
            .filter { !priced || it.candidate.dieselPrice != null }
            .distinctBy { it.candidate.name.lowercase(Locale.GERMANY) }
            .take(4)
            .map { result ->
                FuelSuggestion(
                    name = result.candidate.name,
                    address = result.candidate.address,
                    point = result.candidate.point,
                    pricePerLitre = result.candidate.dieselPrice,
                    detourKm = result.detourKm,
                    distanceAheadKm = result.aheadKm,
                    source = buildString {
                        append(result.candidate.source)
                        if (result.exact) append(" · Fahrtrichtung und Umweg geprüft")
                        else append(" · in Fahrtrichtung gefiltert")
                    },
                    updatedAt = result.candidate.updatedAt
                )
            }
            .toList()
    }

    private fun queryFrance(center: GeoPoint): List<FuelCandidate> {
        val where = "within_distance(geom, geom'POINT(${center.lon} ${center.lat})', '42km') " +
            "and pop='R' and gazole_prix is not null"
        val endpoint = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
            "prix-des-carburants-en-france-flux-instantane-v2/records" +
            "?where=${URLEncoder.encode(where, "UTF-8")}" +
            "&order_by=${URLEncoder.encode("gazole_prix asc", "UTF-8")}&limit=100"
        val results = JSONObject(http(endpoint)).optJSONArray("results") ?: JSONArray()
        return buildList {
            for (index in 0 until results.length()) {
                val row = results.optJSONObject(index) ?: continue
                val geom = row.optJSONObject("geom") ?: continue
                val lat = geom.optDouble("lat", Double.NaN)
                val lon = geom.optDouble("lon", Double.NaN)
                val price = row.optDouble("gazole_prix", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || !price.isFinite()) continue
                val town = row.optString("ville").trim()
                val address = row.optString("adresse").trim()
                val brand = listOf("enseigne", "marque", "nom").firstNotNullOfOrNull { key ->
                    row.optString(key).trim().takeIf(String::isNotBlank)
                }
                add(
                    FuelCandidate(
                        name = brand ?: if (town.isNotBlank()) "Tankstelle $town" else "Tankstelle Frankreich",
                        address = listOf(address, town).filter(String::isNotBlank).joinToString(", "),
                        point = GeoPoint(lat, lon, town, "fuel"),
                        dieselPrice = price,
                        motorway = row.optString("pop") == "A",
                        source = "Frankreich · offizieller Dieselpreis",
                        updatedAt = row.optString("gazole_maj")
                    )
                )
            }
        }
    }

    private fun queryGermany(center: GeoPoint, key: String): List<FuelCandidate> {
        val clean = key.trim()
        if (clean.length < 30) return emptyList()
        val endpoint = "https://creativecommons.tankerkoenig.de/json/list.php" +
            "?lat=${center.lat}&lng=${center.lon}&rad=25&type=diesel&sort=price" +
            "&apikey=${URLEncoder.encode(clean, "UTF-8")}" 
        val root = JSONObject(http(endpoint))
        if (!root.optBoolean("ok")) return emptyList()
        val stations = root.optJSONArray("stations") ?: JSONArray()
        return buildList {
            for (index in 0 until stations.length()) {
                val row = stations.optJSONObject(index) ?: continue
                val price = row.optDouble("diesel", Double.NaN)
                val lat = row.optDouble("lat", Double.NaN)
                val lon = row.optDouble("lng", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || !price.isFinite()) continue
                val name = row.optString("name").ifBlank { row.optString("brand", "Tankstelle") }
                val address = listOf(row.optString("street"), row.optString("houseNumber"), row.optString("place"))
                    .filter(String::isNotBlank).joinToString(" ")
                add(
                    FuelCandidate(
                        name = name,
                        address = address,
                        point = GeoPoint(lat, lon, name, "fuel"),
                        dieselPrice = price,
                        motorway = MotorwayFilter.isMotorwayService(name, address),
                        source = "Deutschland · Tankerkönig",
                        updatedAt = LocalDateTime.now().withNano(0).toString()
                    )
                )
            }
        }
    }

    private fun querySpain(centers: List<GeoPoint>): List<FuelCandidate> {
        val endpoint = "https://energia.serviciosmin.gob.es/ServiciosRestCarburantes/" +
            "PreciosCarburantes/EstacionesTerrestres/"
        val root = JSONObject(http(endpoint))
        val date = root.optString("Fecha")
        val stations = root.optJSONArray("ListaEESSPrecio") ?: JSONArray()
        return buildList {
            for (index in 0 until stations.length()) {
                val row = stations.optJSONObject(index) ?: continue
                val lat = decimal(row.optString("Latitud")) ?: continue
                val lon = decimal(row.optString("Longitud (WGS84)")) ?: continue
                val point = GeoPoint(lat, lon)
                if (centers.none { Geo.distanceM(it, point) <= 48_000.0 }) continue
                val price = decimal(row.optString("Precio Gasoleo A")) ?: continue
                val brand = row.optString("Rótulo").ifBlank { "Tankstelle Spanien" }
                val address = listOf(row.optString("Dirección"), row.optString("Municipio"))
                    .filter(String::isNotBlank).joinToString(", ")
                add(
                    FuelCandidate(
                        name = brand,
                        address = address,
                        point = GeoPoint(lat, lon, brand, "fuel"),
                        dieselPrice = price,
                        motorway = MotorwayFilter.isMotorwayService(brand, address),
                        source = "Spanien · offizieller Dieselpreis",
                        updatedAt = date
                    )
                )
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
        var lastFailure = "Tankdaten nicht erreichbar"
        repeat(3) { attempt ->
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 22_000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "ReisePilot/5.4 Android family travel app")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299 && body.isNotBlank()) return body
                lastFailure = "Tankdaten HTTP $code"
                if (code !in setOf(429, 500, 502, 503, 504) || attempt == 2) error(lastFailure)
                Thread.sleep(650L * (attempt + 1))
            } catch (error: IOException) {
                lastFailure = "Tankdaten: ${error.message ?: error.javaClass.simpleName}"
                if (attempt == 2) throw error
                Thread.sleep(650L * (attempt + 1))
            } finally {
                connection.disconnect()
            }
        }
        error(lastFailure)
    }
}
