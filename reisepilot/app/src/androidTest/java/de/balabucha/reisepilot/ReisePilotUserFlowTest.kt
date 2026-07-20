package de.balabucha.reisepilot

import android.Manifest
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
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private fun swipeDown(times: Int) {
        repeat(times) {
            compose.onRoot().performTouchInput { swipeDown() }
            compose.waitForIdle()
        }
    }

    private fun findByScrolling(text: String, maxSwipes: Int = 18) {
        repeat(maxSwipes + 1) {
            if (compose.onAllNodesWithText(text, substring = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onNodeWithText(text, substring = true).assertExists()
    }

    @Test
    fun primaryUserJourney_remainsStable_andCachesRealDestinationGallery() {
        compose.onNodeWithText("ReisePilot").assertIsDisplayed()
        clickControl("Fahrt starten")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Tracking aktiv")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        clickControl("Tracking stoppen")

        clickTab("Route")
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
        Thread.sleep(2_000)
        compose.waitForIdle()

        clickTab("Entdecken")
        compose.onNodeWithText("Ziel suchen").assertIsDisplayed()
        swipeUp(16)
        clickTab("Route")
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
        clickTab("Entdecken")
        compose.onNodeWithText("Ziel suchen").assertIsDisplayed()

        val selectedPlace = DestinationCatalog.places.first { it.title == "Strand & Promenade Canet" }
        val photoDirectory = File(compose.activity.cacheDir, "travel_photos_v44")
        photoDirectory.deleteRecursively()

        findByScrolling(selectedPlace.title)
        clickControl(selectedPlace.title)
        compose.onNodeWithText("Route in Google Maps").assertIsDisplayed()
        compose.waitUntil(75_000) {
            WikiImageResolver.cachedPhotoCount(compose.activity, selectedPlace) >= 2
        }
        clickControl("Zurück zur Liste")
        compose.onNodeWithText("Ziel suchen").assertExists()

        clickTab("Mehr")
        compose.onNodeWithText("Reiseplan").assertIsDisplayed()
        compose.onNodeWithText("Buchungen").assertIsDisplayed()
        findByScrolling("Technische Einstellungen")
        compose.onNodeWithTag("technical-settings-button", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("technical-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("technical-settings-screen", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Mapbox-Kartenzugang").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithText("System und Fahrzeug").assertExists()
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
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
        clickTab("Entdecken")

        clickControl("Barcelona")
        repeat(3) {
            swipeUp(10)
            swipeDown(10)
        }
        clickTab("Route")
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
    }
}
