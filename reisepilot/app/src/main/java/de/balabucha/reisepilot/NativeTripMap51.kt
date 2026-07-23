package de.balabucha.reisepilot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class MapLayers51(
    val traffic: Boolean = true,
    val fuel: Boolean = true,
    val parking: Boolean = true,
    val tolls: Boolean = true
)

@Composable
fun NativeTripMap51(
    snapshot: TripSnapshot,
    previewRoute: RouteResult?,
    parkings: List<SavedParking> = emptyList(),
    layers: MapLayers51 = MapLayers51(),
    modifier: Modifier = Modifier
) {
    val filteredSnapshot = snapshot.copy(
        congestionJson = if (layers.traffic) snapshot.congestionJson else "[]",
        tolls = if (layers.tolls) snapshot.tolls else emptyList(),
        fuelSuggestion = if (layers.fuel) snapshot.fuelSuggestion else null
    )
    val filteredPreview = previewRoute?.copy(
        congestionJson = if (layers.traffic) previewRoute.congestionJson else "[]",
        tolls = if (layers.tolls) previewRoute.tolls else emptyList()
    )
    NativeTripMap(
        snapshot = filteredSnapshot,
        previewRoute = filteredPreview,
        parkings = if (layers.parking) parkings else emptyList(),
        modifier = modifier
    )
}
