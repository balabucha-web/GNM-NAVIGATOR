package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max

internal object RoadsideClient54 {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    fun query(
        current: GeoPoint,
        route: List<GeoPoint>,
        destination: GeoPoint,
        mapboxToken: String,
        baseRouteDistanceM: Int?
    ): List<RoadsideStop54> {
        if (route.size < 2) return emptyList()
        val samples = RouteAheadTools54.sampleAhead(current, route, maxDistanceM = 120_000.0, stepM = 15_000.0)
        val coordinateList = samples.joinToString(",") { point ->
            "${format(point.lat)},${format(point.lon)}"
        }
        val query = """
            [out:json][timeout:22];
            (
              nwr(around:3500,$coordinateList)[highway~"^(services|rest_area)$"];
              nwr(around:1800,$coordinateList)[amenity=parking][access!~"^(private|no)$"];
              nwr(around:1800,$coordinateList)[parking=layby][access!~"^(private|no)$"];
            );
            out center tags;
        """.trimIndent()
        val elements = JSONObject(postOverpass(query)).optJSONArray("elements") ?: JSONArray()
        val rough = buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: JSONObject()
                val lat = when {
                    element.has("lat") -> element.optDouble("lat", Double.NaN)
                    else -> element.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
                }
                val lon = when {
                    element.has("lon") -> element.optDouble("lon", Double.NaN)
                    else -> element.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
                }
                if (!lat.isFinite() || !lon.isFinite()) continue
                val point = GeoPoint(lat, lon)
                val highway = tags.optString("highway")
                val kind = when (highway) {
                    "services" -> RoadsideKind54.SERVICE_AREA
                    "rest_area" -> RoadsideKind54.REST_AREA
                    else -> RoadsideKind54.PARKING
                }
                val maxOffset = if (kind == RoadsideKind54.PARKING) 2_200.0 else 5_000.0
                val ahead = RouteAheadTools54.forwardDistanceKm(current, point, route, maxOffset) ?: continue
                if (ahead !in 0.15..120.0) continue
                val offset = Geo.closestToPolylineM(point, route) / 1_000.0
                if (kind == RoadsideKind54.PARKING && offset > 2.2) continue
                if (kind != RoadsideKind54.PARKING && offset > 5.0) continue
                val id = "${element.optString("type")}:${element.optLong("id")}".ifBlank {
                    "road:${format(lat)}:${format(lon)}"
                }
                val defaultName = when (kind) {
                    RoadsideKind54.SERVICE_AREA -> "Raststätte"
                    RoadsideKind54.REST_AREA -> "Rastplatz"
                    RoadsideKind54.PARKING -> "Parkplatz"
                }
                val name = tags.optString("name").ifBlank {
                    tags.optString("operator").takeIf(String::isNotBlank)?.let { "$defaultName $it" } ?: defaultName
                }
                add(
                    RoadsideStop54(
                        id = id,
                        name = name,
                        point = GeoPoint(lat, lon, name, kind.name.lowercase()),
                        kind = kind,
                        distanceAheadKm = ahead,
                        routeOffsetKm = offset,
                        detourKm = max(0.1, offset * 2.0),
                        hasFuel = kind == RoadsideKind54.SERVICE_AREA || yes(tags, "fuel") || tags.optString("amenity") == "fuel",
                        hasToilets = kind == RoadsideKind54.SERVICE_AREA || yes(tags, "toilets") || tags.optString("toilets") == "yes",
                        hasFood = kind == RoadsideKind54.SERVICE_AREA || yes(tags, "restaurant") || yes(tags, "fast_food") || yes(tags, "food"),
                        source = "OpenStreetMap"
                    )
                )
            }
        }.distinctBy { stop ->
            "${stop.kind}:${stop.name.lowercase(Locale.GERMANY)}:${format(stop.point.lat)}:${format(stop.point.lon)}"
        }.sortedBy { it.distanceAheadKm }

        val exactEnabled = mapboxTokenLooksValid(mapboxToken) && (baseRouteDistanceM ?: 0) > 0
        if (!exactEnabled) return balance(rough)

        val verifyIds = rough
            .groupBy { it.kind }
            .values
            .flatMap { it.take(2) }
            .sortedBy { it.distanceAheadKm }
            .take(6)
            .map { it.id }
            .toSet()

        val checked = rough.mapNotNull { stop ->
            if (stop.id !in verifyIds) return@mapNotNull stop
            val result = runCatching {
                MapboxClient.detourRoute(mapboxToken, current, stop.point, destination)
            }.getOrNull() ?: return@mapNotNull stop
            val detour = (result.totalDistanceM - baseRouteDistanceM!!).coerceAtLeast(0) / 1_000.0
            val ahead = result.distanceToStopM / 1_000.0
            val limit = if (stop.kind == RoadsideKind54.PARKING) 10.0 else 16.0
            if (detour > limit || ahead > stop.distanceAheadKm + 18.0) return@mapNotNull null
            stop.copy(
                distanceAheadKm = ahead,
                detourKm = detour,
                directionChecked = true,
                source = "OpenStreetMap · Fahrtrichtung geprüft"
            )
        }.sortedBy { it.distanceAheadKm }
        return balance(checked)
    }

    private fun balance(input: List<RoadsideStop54>): List<RoadsideStop54> {
        val selected = mutableListOf<RoadsideStop54>()
        RoadsideKind54.entries.forEach { kind -> selected += input.filter { it.kind == kind }.take(4) }
        return selected.distinctBy { it.id }.sortedBy { it.distanceAheadKm }.take(12)
    }

    private fun yes(tags: JSONObject, key: String): Boolean =
        tags.optString(key).lowercase(Locale.ROOT) in setOf("yes", "true", "1")

    private fun format(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun postOverpass(query: String): String {
        var lastError = "Rastplatzdaten nicht erreichbar"
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
                    connection.setRequestProperty("User-Agent", "ReisePilot/5.4 Android family travel app")
                    val body = "data=${URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8)
                    connection.outputStream.use { it.write(body) }
                    val code = connection.responseCode
                    val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code in 200..299 && response.isNotBlank()) return response
                    lastError = "Rastplatzdaten HTTP $code"
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
