package de.balabucha.reisepilot

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackingResponsiveLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPackingState() {
        compose.activity.getSharedPreferences(PackingRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun packingCoreControlsRemainReachable() {
        compose.onAllNodes(hasText("Mehr") and hasClickAction(), useUnmergedTree = true)[0].performClick()
        compose.onNodeWithTag("page-list:Mehr", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("open-packing-list"))
        compose.onNodeWithTag("open-packing-list", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("packing-total-progress", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("packing-new-item", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("packing-list", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("packing-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("packing-collapse:packing-v1-cat-01-documents"))
        compose.onNodeWithTag(
            "packing-collapse:packing-v1-cat-01-documents",
            useUnmergedTree = true
        ).performClick()
        compose.onNodeWithTag("packing-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("packing-checkbox:packing-v1-cat-01-documents-item-001"))
        compose.onNodeWithTag(
            "packing-checkbox:packing-v1-cat-01-documents-item-001",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }
}
