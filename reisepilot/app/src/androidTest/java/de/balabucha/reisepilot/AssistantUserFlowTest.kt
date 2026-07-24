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
    fun assistantWorksOfflineAndOffersVoiceAndSecureSetup() {
        compose.onNodeWithTag("open-reise-assistant", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Reise-Assistent").assertIsDisplayed()
        compose.onNodeWithText("LOKALER MODUS").assertIsDisplayed()
        compose.onNodeWithTag("assistant-voice", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("assistant-run", useUnmergedTree = true).performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Drei passende Ziele"), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        compose.onNodeWithText("Drei passende Ziele").assertIsDisplayed()

        compose.onNodeWithTag("assistant-settings", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Direkt", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Proxy", useUnmergedTree = true).assertExists()
    }
}