package de.balabucha.reisepilot

import org.junit.Assert.*
import org.junit.Test

class DiscoverLogicTest {
    @Test
    fun manualRegionShowsHighestPriorityFirst() {
        val places = DiscoverLogic.filter(
            region = TravelRegion.ANDORRA,
            kind = null,
            query = "",
            location = GeoPoint(48.86, 2.34),
            followLocation = false
        )
        assertTrue(places.isNotEmpty())
        assertEquals(places.maxOf { it.priority }, places.first().priority)
    }

    @Test
    fun searchFindsShoppingAndDescriptions() {
        val places = DiscoverLogic.filter(
            region = TravelRegion.CANET,
            kind = null,
            query = "einkauf",
            location = null,
            followLocation = false
        )
        assertTrue(places.isNotEmpty())
        assertTrue(places.all {
            it.title.lowercase().contains("einkauf") ||
                it.description.lowercase().contains("einkauf") ||
                it.tip.lowercase().contains("einkauf")
        })
    }

    @Test
    fun spontaneousSuggestionsStayCompact() {
        TravelRegion.entries.forEach { region ->
            val suggestions = DiscoverLogic.spontaneous(region)
            assertTrue(suggestions.size <= 3)
            assertTrue(suggestions.all { it.region == region })
        }
    }
}
