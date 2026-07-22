package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject

enum class Stage { SATURDAY, SUNDAY }
enum class TripMode { TEST, REAL }
enum class Light { GREEN, YELLOW, RED, GREY }

data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val name: String = "",
    val type: String = ""
) {
    fun json(): JSONObject = JSONObject()
        .put("lat", lat).put("lon", lon)
        .put("name", name).put("type", type)

    companion object {
        fun fromJson(j: JSONObject) = GeoPoint(
            j.getDouble("lat"), j.getDouble("lon"),
            j.optString("name"), j.optString("type")
        )
    }
}

data class TollPoint(
    val id: String,
    val name: String,
    val type: String,
    val point: GeoPoint
) {
    fun json(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type)
        .put("point", point.json())

    companion object {
        fun fromJson(j: JSONObject) = TollPoint(
            j.getString("id"), j.optString("name", "Mautstelle"),
            j.optString("type", "toll_booth"),
            GeoPoint.fromJson(j.getJSONObject("point"))
        )
    }
}

data class FuelSuggestion(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val pricePerLitre: Double? = null,
    val detourKm: Double,
    val distanceAheadKm: Double,
    val source: String,
    val updatedAt: String = ""
) {
    fun json(): JSONObject = JSONObject()
        .put("name", name)
        .put("address", address)
        .put("point", point.json())
        .put("pricePerLitre", pricePerLitre ?: JSONObject.NULL)
        .put("detourKm", detourKm)
        .put("distanceAheadKm", distanceAheadKm)
        .put("source", source)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(j: JSONObject) = FuelSuggestion(
            name = j.optString("name", "Tankstelle"),
            address = j.optString("address"),
            point = GeoPoint.fromJson(j.getJSONObject("point")),
            pricePerLitre = if (j.isNull("pricePerLitre")) null else j.optDouble("pricePerLitre"),
            detourKm = j.optDouble("detourKm"),
            distanceAheadKm = j.optDouble("distanceAheadKm"),
            source = j.optString("source"),
            updatedAt = j.optString("updatedAt")
        )
    }
}

data class TripSnapshot(
    val active: Boolean = false,
    val paused: Boolean = false,
    val stage: Stage = Stage.SATURDAY,
    val tripMode: TripMode = TripMode.TEST,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedKmh: Int? = null,
    val accuracyM: Int? = null,
    val driveMinutes: Int = 0,
    val distanceTravelledKm: Double = 0.0,
    val remainingKm: Int? = null,
    val etaEpochMs: Long? = null,
    val typicalDurationMin: Int? = null,
    val trafficDelayMin: Int? = null,
    val scheduleLight: Light = Light.GREY,
    val pauseLight: Light = Light.GREEN,
    val fuelLight: Light = Light.GREEN,
    val fuelLitres: Double = 60.0,
    val fuelSuggestion: FuelSuggestion? = null,
    val nextTitle: String = "Bereit",
    val nextDetail: String = "Noch nicht gestartet",
    val nextDistanceM: Int? = null,
    val routeGeoJson: String = "",
    val congestionJson: String = "[]",
    val tolls: List<TollPoint> = emptyList(),
    val apiOk: Boolean = false,
    val apiMessage: String = "Kartenzugang nicht eingerichtet",
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
) {
    fun json(): JSONObject {
        val a = JSONArray()
        tolls.forEach { a.put(it.json()) }
        return JSONObject()
            .put("active", active).put("paused", paused)
            .put("stage", stage.name)
            .put("tripMode", tripMode.name)
            .put("lat", lat ?: JSONObject.NULL).put("lon", lon ?: JSONObject.NULL)
            .put("speedKmh", speedKmh ?: JSONObject.NULL)
            .put("accuracyM", accuracyM ?: JSONObject.NULL)
            .put("driveMinutes", driveMinutes)
            .put("distanceTravelledKm", distanceTravelledKm)
            .put("remainingKm", remainingKm ?: JSONObject.NULL)
            .put("etaEpochMs", etaEpochMs ?: JSONObject.NULL)
            .put("typicalDurationMin", typicalDurationMin ?: JSONObject.NULL)
            .put("trafficDelayMin", trafficDelayMin ?: JSONObject.NULL)
            .put("scheduleLight", scheduleLight.name)
            .put("pauseLight", pauseLight.name)
            .put("fuelLight", fuelLight.name)
            .put("fuelLitres", fuelLitres)
            .put("fuelSuggestion", fuelSuggestion?.json() ?: JSONObject.NULL)
            .put("nextTitle", nextTitle).put("nextDetail", nextDetail)
            .put("nextDistanceM", nextDistanceM ?: JSONObject.NULL)
            .put("routeGeoJson", routeGeoJson)
            .put("congestionJson", congestionJson)
            .put("tolls", a)
            .put("apiOk", apiOk).put("apiMessage", apiMessage)
            .put("lastUpdatedEpochMs", lastUpdatedEpochMs)
    }

    companion object {
        fun fromJson(raw: String?): TripSnapshot {
            if (raw.isNullOrBlank()) return TripSnapshot()
            return runCatching {
                val j = JSONObject(raw)
                val tollArray = j.optJSONArray("tolls") ?: JSONArray()
                val tolls = buildList {
                    for (i in 0 until tollArray.length()) {
                        add(TollPoint.fromJson(tollArray.getJSONObject(i)))
                    }
                }
                TripSnapshot(
                    active = j.optBoolean("active"),
                    paused = j.optBoolean("paused"),
                    stage = runCatching { Stage.valueOf(j.optString("stage")) }
                        .getOrDefault(Stage.SATURDAY),
                    // Builds up to 20 did not persist a mode. Their stored kilometres
                    // came from setup runs and must never be treated as the real trip.
                    tripMode = runCatching { TripMode.valueOf(j.optString("tripMode")) }
                        .getOrDefault(TripMode.TEST),
                    lat = if (j.isNull("lat")) null else j.getDouble("lat"),
                    lon = if (j.isNull("lon")) null else j.getDouble("lon"),
                    speedKmh = if (j.isNull("speedKmh")) null else j.getInt("speedKmh"),
                    accuracyM = if (j.isNull("accuracyM")) null else j.getInt("accuracyM"),
                    driveMinutes = j.optInt("driveMinutes"),
                    distanceTravelledKm = j.optDouble("distanceTravelledKm"),
                    remainingKm = if (j.isNull("remainingKm")) null else j.getInt("remainingKm"),
                    etaEpochMs = if (j.isNull("etaEpochMs")) null else j.getLong("etaEpochMs"),
                    typicalDurationMin = if (j.isNull("typicalDurationMin")) null else j.getInt("typicalDurationMin"),
                    trafficDelayMin = if (j.isNull("trafficDelayMin")) null else j.getInt("trafficDelayMin"),
                    scheduleLight = runCatching { Light.valueOf(j.optString("scheduleLight")) }.getOrDefault(Light.GREY),
                    pauseLight = runCatching { Light.valueOf(j.optString("pauseLight")) }.getOrDefault(Light.GREEN),
                    fuelLight = runCatching { Light.valueOf(j.optString("fuelLight")) }.getOrDefault(Light.GREEN),
                    fuelLitres = j.optDouble("fuelLitres", 60.0),
                    fuelSuggestion = if (j.isNull("fuelSuggestion")) null else FuelSuggestion.fromJson(j.getJSONObject("fuelSuggestion")),
                    nextTitle = j.optString("nextTitle", "Bereit"),
                    nextDetail = j.optString("nextDetail", "Noch nicht gestartet"),
                    nextDistanceM = if (j.isNull("nextDistanceM")) null else j.getInt("nextDistanceM"),
                    routeGeoJson = j.optString("routeGeoJson"),
                    congestionJson = j.optString("congestionJson", "[]"),
                    tolls = tolls,
                    apiOk = j.optBoolean("apiOk"),
                    apiMessage = j.optString("apiMessage", "Kartenzugang nicht eingerichtet"),
                    lastUpdatedEpochMs = j.optLong("lastUpdatedEpochMs", System.currentTimeMillis())
                )
            }.getOrDefault(TripSnapshot())
        }
    }
}

object TripConfig {
    val schwerin = GeoPoint(53.6400, 11.4000, "Schwerin", "start")
    val hotel = GeoPoint(47.499678, 6.817207, "greet Hôtel Montbéliard", "hotel")
    val canet = GeoPoint(42.7069, 3.0182, "Malibu Village", "hotel")

    val saturdayFuel = listOf(
        GeoPoint(49.3278, 11.0182, "BayWa Schwabach", "fuel"),
        GeoPoint(47.5015, 6.8179, "E.Leclerc Montbéliard", "fuel")
    )
    val sundayFuel = listOf(
        GeoPoint(44.1379, 4.8076, "Intermarché Orange", "fuel")
    )

    val fallbackTolls = listOf(
        TollPoint("fontaine", "Fontaine-Larivière · A36", "toll_booth",
            GeoPoint(47.716, 7.005, "Fontaine-Larivière", "toll")),
        TollPoint("ecot", "Saint-Maurice/Écot · A36", "toll_booth",
            GeoPoint(47.436, 6.652, "Saint-Maurice/Écot", "toll")),
        TollPoint("vienne", "Vienne-Reventin · A7", "toll_booth",
            GeoPoint(45.4735, 4.833558, "Vienne-Reventin", "toll")),
        TollPoint("perpignan", "Perpignan Nord · A9", "toll_booth",
            GeoPoint(42.78166, 2.89727, "Perpignan Nord", "toll"))
    )

    fun destination(stage: Stage) = if (stage == Stage.SATURDAY) hotel else canet
    fun origin(stage: Stage) = if (stage == Stage.SATURDAY) schwerin else hotel
    fun fuelStops(stage: Stage) = if (stage == Stage.SATURDAY) saturdayFuel else sundayFuel
}
