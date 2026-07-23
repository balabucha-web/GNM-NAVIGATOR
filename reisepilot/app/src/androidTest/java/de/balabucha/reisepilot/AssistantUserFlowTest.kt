package de.balabucha.reisepilot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Drei passende Ziele"), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        compose.onNodeWithText("Drei passende Ziele").assertIsDisplayed()
    }
}
