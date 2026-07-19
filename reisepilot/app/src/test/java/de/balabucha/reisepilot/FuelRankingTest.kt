package de.balabucha.reisepilot

import org.junit.Assert.*
import org.junit.Test

class FuelRankingTest {
    private val current = GeoPoint(48.0000, 6.0000)
    private val destination = GeoPoint(47.0000, 5.0000)
    private val route = listOf(
        current,
        GeoPoint(47.8, 5.8),
        GeoPoint(47.6, 5.6),
        GeoPoint(47.4, 5.4),
        destination
    )

    @Test
    fun motorwayStationIsAlwaysExcluded() {
        val motorway = FuelCandidate(
            name = "Autobahn teuer",
            address = "A7",
            point = GeoPoint(47.8, 5.8),
            dieselPrice = 1.40,
            motorway = true,
            source = "test"
        )
        val local = FuelCandidate(
            name = "Ort günstig",
            address = "Dorfstraße",
            point = GeoPoint(47.79, 5.79),
            dieselPrice = 1.70,
            motorway = false,
            source = "test"
        )

        val result = FuelRanking.best(current, destination, route, listOf(motorway, local), 7.4)
        assertEquals("Ort günstig", result?.name)
    }

    @Test
    fun smallDetourCanBeatTinyPriceSavingFarAway() {
        val close = FuelCandidate(
            name = "Nah",
            address = "Ort 1",
            point = GeoPoint(47.80, 5.80),
            dieselPrice = 1.65,
            motorway = false,
            source = "test"
        )
        val farOffRoute = FuelCandidate(
            name = "Weit",
            address = "Ort 2",
            point = GeoPoint(47.80, 5.72),
            dieselPrice = 1.64,
            motorway = false,
            source = "test"
        )

        val result = FuelRanking.best(current, destination, route, listOf(close, farOffRoute), 7.4)
        assertEquals("Nah", result?.name)
    }

    @Test
    fun significantlyCheaperStationWinsWhenDetourIsReasonable() {
        val expensive = FuelCandidate(
            name = "Teuer nah",
            address = "Ort 1",
            point = GeoPoint(47.80, 5.80),
            dieselPrice = 1.85,
            motorway = false,
            source = "test"
        )
        val cheap = FuelCandidate(
            name = "Günstig",
            address = "Ort 2",
            point = GeoPoint(47.80, 5.77),
            dieselPrice = 1.55,
            motorway = false,
            source = "test"
        )

        val result = FuelRanking.best(current, destination, route, listOf(expensive, cheap), 7.4)
        assertEquals("Günstig", result?.name)
    }

    @Test
    fun stationBehindCurrentDirectionIsRejected() {
        val behind = FuelCandidate(
            name = "Zurück",
            address = "Hinter dem Start",
            point = GeoPoint(48.2, 6.2),
            dieselPrice = 1.20,
            motorway = false,
            source = "test"
        )
        val ahead = FuelCandidate(
            name = "Voraus",
            address = "Richtung Ziel",
            point = GeoPoint(47.8, 5.8),
            dieselPrice = 1.70,
            motorway = false,
            source = "test"
        )

        val result = FuelRanking.best(current, destination, route, listOf(behind, ahead), 7.4)
        assertEquals("Voraus", result?.name)
    }
}
