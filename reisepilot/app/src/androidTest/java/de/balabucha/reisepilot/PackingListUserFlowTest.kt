package de.balabucha.reisepilot

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackingListUserFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPackingState() {
        compose.activity.getSharedPreferences(PackingRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun clickControl(label: String) {
        val direct = compose.onAllNodes(
            hasText(label) and hasClickAction(),
            useUnmergedTree = true
        )
        val nodes = direct.fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (nodes.isNotEmpty()) {
            direct[0].performClick()
        } else {
            compose.onNode(
                hasClickAction() and hasAnyDescendant(hasText(label)),
                useUnmergedTree = true
            ).performClick()
        }
        compose.waitForIdle()
    }

    private fun openPackingList() {
        clickControl("Mehr")
        compose.onNodeWithTag("page-list:Mehr", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("open-packing-list"))
        compose.onNodeWithTag("open-packing-list", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("packing-screen", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun packingList_addCheckPersistDragAndStressScroll() {
        openPackingList()
        compose.onNodeWithText("0 von 268 eingepackt · 0 %").assertExists()

        val categoryId = "packing-v1-cat-01-documents"
        val firstItemId = "$categoryId-item-001"
        val secondItemId = "$categoryId-item-002"
        compose.onNodeWithTag("packing-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("packing-collapse:$categoryId"))
        compose.onNodeWithTag("packing-collapse:$categoryId", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("packing-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("packing-checkbox:$firstItemId"))
        compose.onNodeWithTag("packing-checkbox:$firstItemId", useUnmergedTree = true).performClick()

        compose.onNodeWithTag("packing-new-item", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("packing-editor-name", useUnmergedTree = true)
            .performTextInput("Test-Sonnenhut")
        compose.onNodeWithTag("packing-editor-quantity", useUnmergedTree = true)
            .performTextReplacement("2")
        compose.onNodeWithTag("packing-editor-note", useUnmergedTree = true)
            .performTextInput("griffbereit")
        compose.onNodeWithTag("packing-editor-save", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("packing-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("packing-search"))
        compose.onNodeWithTag("packing-search", useUnmergedTree = true)
            .performTextInput("Test-Sonnenhut")
        compose.onNode(hasText("Test-Sonnenhut") and !hasSetTextAction()).assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("packing-screen", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(hasText("Test-Sonnenhut") and !hasSetTextAction()).assertExists()
        val restarted = PackingRepository(compose.activity.applicationContext)
        assertEquals(269, restarted.state.items.size)
        assertEquals(1, restarted.state.items.count { it.checked })

        compose.onNodeWithTag("packing-search", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("packing-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("packing-item-drag:$firstItemId"))
        compose.onNodeWithTag("packing-item-drag:$firstItemId", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                advanceEventTime(650)
                moveTo(Offset(center.x, center.y + 120f), 250)
                up()
            }
        compose.waitForIdle()
        val afterDrag = PackingRepository(compose.activity.applicationContext).state.items
            .filter { it.categoryId == categoryId }
            .sortedBy { it.position }
        assertEquals(secondItemId, afterDrag.first().id)
        assertEquals(firstItemId, afterDrag[1].id)

        repeat(3) {
            repeat(16) {
                compose.onNodeWithTag("packing-list", useUnmergedTree = true)
                    .performTouchInput { swipeUp() }
            }
            repeat(16) {
                compose.onNodeWithTag("packing-list", useUnmergedTree = true)
                    .performTouchInput { swipeDown() }
            }
        }
        compose.onNodeWithTag("packing-screen", useUnmergedTree = true).assertExists()
    }
}
