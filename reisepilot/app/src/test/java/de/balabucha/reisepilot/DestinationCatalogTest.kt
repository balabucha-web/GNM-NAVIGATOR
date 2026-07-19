package de.balabucha.reisepilot

import org.junit.Assert.*
import org.junit.Test

class DestinationCatalogTest {
    @Test
    fun everyRegionHasBroadUsefulCoverage() {
        TravelRegion.entries.forEach { region ->
            val places = DestinationCatalog.forRegion(region)
            assertTrue("$region needs at least fourteen entries", places.size >= 14)
            PlaceKind.entries.forEach { kind ->
                assertTrue("$region needs $kind", places.any { it.kind == kind })
            }
            assertTrue("Descriptions must be meaningful", places.all { it.description.length >= 55 })
            assertTrue("Tips must be useful", places.all { it.tip.length >= 15 })
            assertTrue("Image queries must exist", places.all { it.imageQuery.length >= 8 })
            assertTrue(
                "Coordinates must be valid",
                places.all { it.point.lat in -90.0..90.0 && it.point.lon in -180.0..180.0 }
            )
            assertEquals(
                "Titles must be unique inside a region",
                places.size,
                places.map { it.title.lowercase() }.distinct().size
            )
        }
    }

    @Test
    fun andorraContainsRequestedFiveToSevenKilometreWalk() {
        val andorra = DestinationCatalog.forRegion(TravelRegion.ANDORRA)
        assertTrue(andorra.any {
            it.title.contains("Pardines") &&
                (it.description.contains("fünf bis sieben") || it.tip.contains("5–7"))
        })
    }

    @Test
    fun manualListsAreOrderedByPriority() {
        TravelRegion.entries.forEach { region ->
            val priorities = DestinationCatalog.forRegion(region).map { it.priority }
            assertEquals(priorities.sortedDescending(), priorities)
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
