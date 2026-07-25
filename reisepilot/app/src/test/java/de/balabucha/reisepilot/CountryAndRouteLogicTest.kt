package de.balabucha.reisepilot

import org.junit.Assert.*
import org.junit.Test

class CountryAndRouteLogicTest {
    @Test
    fun regionDetection_respectsConfiguredRadius() {
        assertNull(DestinationCatalog.nearestRegion(GeoPoint(53.64, 11.40)))
        assertEquals(TravelRegion.CANET, DestinationCatalog.nearestRegion(GeoPoint(42.70, 3.02)))
        assertEquals(TravelRegion.PARIS, DestinationCatalog.nearestRegion(GeoPoint(48.86, 2.35)))
    }

    @Test
    fun countries_areResolvedWithoutLatitudeShortcut() {
        assertEquals(FuelCountry.GERMANY, CountryResolver.country(GeoPoint(53.64, 11.40)))
        assertEquals(FuelCountry.FRANCE, CountryResolver.country(GeoPoint(48.8566, 2.3522)))
        assertEquals(FuelCountry.FRANCE, CountryResolver.country(GeoPoint(42.7069, 3.0182)))
        assertEquals(FuelCountry.SPAIN, CountryResolver.country(GeoPoint(41.3874, 2.1686)))
        assertEquals(FuelCountry.ANDORRA, CountryResolver.country(GeoPoint(42.5063, 1.5218)))
    }

    @Test
    fun routeDistance_usesProgressAndRejectsPassedTargets() {
        val route = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.10))
        val ahead = Geo.distanceAheadOnRouteM(GeoPoint(0.0, 0.02), GeoPoint(0.0, 0.08), route)
        assertNotNull(ahead)
        assertTrue(ahead!! in 6_500.0..6_900.0)
        assertNull(Geo.distanceAheadOnRouteM(GeoPoint(0.0, 0.08), GeoPoint(0.0, 0.02), route))
    }

    @Test
    fun motorwayFilter_isConservativeButKeepsAutohof() {
        assertTrue(MotorwayFilter.isMotorwayService("Tank & Rast", "Raststätte A7"))
        assertTrue(MotorwayFilter.isMotorwayService("Serways Raststätte", "A9"))
        assertFalse(MotorwayFilter.isMotorwayService("Shell Autohof", "Gewerbepark 1"))
    }
}
