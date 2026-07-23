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

    private fun swipePage(title: String, up: Boolean, times: Int) {
        repeat(times) {
            pageList(title).performTouchInput {
                if (up) swipeUp() else swipeDown()
            }
            compose.waitForIdle()
        }
    }

    @Test
    fun primaryUserJourney_remainsStable_onVisual50Navigation() {
        pageList("Start").assertIsDisplayed()
        compose.onNodeWithText("Dein Reise-Cockpit").assertIsDisplayed()
        compose.onNodeWithText("Etappe wählen").assertIsDisplayed()
        compose.onNodeWithText("Fahrt auf einen Blick").assertExists()

        val startLabel = if (java.time.Instant.now().isBefore(VACATION_DEPARTURE.toInstant())) {
            "Testfahrt starten"
        } else {
            "Reise starten"
        }
        pageList("Start").performScrollToNode(hasText(startLabel))
        clickControl(startLabel)
        Thread.sleep(1_500)
        compose.activity.serviceAction(TripTrackingService.ACTION_STOP)
        compose.waitForIdle()

        clickTab("Karte")
        compose.onNodeWithText("Route & Karte").assertIsDisplayed()

        clickTab("Ziele")
        pageList("Ziele").assertIsDisplayed()
        compose.onNodeWithText("Ziel suchen").assertIsDisplayed()

        val selectedPlace = DestinationCatalog.places.first { it.title == "Collioure" }
        val cardTag = destinationCardTag(selectedPlace)
        pageList("Ziele").performScrollToNode(hasTestTag(cardTag))
        compose.onNodeWithTag(cardTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Route in Google Maps").assertIsDisplayed()
        compose.onNodeWithText("Zurück zur Liste").performClick()
        compose.waitForIdle()

        clickTab("Packliste")
        compose.onNodeWithText("Packliste", useUnmergedTree = true).assertExists()

        clickTab("Mehr")
        pageList("Mehr").assertIsDisplayed()
        compose.onNodeWithText("Reiseplan").assertIsDisplayed()
        compose.onNodeWithText("Buchungen").assertIsDisplayed()
        pageList("Mehr").performScrollToNode(hasTestTag("technical-settings-button"))
        compose.onNodeWithTag("technical-settings-button", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("technical-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("Karte und Live-Verkehr").assertIsDisplayed()
    }

    @Test
    fun visualDestinationList_survivesHeavyScrollingAndFiltering() {
        clickTab("Ziele")
        pageList("Ziele").assertIsDisplayed()
        compose.onNodeWithText("Ziel suchen").assertIsDisplayed()

        repeat(3) {
            swipePage("Ziele", up = true, times = 8)
            swipePage("Ziele", up = false, times = 8)
        }

        clickControl("Barcelona")
        compose.onNodeWithText("Barcelona", useUnmergedTree = true).assertExists()
        repeat(2) {
            swipePage("Ziele", up = true, times = 8)
            swipePage("Ziele", up = false, times = 8)
        }

        clickTab("Karte")
        compose.onNodeWithText("Route & Karte").assertIsDisplayed()
        clickTab("Ziele")
        pageList("Ziele").assertIsDisplayed()
    }
}
