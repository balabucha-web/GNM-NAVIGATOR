package de.balabucha.reisepilot

object DiscoverLogic {
    fun filter(
        region: TravelRegion,
        kind: PlaceKind?,
        query: String,
        location: GeoPoint?,
        followLocation: Boolean
    ): List<TravelPlace> {
        val normalized = query.trim().lowercase()
        val filtered = DestinationCatalog.forRegion(region)
            .asSequence()
            .filter { kind == null || it.kind == kind }
            .filter {
                normalized.isBlank() ||
                    it.title.lowercase().contains(normalized) ||
                    it.description.lowercase().contains(normalized) ||
                    it.tip.lowercase().contains(normalized)
            }
            .toList()

        return if (followLocation && location != null) {
            filtered.sortedWith(
                compareBy<TravelPlace> {
                    DestinationCatalog.distanceKm(location, it) ?: Double.MAX_VALUE
                }.thenByDescending { it.priority }
            )
        } else {
            filtered.sortedByDescending { it.priority }
        }
    }

    fun spontaneous(region: TravelRegion): List<TravelPlace> =
        DestinationCatalog.forRegion(region)
            .filter { it.kind == PlaceKind.QUICK || it.duration.contains("1–") || it.duration.contains("30") }
            .sortedByDescending { it.priority }
            .take(3)
}
