package de.balabucha.reisepilot

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.rules.GrantPermissionRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun clickTab(label: String) {
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun scrollUp(times: Int) {
        repeat(times) {
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private fun scrollDown(times: Int) {
        repeat(times) {
            compose.onRoot().performTouchInput { swipeDown() }
            compose.waitForIdle()
        }
    }

    @Test
    fun completeUserFlow_remainsStable() {
        compose.onNodeWithText("ReisePilot").assertIsDisplayed()
        compose.onNodeWithText("Fahrt starten").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Tracking aktiv").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tracking stoppen").performClick()

        clickTab("Route")
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()

        clickTab("Entdecken")
        compose.onNodeWithText("Entdecken").assertIsDisplayed()
        scrollUp(12)
        compose.onNodeWithText("Entdecken").assertExists()
        scrollDown(12)
        compose.onNodeWithText("Entdecken").assertIsDisplayed()

        scrollUp(2)
        compose.onNodeWithText("Markt Canet-Plage").performClick()
        compose.onNodeWithText("Route in Google Maps").assertIsDisplayed()
        compose.onNodeWithText("Zurück zur Liste").performClick()
        compose.onNodeWithText("Entdecken").assertIsDisplayed()

        clickTab("Mehr")
        compose.onNodeWithText("Reiseplan").assertIsDisplayed()
        compose.onNodeWithText("Buchungen").assertIsDisplayed()
        compose.onNodeWithText("Technische Einstellungen").performClick()
        compose.onNodeWithText("Mapbox-Kartenzugang").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Reiseplan").assertIsDisplayed()
    }

    @Test
    fun allDestinationRegions_canBeScrolledWithoutCrash() {
        clickTab("Entdecken")
        listOf("Canet & Umgebung", "Barcelona", "Andorra", "Paris").forEach { region ->
            scrollDown(20)
            compose.onNodeWithText(region, useUnmergedTree = true).performClick()
            compose.waitForIdle()
            scrollUp(10)
            scrollDown(10)
            compose.onNodeWithText("Entdecken").assertExists()
        }
    }
}
