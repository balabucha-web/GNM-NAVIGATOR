package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RouteResult(
    val durationSec: Int,
    val typicalDurationSec: Int?,
    val distanceM: Int,
    val geometryJson: String,
    val congestionJson: String,
    val geometry: List<GeoPoint>,
    val tolls: List<TollPoint>
)

data class DetourRouteResult(
    val totalDistanceM: Int,
    val distanceToStopM: Int
)

object MapboxClient {
    fun route(token: String, origin: GeoPoint, destination: GeoPoint): RouteResult {
        require(token.startsWith("pk.")) { "Öffentlicher Mapbox-Token erforderlich" }
        val coords = "${origin.lon},${origin.lat};${destination.lon},${destination.lat}"
        val root = request(
            token,
            coords,
            "alternatives=false&steps=true&geometries=geojson&overview=full" +
                "&annotations=congestion,distance,duration&language=de"
        )
        val route = root.getJSONArray("routes").getJSONObject(0)
        val geometryObj = route.getJSONObject("geometry")
        val coordsArray = geometryObj.getJSONArray("coordinates")
        val geometry = buildList {
            for (i in 0 until coordsArray.length()) {
                val c = coordsArray.getJSONArray(i)
                add(GeoPoint(c.getDouble(1), c.getDouble(0)))
            }
        }
        val leg = route.getJSONArray("legs").getJSONObject(0)
        val annotation = leg.optJSONObject("annotation")
        val congestion = annotation?.optJSONArray("congestion") ?: JSONArray()
        val tolls = mutableListOf<TollPoint>()
        val steps = leg.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val intersections = step.optJSONArray("intersections") ?: JSONArray()
            for (n in 0 until intersections.length()) {
                val intersection = intersections.getJSONObject(n)
                val toll = intersection.optJSONObject("toll_collection") ?: continue
                val location = intersection.optJSONArray("location") ?: continue
                val name = toll.optString("name").ifBlank { "Mautstelle" }
                val type = toll.optString("type", "toll_booth")
                val point = GeoPoint(location.getDouble(1), location.getDouble(0), name, "toll")
                val id = "${point.lat}_${point.lon}_${type}"
                if (tolls.none { it.id == id }) tolls += TollPoint(id, name, type, point)
            }
        }
        return RouteResult(
            durationSec = route.getDouble("duration").toInt(),
            typicalDurationSec = if (route.has("duration_typical"))
                route.optDouble("duration_typical").toInt() else null,
            distanceM = route.getDouble("distance").toInt(),
            geometryJson = geometryObj.toString(),
            congestionJson = congestion.toString(),
            geometry = geometry,
            tolls = tolls
        )
    }

    fun detourRoute(
        token: String,
        current: GeoPoint,
        stop: GeoPoint,
        destination: GeoPoint
    ): DetourRouteResult {
        require(token.startsWith("pk.")) { "Öffentlicher Mapbox-Token erforderlich" }
        val coords = "${current.lon},${current.lat};${stop.lon},${stop.lat};${destination.lon},${destination.lat}"
        val root = request(
            token,
            coords,
            "alternatives=false&steps=false&geometries=geojson&overview=false&language=de"
        )
        val route = root.getJSONArray("routes").getJSONObject(0)
        val legs = route.getJSONArray("legs")
        require(legs.length() >= 2) { "Tankumweg konnte nicht aufgeteilt werden" }
        return DetourRouteResult(
            totalDistanceM = route.getDouble("distance").toInt(),
            distanceToStopM = legs.getJSONObject(0).getDouble("distance").toInt()
        )
    }

    private fun request(token: String, coords: String, options: String): JSONObject {
        val url = URL(
            "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/$coords" +
                "?$options&access_token=${URLEncoder.encode(token, "UTF-8")}"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ReisePilot/4.1 Android")
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault(body)
            error("Mapbox $code: $msg")
        }
        return JSONObject(body)
    }
}
