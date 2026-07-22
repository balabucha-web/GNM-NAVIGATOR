package de.balabucha.reisepilot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReisePilotUserFlowTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun pageList(title: String) = compose.onNodeWithTag(
        "page-list:$title",
        useUnmergedTree = true
    )

    private fun clickControl(label: String) {
        val direct = compose.onAllNodes(
            hasText(label) and hasClickAction(),
            useUnmergedTree = true
        )
        val directNodes = direct.fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (directNodes.isNotEmpty()) {
            direct[0].performClick()
        } else {
            compose.onNode(
                hasClickAction() and hasAnyDescendant(hasText(label)),
                useUnmergedTree = true
            ).performClick()
        }
        compose.waitForIdle()
    }

    private fun clickTab(label: String) = clickControl(label)

    private fun swipeUp(times: Int) {
        repeat(times) {
            pageList("Entdecken").performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private fun swipeDown(times: Int) {
        repeat(times) {
            pageList("Entdecken").performTouchInput { swipeDown() }
            compose.waitForIdle()
        }
    }

    private fun findTagByScrolling(tag: String) {
        pageList("Entdecken").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun primaryUserJourney_remainsStable_andCachesRealDestinationGallery() {
        pageList("Start").assertIsDisplayed()
        if (java.time.Instant.now().isBefore(VACATION_DEPARTURE.toInstant())) {
            compose.onNodeWithTag("vacation-countdown", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Samstag, 25. Juli 2026 · 09:00 Uhr")
                .assertIsDisplayed()
        }

        // Exercise the start action without making the UI audit depend on an emulator GPS fix.
        val startLabel = if (java.time.Instant.now().isBefore(VACATION_DEPARTURE.toInstant())) {
            "Testfahrt starten"
        } else {
            "Reise starten"
        }
        pageList("Start").performScrollToNode(hasText(startLabel))
        clickControl(startLabel)
        Thread.sleep(2_000)
        compose.activity.serviceAction(TripTrackingService.ACTION_STOP)
        compose.waitForIdle()

        // Reproduce the reported accidental 10 km test drive and verify the
        // user-facing reset clears only the journey state.
        val testDrive = TripSnapshot(
            active = false,
            stage = Stage.SATURDAY,
            tripMode = TripMode.TEST,
            driveMinutes = 14,
            distanceTravelledKm = 10.0,
            fuelLitres = 59.2,
            nextTitle = "Tracking beendet",
            nextDetail = "Testfahrt"
        )
        compose.activity.getSharedPreferences("trip_state", Context.MODE_PRIVATE).edit()
            .putString("snapshot", testDrive.json().toString())
            .commit()
        compose.activity.sendBroadcast(Intent(TripTrackingService.ACTION_UPDATE).apply {
            setPackage(compose.activity.packageName)
            putExtra("snapshot", testDrive.json().toString())
        })
        clickTab("Route")
        compose.onNodeWithText("Route & Karte").assertIsDisplayed()
        Thread.sleep(2_000)
        compose.waitForIdle()

        clickTab("Entdecken")
        compose.onNodeWithText("Ziel suchen").assertIsDisplayed()

        val selectedPlace = DestinationCatalog.places.first { it.title == "Collioure" }
        val fixtureDirectory = File(compose.activity.cacheDir, "gallery-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val fixturePhotos = listOf(Color.BLUE, Color.CYAN, Color.YELLOW).mapIndexed { index, color ->
            val file = File(fixtureDirectory, "collioure-$index.png")
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).run {
                eraseColor(color)
                file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                recycle()
            }
            Uri.fromFile(file).toString()
        }
        WikiImageResolver.debugGalleryOverride = { place, limit ->
            if (place.title == selectedPlace.title) fixturePhotos.take(limit) else emptyList()
        }
        val parkingFixture = ParkingSpot(
            id = "test:parking:collioure",
            name = "Parking du Cap Dourats",
            point = GeoPoint(42.5261364, 3.0689640),
            kind = ParkingKind.SURFACE,
            distanceToDestinationM = 1_160,
            fee = ParkingFee.PAID,
            capacity = 230,
            recommended = true,
            note = "Testempfehlung"
        )
        ParkingResolver.debugOverride = {
            ParkingSearchResult(listOf(parkingFixture), System.currentTimeMillis())
        }

        val cardTag = "destination-card:${selectedPlace.region.name}:${selectedPlace.title}"
        findTagByScrolling(cardTag)
        compose.onNodeWithTag(cardTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Route in Google Maps").assertIsDisplayed()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("1 / 4", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithContentDescription(
            "Collioure · Foto 2",
            useUnmergedTree = true
        ).assertExists()
        compose.onNodeWithText("1 / 4").assertExists()
        compose.onNodeWithTag("parking-section", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithText("Parking du Cap Dourats").assertIsDisplayed()
        compose.onNodeWithTag("parking-save:${parkingFixture.id}", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            check(ParkingSelectionStore.selectedFor(compose.activity, selectedPlace)?.spot?.id == parkingFixture.id)
        }
        compose.onNodeWithText("Von Karte entfernen").assertIsDisplayed()
        compose.onNodeWithTag("parking-save:${parkingFixture.id}", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            check(ParkingSelectionStore.selectedFor(compose.activity, selectedPlace) == null)
        }
        WikiImageResolver.debugGalleryOverride = null
        ParkingResolver.debugOverride = null
        compose.onNodeWithText("Zurück zur Liste").performScrollTo().performClick()
        compose.waitForIdle()
        pageList("Entdecken").assertExists()
        compose.onNodeWithTag(cardTag, useUnmergedTree = true).assertIsDisplayed()

        clickTab("Mehr")
        compose.onNodeWithText("Reiseplan").assertIsDisplayed()
        compose.onNodeWithText("Buchungen").assertIsDisplayed()
        pageList("Mehr").performScrollToNode(hasTestTag("technical-settings-button"))
        compose.onNodeWithTag("technical-settings-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("technical-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("technical-settings-screen", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Karte und Live-Verkehr").assertIsDisplayed()
        pageList("Einstellungen").performScrollToNode(hasTestTag("check-live-sources"))
        compose.onNodeWithTag("check-live-sources", useUnmergedTree = true).assertIsDisplayed()
        pageList("Einstellungen").performScrollToNode(hasTestTag("reset-test-trip"))
        compose.onNodeWithTag("reset-test-trip", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Testfahrt löschen?").assertIsDisplayed()
        compose.onNodeWithTag("confirm-reset-test-trip", useUnmergedTree = true).performClick()
        compose.waitUntil(8_000) {
            val raw = compose.activity.getSharedPreferences("trip_state", Context.MODE_PRIVATE)
                .getString("snapshot", null)
            val reset = TripSnapshot.fromJson(raw)
            !reset.active && reset.distanceTravelledKm == 0.0 && reset.driveMinutes == 0
        }
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        pageList("Mehr").performScrollToNode(hasText("System und Fahrzeug"))
        compose.onNodeWithText("System und Fahrzeug").assertIsDisplayed()
    }

    @Test
    fun destinationList_survivesHeavyScrollingAndFiltering() {
        clickTab("Entdecken")
        compose.onNodeWithText("Ziel suchen").assertIsDisplayed()
        repeat(4) {
            swipeUp(12)
            swipeDown(12)
        }
        clickTab("Route")
        compose.onNodeWithText("Route & Karte").assertIsDisplayed()
        clickTab("Entdecken")

        clickControl("Barcelona")
        repeat(3) {
            swipeUp(10)
            swipeDown(10)
        }
        clickTab("Route")
        compose.onNodeWithText("Route & Karte").assertIsDisplayed()
    }
}
