package de.balabucha.reisepilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Dated OSM fallback used when every public Overpass endpoint is busy. */
internal object OfflineParkingCatalog {
    @Volatile
    private var values: Map<String, List<ParkingSpot>>? = null

    fun spots(context: Context, place: TravelPlace): List<ParkingSpot> =
        all(context.applicationContext)[key(place)].orEmpty()

    fun coveredDestinationCount(context: Context): Int =
        DestinationCatalog.places.count { spots(context, it).isNotEmpty() }

    private fun key(place: TravelPlace) = "${place.region.name}:${place.title}"

    private fun all(context: Context): Map<String, List<ParkingSpot>> {
        values?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open("destination_parkings.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val places = JSONObject(raw).getJSONObject("places")
            buildMap {
                places.keys().forEach { placeKey ->
                    val array = places.optJSONArray(placeKey) ?: JSONArray()
                    val parkings = buildList {
                        for (index in 0 until array.length()) {
                            val row = array.optJSONObject(index) ?: continue
                            val capacity = row.optInt("capacity", -1).takeIf { it >= 0 }
                            val supervised = when {
                                row.isNull("supervised") -> null
                                else -> row.optBoolean("supervised")
                            }
                            add(
                                ParkingSpot(
                                    id = row.optString("id", "offline:$placeKey:$index"),
                                    name = row.optString("name", "Parkplatz"),
                                    point = GeoPoint(
                                        row.getDouble("lat"),
                                        row.getDouble("lon"),
                                        row.optString("name"),
                                        "parking"
                                    ),
                                    kind = runCatching { ParkingKind.valueOf(row.optString("kind")) }
                                        .getOrDefault(ParkingKind.UNKNOWN),
                                    distanceToDestinationM = row.optInt("distance"),
                                    fee = runCatching { ParkingFee.valueOf(row.optString("fee")) }
                                        .getOrDefault(ParkingFee.UNKNOWN),
                                    capacity = capacity,
                                    openingHours = row.optString("openingHours"),
                                    access = row.optString("access"),
                                    operator = row.optString("operator"),
                                    maxHeight = row.optString("maxHeight"),
                                    supervised = supervised,
                                    note = "Vorab gespeicherter OSM-Stand vom 22.07.2026 · live erneut prüfen.",
                                    source = "OpenStreetMap · Offline-Grundbestand"
                                )
                            )
                        }
                    }
                    if (parkings.isNotEmpty()) put(placeKey, parkings)
                }
            }
        }.getOrDefault(emptyMap())
        values = loaded
        return loaded
    }
}
