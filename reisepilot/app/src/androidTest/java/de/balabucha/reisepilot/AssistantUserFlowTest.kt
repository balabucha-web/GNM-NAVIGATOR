package de.balabucha.reisepilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        compose.onNodeWithText("LOKALER MODUS").assertIsDisplayed()
        compose.onNodeWithTag("assistant-voice", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("assistant-run", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // Die Antwortkarten verlängern die LazyColumn dynamisch. Deshalb wie ein
        // Nutzer schrittweise bis zum Einstellungsbereich scrollen, statt einen
        // noch nicht komponierten Lazy-List-Knoten direkt anzuspringen.
        val page = compose.onNodeWithTag("page-list:Reise-Assistent", useUnmergedTree = true)
        var settingsFound = false
        for (attempt in 0 until 14) {
            settingsFound = compose.onAllNodes(hasTestTag("assistant-settings"), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
            if (settingsFound) break
            page.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        assertTrue("AI-Einstellungen wurden nach dem Scrollen nicht gefunden", settingsFound)

        compose.onNodeWithTag("assistant-settings", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Lokal", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Direkt", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Proxy", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Antworten automatisch vorlesen", useUnmergedTree = true).assertExists()
    }
}