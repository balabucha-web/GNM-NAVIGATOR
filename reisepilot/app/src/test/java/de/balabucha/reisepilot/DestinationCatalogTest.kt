package de.balabucha.reisepilot

import org.junit.Assert.*
import org.junit.Test

class DestinationCatalogTest {
    @Test
    fun everyRegionHasUsefulCoverage() {
        TravelRegion.entries.forEach { region ->
            val places = DestinationCatalog.forRegion(region)
            assertTrue("$region needs at least six entries", places.size >= 6)
            assertTrue("$region needs a highlight", places.any { it.kind == PlaceKind.HIGHLIGHT })
            assertTrue("$region needs a family option", places.any { it.kind == PlaceKind.FAMILY })
            assertTrue("$region needs a shopping option", places.any { it.kind == PlaceKind.SHOPPING })
            assertTrue("Descriptions must be meaningful", places.all { it.description.length >= 35 })
            assertTrue("Coordinates must be valid", places.all { it.point.lat in -90.0..90.0 && it.point.lon in -180.0..180.0 })
        }
    }

    @Test
    fun nearestRegionSelectsAndorraInsideAndorra() {
        val result = DestinationCatalog.nearestRegion(GeoPoint(42.51, 1.53))
        assertEquals(TravelRegion.ANDORRA, result)
    }

    @Test
    fun nearestRegionSelectsParisInsideParis() {
        val result = DestinationCatalog.nearestRegion(GeoPoint(48.86, 2.34))
        assertEquals(TravelRegion.PARIS, result)
    }
}
