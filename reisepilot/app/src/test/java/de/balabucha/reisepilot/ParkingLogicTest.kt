package de.balabucha.reisepilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingLogicTest {
    private val collioure = DestinationCatalog.places.first { it.title == "Collioure" }

    @Test
    fun overpassParking_isParsedAndUnsafeOrStreetOnlyResultsAreRemoved() {
        val raw = """
            {
              "elements": [
                {"type":"node","id":1,"lat":42.5249,"lon":3.0797,"tags":{
                  "amenity":"parking","name":"Parking du Douy","parking":"surface",
                  "fee":"yes","capacity":"62","access":"yes"
                }},
                {"type":"way","id":2,"center":{"lat":42.5261,"lon":3.0690},"tags":{
                  "amenity":"parking","name":"Parking du Cap Dourats","parking":"surface",
                  "fee":"yes","capacity":"230","supervised":"yes"
                }},
                {"type":"node","id":3,"lat":42.5250,"lon":3.0830,"tags":{
                  "amenity":"parking","name":"Privat","access":"private"
                }},
                {"type":"node","id":4,"lat":42.5251,"lon":3.0831,"tags":{
                  "amenity":"parking","name":"Straßenrand","parking":"street_side"
                }}
              ]
            }
        """.trimIndent()

        val parsed = ParkingLogic.parseOverpass(raw, collioure)

        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.name == "Parking du Douy" && it.fee == ParkingFee.PAID })
        assertTrue(parsed.any { it.capacity == 230 && it.supervised == true })
        assertFalse(parsed.any { it.name == "Privat" || it.name == "Straßenrand" })
    }

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
