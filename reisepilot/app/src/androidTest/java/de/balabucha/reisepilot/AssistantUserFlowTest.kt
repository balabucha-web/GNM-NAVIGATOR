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
        compose.waitForIdle()

        // Der lokale Assistent arbeitet synchron aus den App-Daten. Entscheidend für
        // diesen UI-Test ist, dass die Auswertung die Oberfläche nicht beendet oder
        // blockiert und anschließend alle Sicherheitsmodi erreichbar bleiben.
        compose.onNodeWithTag("page-list:Reise-Assistent", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("assistant-settings", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.onNodeWithText("Lokal", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Direkt", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Proxy", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Antworten automatisch vorlesen", useUnmergedTree = true).assertExists()
    }
}