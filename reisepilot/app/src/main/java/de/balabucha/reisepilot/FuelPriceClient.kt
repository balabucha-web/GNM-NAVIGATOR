package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import kotlin.math.max

internal data class FuelCandidate(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val dieselPrice: Double?,
    val motorway: Boolean,
    val source: String,
    val updatedAt: String = ""
)

internal object FuelRanking {
    fun best(
        current: GeoPoint,
        destination: GeoPoint,
        route: List<GeoPoint>,
        candidates: List<FuelCandidate>,
        consumption: Double,
        plannedLitres: Double = 35.0
    ): FuelSuggestion? {
        val currentToDestination = Geo.distanceM(current, destination) / 1000.0
        val usable = candidates.mapNotNull { station ->
            if (station.motorway) return@mapNotNull null
            val aheadKm = Geo.distanceM(current, station.point) / 1000.0
            if (aheadKm !in 0.3..85.0) return@mapNotNull null
            val stationToDestination = Geo.distanceM(station.point, destination) / 1000.0
            if (stationToDestination > currentToDestination + 12.0) return@mapNotNull null

            val routeOffsetKm = if (route.size >= 2) {
                Geo.closestToPolylineM(station.point, route) / 1000.0
            } else {
                0.0
            }
            if (routeOffsetKm > 8.0) return@mapNotNull null

            // Out and back from the route is longer than the straight route offset.
            val detourKm = max(0.4, routeOffsetKm * 2.25)
            val price = station.dieselPrice
            val detourFuelCost = if (price != null) detourKm * consumption / 100.0 * price else 0.0
            val timePenalty = detourKm * 0.20
            val score = if (price != null) price * plannedLitres + detourFuelCost + timePenalty
                else 1_000.0 + detourKm

            Triple(station, FuelSuggestion(
                name = station.name,
                address = station.address,
                point = station.point,
                pricePerLitre = price,
                detourKm = detourKm,
                distanceAheadKm = aheadKm,
                source = station.source,
                updatedAt = station.updatedAt
            ), score)
        }

        val pricedAvailable = usable.any { it.first.dieselPrice != null }
        return usable
            .filter { !pricedAvailable || it.first.dieselPrice != null }
            .minByOrNull { it.third }
            ?.second
    }
}

internal object FuelPriceClient {
    fun query(
        current: GeoPoint,
        route: List<GeoPoint>,
        destination: GeoPoint,
        consumption: Double,
        tankerKoenigKey: String
    ): FuelSuggestion? {
        val candidates = when (country(current)) {
            FuelCountry.ANDORRA -> andorraFallback()
            FuelCountry.SPAIN -> querySpain(current)
            FuelCountry.GERMANY -> queryGermany(current, tankerKoenigKey)
            FuelCountry.FRANCE -> queryFrance(current)
        }
        return FuelRanking.best(current, destination, route, candidates, consumption)
    }

    private enum class FuelCountry { GERMANY, FRANCE, SPAIN, ANDORRA }

    private fun country(point: GeoPoint): FuelCountry = when {
        point.lat in 42.40..42.68 && point.lon in 1.35..1.82 -> FuelCountry.ANDORRA
        point.lat < 42.55 && point.lon < 3.45 -> FuelCountry.SPAIN
        point.lat > 48.65 || point.lon > 8.15 -> FuelCountry.GERMANY
        else -> FuelCountry.FRANCE
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
                    motorway = false,
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
                val upper = "$brand $address".uppercase()
                val motorway = listOf("AUTOPISTA", "AUTOVIA", "AUTOVÍA", "AREA DE SERVICIO", "ÁREA DE SERVICIO", "AP-7", "AP-2")
                    .any { upper.contains(it) }
                add(FuelCandidate(
                    name = brand,
                    address = address,
                    point = GeoPoint(lat, lon, brand, "fuel"),
                    dieselPrice = price,
                    motorway = motorway,
                    source = "Spanien · offizieller Preis",
                    updatedAt = date
                ))
            }
        }
    }

    private fun andorraFallback(): List<FuelCandidate> = listOf(
        FuelCandidate(
            "Tankstelle Santa Coloma",
            "Avinguda d'Enclar, Santa Coloma",
            GeoPoint(42.4955, 1.4976, "Santa Coloma", "fuel"),
            null,
            false,
            "Andorra · Preis vor Ort"
        ),
        FuelCandidate(
            "Tankstelle Andorra la Vella",
            "Avinguda de Tarragona, Andorra la Vella",
            GeoPoint(42.5038, 1.5138, "Andorra la Vella", "fuel"),
            null,
            false,
            "Andorra · Preis vor Ort"
        ),
        FuelCandidate(
            "Tankstelle Encamp",
            "Avinguda de Joan Martí, Encamp",
            GeoPoint(42.5351, 1.5831, "Encamp", "fuel"),
            null,
            false,
            "Andorra · Preis vor Ort"
        )
    )

    private fun decimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "ReisePilot/3.3 Android")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Tankdaten HTTP $code")
        return body
    }
}
