package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Searches useful roadside places around the user while no route is active. */
internal object NearbyRoadsideClient56 {
    fun query(current: GeoPoint): List<RoadsideStop54> {
        val query = """
            [out:json][timeout:22];
            (
              nwr(around:35000,${format(current.lat)},${format(current.lon)})[highway~"^(services|rest_area)$"];
              nwr(around:15000,${format(current.lat)},${format(current.lon)})[amenity=parking][access!~"^(private|no)$"];
              nwr(around:15000,${format(current.lat)},${format(current.lon)})[parking=layby][access!~"^(private|no)$"];
            );
            out center tags;
        """.trimIndent()
        val elements = JSONObject(postOverpass(query)).optJSONArray("elements") ?: JSONArray()
        val input = buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: JSONObject()
                val lat = if (element.has("lat")) element.optDouble("lat", Double.NaN)
                    else element.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
                val lon = if (element.has("lon")) element.optDouble("lon", Double.NaN)
                    else element.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
                if (!lat.isFinite() || !lon.isFinite()) continue
                val point = GeoPoint(lat, lon)
                val distance = Geo.distanceM(current, point) / 1_000.0
                if (distance !in 0.05..35.0) continue
                val highway = tags.optString("highway")
                val kind = when (highway) {
                    "services" -> RoadsideKind54.SERVICE_AREA
                    "rest_area" -> RoadsideKind54.REST_AREA
                    else -> RoadsideKind54.PARKING
                }
                val defaultName = when (kind) {
                    RoadsideKind54.SERVICE_AREA -> "Raststätte"
                    RoadsideKind54.REST_AREA -> "Rastplatz"
                    RoadsideKind54.PARKING -> "Parkplatz"
                }
                val name = tags.optString("name").ifBlank {
                    tags.optString("operator").takeIf(String::isNotBlank)?.let { "$defaultName $it" } ?: defaultName
                }
                val id = "${element.optString("type")}:${element.optLong("id")}".ifBlank {
                    "near:${format(lat)}:${format(lon)}"
                }
                add(
                    RoadsideStop54(
                        id = id,
                        name = name,
                        point = GeoPoint(lat, lon, name, kind.name.lowercase()),
                        kind = kind,
                        distanceAheadKm = distance,
                        routeOffsetKm = distance,
                        detourKm = null,
                        hasFuel = kind == RoadsideKind54.SERVICE_AREA || yes(tags, "fuel") || tags.optString("amenity") == "fuel",
                        hasToilets = kind == RoadsideKind54.SERVICE_AREA || yes(tags, "toilets") || tags.optString("toilets") == "yes",
                        hasFood = kind == RoadsideKind54.SERVICE_AREA || yes(tags, "restaurant") || yes(tags, "fast_food") || yes(tags, "food"),
                        directionChecked = false,
                        source = "OpenStreetMap · in deiner Nähe"
                    )
                )
            }
        }.distinctBy { stop ->
            "${stop.kind}:${stop.name.lowercase(Locale.GERMANY)}:${format(stop.point.lat)}:${format(stop.point.lon)}"
        }.sortedBy { it.distanceAheadKm }

        val selected = mutableListOf<RoadsideStop54>()
        RoadsideKind54.entries.forEach { kind -> selected += input.filter { it.kind == kind }.take(4) }
        return selected.distinctBy { it.id }.sortedBy { it.distanceAheadKm }.take(12)
    }

    private fun yes(tags: JSONObject, key: String): Boolean =
        tags.optString(key).lowercase(Locale.ROOT) in setOf("yes", "true", "1")

    private fun format(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun postOverpass(query: String): String {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        var lastError = "Umgebungsdaten nicht erreichbar"
        endpoints.forEachIndexed { endpointIndex, endpoint ->
            repeat(2) { attempt ->
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 12_000
                    connection.readTimeout = 28_000
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "ReisePilot/5.6 Android nearby roadside")
                    val body = "data=${URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8)
                    connection.outputStream.use { it.write(body) }
                    val code = connection.responseCode
                    val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code in 200..299 && response.isNotBlank()) return response
                    lastError = "Umgebungsdaten HTTP $code"
                    if (code !in setOf(429, 500, 502, 503, 504)) return@repeat
                    Thread.sleep(700L * (attempt + 1 + endpointIndex))
                } catch (error: IOException) {
                    lastError = error.message ?: error.javaClass.simpleName
                    Thread.sleep(700L * (attempt + 1 + endpointIndex))
                } finally {
                    connection.disconnect()
                }
            }
        }
        error(lastError)
    }
}
