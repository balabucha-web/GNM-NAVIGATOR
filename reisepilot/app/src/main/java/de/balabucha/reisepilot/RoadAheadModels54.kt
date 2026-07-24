package de.balabucha.reisepilot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RoadsideKind54(val label: String) {
    SERVICE_AREA("Raststätte"),
    REST_AREA("Rastplatz"),
    PARKING("Parkplatz")
}

data class RoadsideStop54(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val kind: RoadsideKind54,
    val distanceAheadKm: Double,
    val routeOffsetKm: Double,
    val detourKm: Double? = null,
    val hasFuel: Boolean = false,
    val hasToilets: Boolean = false,
    val hasFood: Boolean = false,
    val directionChecked: Boolean = false,
    val source: String = "OpenStreetMap"
)

data class RoadAheadState54(
    val active: Boolean = false,
    val speedKmh: Int? = null,
    val accuracyM: Int? = null,
    val speedUpdatedAt: Long = 0L,
    val fuelOptions: List<FuelSuggestion> = emptyList(),
    val roadsideStops: List<RoadsideStop54> = emptyList(),
    val dataUpdatedAt: Long = 0L,
    val loadingFuel: Boolean = false,
    val loadingRoadside: Boolean = false,
    val message: String = ""
) {
    val nextService: RoadsideStop54?
        get() = roadsideStops.firstOrNull { it.kind == RoadsideKind54.SERVICE_AREA }
            ?: roadsideStops.firstOrNull { it.kind == RoadsideKind54.REST_AREA }

    val nextRestArea: RoadsideStop54?
        get() = roadsideStops.firstOrNull { it.kind == RoadsideKind54.REST_AREA }
            ?: roadsideStops.firstOrNull { it.kind == RoadsideKind54.SERVICE_AREA }

    val nextParking: RoadsideStop54?
        get() = roadsideStops.firstOrNull { it.kind == RoadsideKind54.PARKING }

    val nextFuel: FuelSuggestion?
        get() = fuelOptions.firstOrNull()
}

object RoadAheadStore54 {
    var state by mutableStateOf(RoadAheadState54())
        internal set
}
