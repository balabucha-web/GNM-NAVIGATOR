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

    private fun clickableText(label: String): SemanticsMatcher =
        hasText(label) and hasClickAction()

    private fun clickTab(label: String) {
        compose.onNode(clickableText(label), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

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
    fun primaryUserJourney_remainsStable() {
        compose.onNodeWithText("ReisePilot").assertIsDisplayed()
        compose.onNodeWithText("Fahrt starten").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Tracking aktiv")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("Tracking stoppen").performClick()

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

        findByScrolling("Strand & Promenade Canet")
        compose.onNodeWithText("Strand & Promenade Canet").performClick()
        compose.onNodeWithText("Route in Google Maps").assertIsDisplayed()
        Thread.sleep(2_000)
        compose.onNodeWithText("Zurück zur Liste").performClick()
        compose.onNode(clickableText("Entdecken"), useUnmergedTree = true).assertExists()

        clickTab("Mehr")
        compose.onNodeWithText("Reiseplan").assertIsDisplayed()
        compose.onNodeWithText("Buchungen").assertIsDisplayed()
        findByScrolling("Technische Einstellungen")
        compose.onNodeWithText("Technische Einstellungen").performClick()
        compose.onNodeWithText("Mapbox-Kartenzugang").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
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
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
        clickTab("Entdecken")

        compose.onNode(clickableText("Barcelona"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        repeat(3) {
            swipeUp(10)
            swipeDown(10)
        }
        clickTab("Route")
        compose.onNodeWithText("Live-Karte").assertIsDisplayed()
    }
}
