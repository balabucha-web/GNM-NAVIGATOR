package de.balabucha.reisepilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingLogicTest {
    private val collioure = DestinationCatalog.places.first { it.title == "Collioure" }

    @Test
    fun curatedRecommendationAlwaysRanksBeforeAnonymousNearbyParking() {
        val anonymous = ParkingSpot(
            id = "osm:node:99",
            name = "Parkplatz",
            point = GeoPoint(42.5250, 3.0832),
            kind = ParkingKind.SURFACE,
            distanceToDestinationM = 20,
            fee = ParkingFee.FREE
        )
        val ranked = ParkingLogic.rank(
            collioure,
            ParkingCatalog.recommendations(collioure) + anonymous
        )

        assertEquals("Parking du Cap Dourats", ranked.first().name)
        assertTrue(ranked.first().recommended)
        assertTrue(ranked.first().walkingMinutes > 1)
    }

    @Test
    fun everyDestinationHasParkingAdviceAndAUsefulSearchRadius() {
        DestinationCatalog.places.forEach { place ->
            assertTrue(ParkingCatalog.advice(place).length >= 45)
            assertTrue(ParkingLogic.searchRadiusM(place) in 1_000..2_500)
        }
    }
}
