package de.balabucha.reisepilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
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
        compose.onNodeWithText("Sprechen & fragen").assertIsDisplayed()
        compose.onNodeWithTag("assistant-question-55", useUnmergedTree = true)
            .performTextInput("Was passt heute?")

        val runButtons = compose.onAllNodes(
            hasText("Was passt heute?") and hasClickAction(),
            useUnmergedTree = true
        )
        val buttonCount = runButtons.fetchSemanticsNodes(atLeastOneRootRequired = false).size
        assertTrue("Kein ausführbarer Assistenten-Button gefunden", buttonCount >= 1)
        runButtons[buttonCount - 1].performClick()
        compose.waitForIdle()

        val page = compose.onNodeWithTag("page-list:Reise-Assistent", useUnmergedTree = true)
        var settingsFound = false
        for (attempt in 0 until 16) {
            settingsFound = compose.onAllNodesWithText("AI-Modus und API-Key", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
            if (settingsFound) break
            page.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        assertTrue("AI-Einstellungen wurden nach dem Scrollen nicht gefunden", settingsFound)

        compose.onNodeWithText("Einrichten", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Lokal", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Direkt", useUnmergedTree = true).assertExists().performClick()
        compose.onNodeWithText("Proxy", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("OpenAI API-Key", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Key speichern", useUnmergedTree = true).assertExists()
    }
}
