package de.balabucha.reisepilot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal data class MapLayers54(
    val traffic: Boolean = true,
    val fuel: Boolean = true,
    val roadside: Boolean = true,
    val parking: Boolean = true,
    val tolls: Boolean = true
)

@Composable
internal fun NativeTripMap54(
    snapshot: TripSnapshot,
    previewRoute: RouteResult?,
    savedParkings: List<SavedParking>,
    roadState: RoadAheadState54,
    layers: MapLayers54,
    modifier: Modifier = Modifier
) {
    val roadsideParkings = if (layers.roadside) {
        roadState.roadsideStops.map { stop ->
            SavedParking(
                placeKey = "road54:${stop.id}",
                placeTitle = stop.kind.label,
                spot = ParkingSpot(
                    id = "road54:${stop.id}",
                    name = "${stop.kind.label} · ${stop.name}",
                    point = stop.point,
                    kind = ParkingKind.SURFACE,
                    distanceToDestinationM = (stop.distanceAheadKm * 1_000).toInt(),
                    fee = ParkingFee.UNKNOWN,
                    recommended = stop.directionChecked,
                    note = "${distanceKm54(stop.distanceAheadKm)} voraus",
                    source = stop.source
                )
            )
        }
    } else emptyList()

    val filteredSnapshot = snapshot.copy(
        congestionJson = if (layers.traffic) snapshot.congestionJson else "[]",
        tolls = if (layers.tolls) snapshot.tolls else emptyList(),
        fuelSuggestion = if (layers.fuel) roadState.nextFuel ?: snapshot.fuelSuggestion else null
    )
    val filteredPreview = previewRoute?.copy(
        congestionJson = if (layers.traffic) previewRoute.congestionJson else "[]",
        tolls = if (layers.tolls) previewRoute.tolls else emptyList()
    )
    NativeTripMap(
        snapshot = filteredSnapshot,
        previewRoute = filteredPreview,
        parkings = buildList {
            if (layers.parking) addAll(savedParkings)
            addAll(roadsideParkings)
        },
        modifier = modifier
    )
}
