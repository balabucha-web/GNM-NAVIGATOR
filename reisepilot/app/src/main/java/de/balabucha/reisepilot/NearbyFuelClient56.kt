package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.Locale

/** Loads useful fuel stations around the current location before a route exists. */
internal object NearbyFuelClient56 {
    fun query(current: GeoPoint, tankerKoenigKey: String): List<FuelSuggestion> {
        val candidates = when (CountryResolver.country(current)) {
            FuelCountry.GERMANY -> queryGermany(current, tankerKoenigKey).ifEmpty { queryOsm(current) }
            FuelCountry.FRANCE -> queryFrance(current)
            FuelCountry.SPAIN -> querySpain(current)
            FuelCountry.ANDORRA -> andorraFallback()
        }
        val ranked = candidates.asSequence()
            .filterNot { it.motorway }
            .map { station -> station to Geo.distanceM(current, station.point) / 1_000.0 }
            .filter { (_, distance) -> distance in 0.05..35.0 }
            .sortedWith(
                compareBy<Pair<FuelCandidate, Double>> { it.first.dieselPrice ?: 99.0 }
                    .thenBy { it.second }
            )
            .distinctBy { (station, _) -> "${station.name.lowercase(Locale.GERMANY)}:${"%.4f".format(Locale.US, station.point.lat)}:${"%.4f".format(Locale.US, station.point.lon)}" }
            .take(6)
            .toList()
        val priced = ranked.any { it.first.dieselPrice != null }
        return ranked.asSequence()
            .filter { !priced || it.first.dieselPrice != null }
            .take(4)
            .map { (station, distance) ->
                FuelSuggestion(
                    name = station.name,
                    address = station.address,
                    point = station.point,
                    pricePerLitre = station.dieselPrice,
                    detourKm = 0.0,
                    distanceAheadKm = distance,
                    source = "${station.source} · in deiner Nähe",
                    updatedAt = station.updatedAt
                )
            }
            .toList()
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

    private fun queryOsm(center: GeoPoint): List<FuelCandidate> {
        val query = """
            [out:json][timeout:18];
            nwr(around:22000,${format(center.lat)},${format(center.lon)})[amenity=fuel][access!~"^(private|no)$"];
            out center tags;
        """.trimIndent()
        val root = JSONObject(postOverpass(query))
        val elements = root.optJSONArray("elements") ?: JSONArray()
        return buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: JSONObject()
                val lat = if (element.has("lat")) element.optDouble("lat", Double.NaN)
                    else element.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
                val lon = if (element.has("lon")) element.optDouble("lon", Double.NaN)
                    else element.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
                if (!lat.isFinite() || !lon.isFinite()) continue
                val brand = tags.optString("brand").ifBlank { tags.optString("operator") }
                val name = tags.optString("name").ifBlank { brand.ifBlank { "Tankstelle" } }
                val address = listOf(
                    tags.optString("addr:street"), tags.optString("addr:housenumber"), tags.optString("addr:city")
                ).filter(String::isNotBlank).joinToString(" ")
                add(
                    FuelCandidate(
                        name = name,
                        address = address,
                        point = GeoPoint(lat, lon, name, "fuel"),
                        dieselPrice = null,
                        motorway = MotorwayFilter.isMotorwayService(name, address),
                        source = "OpenStreetMap · Preis nicht gemeldet"
                    )
                )
            }
        }
    }

    private fun queryFrance(center: GeoPoint): List<FuelCandidate> {
        val where = "within_distance(geom, geom'POINT(${center.lon} ${center.lat})', '35km') and pop='R' and gazole_prix is not null"
        val endpoint = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
            "prix-des-carburants-en-france-flux-instantane-v2/records" +
            "?where=${URLEncoder.encode(where, "UTF-8")}" +
            "&order_by=${URLEncoder.encode("gazole_prix asc", "UTF-8")}&limit=80"
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

    private fun querySpain(center: GeoPoint): List<FuelCandidate> {
        val endpoint = "https://energia.serviciosmin.gob.es/ServiciosRestCarburantes/PreciosCarburantes/EstacionesTerrestres/"
        val root = JSONObject(http(endpoint))
        val date = root.optString("Fecha")
        val stations = root.optJSONArray("ListaEESSPrecio") ?: JSONArray()
        return buildList {
            for (index in 0 until stations.length()) {
                val row = stations.optJSONObject(index) ?: continue
                val lat = decimal(row.optString("Latitud")) ?: continue
                val lon = decimal(row.optString("Longitud (WGS84)")) ?: continue
                val point = GeoPoint(lat, lon)
                if (Geo.distanceM(center, point) > 40_000.0) continue
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

    private fun postOverpass(query: String): String {
        val endpoints = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
        var lastError = "Tankstellen in der Nähe nicht erreichbar"
        for (endpoint in endpoints) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 24_000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "ReisePilot/5.6 Android nearby fuel")
                val body = "data=${URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299 && response.isNotBlank()) return response
                lastError = "Tankstellensuche HTTP $code"
            } catch (error: IOException) {
                lastError = error.message ?: error.javaClass.simpleName
            } finally {
                connection.disconnect()
            }
        }
        error(lastError)
    }

    private fun decimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()
    private fun format(value: Double): String = String.format(Locale.US, "%.6f", value)

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
                connection.setRequestProperty("User-Agent", "ReisePilot/5.6 Android nearby data")
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
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
