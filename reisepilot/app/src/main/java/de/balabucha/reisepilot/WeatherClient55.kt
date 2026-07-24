package de.balabucha.reisepilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class WeatherTarget55(
    val label: String,
    val place: String,
    val point: GeoPoint,
    val hourOffset: Int
)

internal data class WeatherPoint55(
    val label: String,
    val place: String,
    val temperatureC: Int?,
    val rainProbability: Int?,
    val windKmh: Int?,
    val weatherCode: Int?,
    val forecastTime: String
)

internal data class RouteWeather55(
    val points: List<WeatherPoint55> = emptyList(),
    val sunset: String = "",
    val updatedAt: Long = 0L,
    val fromCache: Boolean = false,
    val message: String = ""
) {
    val current: WeatherPoint55?
        get() = points.firstOrNull()

    fun json(): JSONObject = JSONObject().apply {
        put("updatedAt", updatedAt)
        put("fromCache", fromCache)
        put("sunset", sunset)
        put("points", JSONArray().apply {
            points.forEach { point ->
                put(JSONObject().apply {
                    put("label", point.label)
                    put("place", point.place)
                    put("temperatureC", point.temperatureC ?: JSONObject.NULL)
                    put("rainProbability", point.rainProbability ?: JSONObject.NULL)
                    put("windKmh", point.windKmh ?: JSONObject.NULL)
                    put("condition", weatherLabel55(point.weatherCode))
                    put("forecastTime", point.forecastTime)
                })
            }
        })
    }
}

internal object WeatherClient55 {
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val CACHE_PREFS = "weather_cache_v55"
    private const val CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1_000L

    fun query(context: Context, targets: List<WeatherTarget55>): RouteWeather55 {
        if (targets.isEmpty()) return RouteWeather55(message = "Keine Wetterpunkte verfügbar")
        val normalized = targets.take(4)
        val cacheKey = cacheKey(normalized)
        val prefs = context.applicationContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val bodyKey = "body:$cacheKey"
        val timeKey = "time:$cacheKey"

        return runCatching {
            val body = http(buildUrl(normalized))
            prefs.edit()
                .putString(bodyKey, body)
                .putLong(timeKey, System.currentTimeMillis())
                .apply()
            parse(body, normalized, fromCache = false, message = "")
        }.getOrElse { error ->
            val cached = prefs.getString(bodyKey, null)
            val cachedAt = prefs.getLong(timeKey, 0L)
            if (!cached.isNullOrBlank() && System.currentTimeMillis() - cachedAt <= CACHE_MAX_AGE_MS) {
                runCatching {
                    parse(
                        cached,
                        normalized,
                        fromCache = true,
                        message = "Offline-Wetter · letzter erfolgreicher Stand"
                    )
                }.getOrElse {
                    RouteWeather55(message = "Wetter nicht verfügbar: ${error.message.orEmpty().take(100)}")
                }
            } else {
                RouteWeather55(message = "Wetter nicht verfügbar: ${error.message.orEmpty().take(100)}")
            }
        }
    }

    private fun buildUrl(targets: List<WeatherTarget55>): String {
        val latitudes = targets.joinToString(",") { String.format(java.util.Locale.US, "%.5f", it.point.lat) }
        val longitudes = targets.joinToString(",") { String.format(java.util.Locale.US, "%.5f", it.point.lon) }
        val params = linkedMapOf(
            "latitude" to latitudes,
            "longitude" to longitudes,
            "current" to "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_gusts_10m",
            "hourly" to "temperature_2m,precipitation_probability,weather_code,wind_speed_10m",
            "daily" to "sunset",
            "timezone" to "auto",
            "forecast_days" to "2",
            "wind_speed_unit" to "kmh"
        )
        return FORECAST_URL + "?" + params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    private fun parse(
        body: String,
        targets: List<WeatherTarget55>,
        fromCache: Boolean,
        message: String
    ): RouteWeather55 {
        val roots = when (val first = body.trim().firstOrNull()) {
            '[' -> JSONArray(body).let { array -> List(array.length()) { array.getJSONObject(it) } }
            '{' -> listOf(JSONObject(body))
            else -> error("Ungültige Wetterantwort: $first")
        }
        val points = targets.mapIndexedNotNull { index, target ->
            val root = roots.getOrNull(index) ?: roots.firstOrNull() ?: return@mapIndexedNotNull null
            parsePoint(root, target)
        }
        val sunset = roots.firstOrNull()
            ?.optJSONObject("daily")
            ?.optJSONArray("sunset")
            ?.optString(0)
            .orEmpty()
            .substringAfter('T', "")
            .take(5)
        return RouteWeather55(
            points = points,
            sunset = sunset,
            updatedAt = System.currentTimeMillis(),
            fromCache = fromCache,
            message = message
        )
    }

    private fun parsePoint(root: JSONObject, target: WeatherTarget55): WeatherPoint55 {
        if (target.hourOffset <= 0) {
            val current = root.optJSONObject("current")
            if (current != null) {
                return WeatherPoint55(
                    label = target.label,
                    place = target.place,
                    temperatureC = current.optDouble("temperature_2m", Double.NaN).takeIf(Double::isFinite)?.roundToInt(),
                    rainProbability = null,
                    windKmh = current.optDouble("wind_speed_10m", Double.NaN).takeIf(Double::isFinite)?.roundToInt(),
                    weatherCode = current.optInt("weather_code"),
                    forecastTime = current.optString("time").substringAfter('T', "").take(5)
                )
            }
        }

        val hourly = root.optJSONObject("hourly") ?: return WeatherPoint55(
            target.label, target.place, null, null, null, null, ""
        )
        val times = hourly.optJSONArray("time") ?: JSONArray()
        val temperatures = hourly.optJSONArray("temperature_2m") ?: JSONArray()
        val rain = hourly.optJSONArray("precipitation_probability") ?: JSONArray()
        val codes = hourly.optJSONArray("weather_code") ?: JSONArray()
        val winds = hourly.optJSONArray("wind_speed_10m") ?: JSONArray()
        val targetTime = LocalDateTime.now().plusHours(target.hourOffset.toLong())
        var bestIndex = 0
        var bestMinutes = Long.MAX_VALUE
        for (index in 0 until times.length()) {
            val parsed = runCatching {
                LocalDateTime.parse(times.optString(index), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }.getOrNull() ?: continue
            val minutes = abs(Duration.between(parsed, targetTime).toMinutes())
            if (minutes < bestMinutes) {
                bestMinutes = minutes
                bestIndex = index
            }
        }
        return WeatherPoint55(
            label = target.label,
            place = target.place,
            temperatureC = temperatures.optDouble(bestIndex, Double.NaN).takeIf(Double::isFinite)?.roundToInt(),
            rainProbability = rain.optDouble(bestIndex, Double.NaN).takeIf(Double::isFinite)?.roundToInt(),
            windKmh = winds.optDouble(bestIndex, Double.NaN).takeIf(Double::isFinite)?.roundToInt(),
            weatherCode = codes.optInt(bestIndex),
            forecastTime = times.optString(bestIndex).substringAfter('T', "").take(5)
        )
    }

    private fun cacheKey(targets: List<WeatherTarget55>): String = targets.joinToString("|") {
        "%.2f,%.2f,%d".format(java.util.Locale.US, it.point.lat, it.point.lon, it.hourOffset)
    }.hashCode().toString()

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReisePilot/5.5 Android weather")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) throw IOException("Open-Meteo HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }
}

internal fun weatherLabel55(code: Int?): String = when (code) {
    0 -> "klar"
    1, 2 -> "leicht bewölkt"
    3 -> "bewölkt"
    45, 48 -> "Nebel"
    51, 53, 55, 56, 57 -> "Nieselregen"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Regen"
    71, 73, 75, 77, 85, 86 -> "Schnee"
    95, 96, 99 -> "Gewitter"
    else -> "Wetter"
}
