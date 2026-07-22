package de.balabucha.reisepilot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParkingParserDeviceTest {
    @Test
    fun overpassParking_isParsedAndUnsafeOrStreetOnlyResultsAreRemoved() {
        val place = DestinationCatalog.places.first { it.title == "Collioure" }
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

        val parsed = ParkingLogic.parseOverpass(raw, place)

        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.name == "Parking du Douy" && it.fee == ParkingFee.PAID })
        assertTrue(parsed.any { it.capacity == 230 && it.supervised == true })
        assertFalse(parsed.any { it.name == "Privat" || it.name == "Straßenrand" })
    }

    @Test
    fun overloadedOverpassPayload_isRejectedSoAnotherEndpointCanRun() {
        val place = DestinationCatalog.places.first { it.title == "Collioure" }
        assertThrows(IllegalStateException::class.java) {
            ParkingLogic.parseOverpass(
                """{"remark":"runtime error: Query timed out in dispatcher","elements":[]}""",
                place
            )
        }
        assertEquals(
            listOf("overpass-api.de", "maps.mail.ru", "overpass.private.coffee"),
            ParkingResolver.overpassEndpoints().map { java.net.URL(it).host }
        )
    }

    @Test
    fun bundledParkingFallback_coversEveryDestination() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(93, OfflineParkingCatalog.coveredDestinationCount(context))
    }

    @Test
    fun bundledDestinationPhotos_coverEveryDestination() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(DestinationOfflinePhotoCatalog.hasEveryDestination(context))
    }
}
