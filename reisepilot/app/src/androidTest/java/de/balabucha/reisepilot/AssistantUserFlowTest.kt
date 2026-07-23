package de.balabucha.reisepilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantUserFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun assistantWorksOfflineWithoutShippingAnApiKey() {
        compose.onNodeWithTag("open-reise-assistant", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Reise-Assistent").assertIsDisplayed()
        compose.onNodeWithText("LOKALER MODUS").assertIsDisplayed()
        compose.onNodeWithTag("assistant-run", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("page-list:Reise-Assistent", useUnmergedTree = true)
            .performScrollToNode(hasText("Drei passende Ziele"))
        compose.onNodeWithText("Drei passende Ziele", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Lokal", useUnmergedTree = true).assertExists()
    }
}
