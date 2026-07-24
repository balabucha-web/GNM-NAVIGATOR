package de.balabucha.reisepilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.round

object AssistantContext52 {
    fun build(
        context: Context,
        snapshot: TripSnapshot,
        intent: AssistantIntent52,
        question: String,
        history: List<Pair<String, String>> = emptyList()
    ): JSONObject {
        val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
        val region = DestinationCatalog.nearestRegion(location) ?: TravelRegion.CANET
        val packing = PackingRepository(context).state
        val progress = PackingLogic.progress(packing.items)
        val openItems = packing.items.filterNot { it.checked }.map { it.name }.distinct().take(45)
        val destinationCandidates = DestinationCatalog.places
            .asSequence()
            .filter { it.region == region }
            .map { place -> place to DestinationCatalog.distanceKm(location, place) }
            .sortedWith(compareBy<Pair<TravelPlace, Double?>> { it.second ?: Double.MAX_VALUE }.thenByDescending { it.first.priority })
            .take(28)
            .toList()
        val now = ZonedDateTime.now()

        return JSONObject().apply {
            put("intent", intent.name)
            put("question", question.trim().take(900))
            put("localDateTime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("privacy", JSONObject().apply {
                put("positionRounded", location != null)
                put("personalNamesIncluded", false)
                put("bookingNumbersIncluded", false)
                put("gpsHistoryIncluded", false)
            })
            put("family", JSONObject().apply {
                put("adults", 2)
                put("children", 2)
                put("childAges", JSONArray(listOf(10, 7)))
                put("preference", "entspannt, familiengeeignet, wenig Parkplatzstress, nachvollziehbare Vorschläge")
            })
            put("currentLocation", location?.let {
                JSONObject().put("lat", round2(it.lat)).put("lon", round2(it.lon))
            } ?: JSONObject.NULL)
            put("trip", JSONObject().apply {
                put("active", snapshot.active)
                put("paused", snapshot.paused)
                put("stage", snapshot.stage.name)
                put("origin", TripConfig.origin(snapshot.stage).name)
                put("destination", TripConfig.destination(snapshot.stage).name)
                put("remainingKm", snapshot.remainingKm ?: JSONObject.NULL)
                put("etaEpochMs", snapshot.etaEpochMs ?: JSONObject.NULL)
                put("driveMinutes", snapshot.driveMinutes)
                put("speedKmh", snapshot.speedKmh ?: JSONObject.NULL)
                put("trafficDelayMinutes", snapshot.trafficDelayMin ?: JSONObject.NULL)
                put("nextInstruction", snapshot.nextTitle)
                put("nextDetail", snapshot.nextDetail)
                put("tolls", JSONArray(snapshot.tolls.map { it.name }))
            })
            put("fuel", JSONObject().apply {
                put("estimatedLitres", snapshot.fuelLitres)
                put("status", snapshot.fuelLight.name)
                put("suggestion", snapshot.fuelSuggestion?.let { fuel ->
                    JSONObject().apply {
                        put("name", fuel.name)
                        put("distanceAheadKm", fuel.distanceAheadKm)
                        put("detourKm", fuel.detourKm)
                        put("pricePerLitre", fuel.pricePerLitre ?: JSONObject.NULL)
                        put("address", fuel.address)
                    }
                } ?: JSONObject.NULL)
            })
            put("weather", JSONObject.NULL)
            put("weatherNote", "Wetterdaten sind in diesem Build noch nicht angebunden. Keine Wetterwerte erfinden.")
            put("packing", JSONObject().apply {
                put("done", progress.done)
                put("total", progress.total)
                put("openItems", JSONArray(openItems))
            })
            put("region", JSONObject().apply {
                put("name", region.label)
                put("shortPlan", region.shortPlan)
                put("logistics", region.logistics)
            })
            put("destinations", JSONArray().apply {
                destinationCandidates.forEach { (place, distance) ->
                    put(JSONObject().apply {
                        put("title", place.title)
                        put("kind", place.kind.label)
                        put("duration", place.duration)
                        put("description", place.description)
                        put("tip", place.tip)
                        put("priority", place.priority)
                        put("distanceKm", distance ?: JSONObject.NULL)
                        put("point", JSONObject().put("lat", place.point.lat).put("lon", place.point.lon))
                    })
                }
            })
            put("recentConversation", JSONArray().apply {
                history.takeLast(4).forEach { (user, assistant) ->
                    put(JSONObject().put("user", user.take(400)).put("assistant", assistant.take(500)))
                }
            })
            put("allowedActions", JSONArray(listOf(
                "open_destination_route",
                "open_google_maps_search",
                "propose_packing_items_with_confirmation",
                "read_answer_aloud"
            )))
        }
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}