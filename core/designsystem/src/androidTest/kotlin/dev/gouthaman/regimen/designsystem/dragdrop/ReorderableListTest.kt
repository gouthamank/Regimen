package dev.gouthaman.regimen.designsystem.dragdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val ITEM_HEIGHT = 64.dp

@Composable
private fun ReorderableTestList(
    initial: List<String>,
    onStateReady: (DragDropState) -> Unit = {},
    onOrderChanged: (List<String>) -> Unit = {},
    onDragStopped: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val working = remember { mutableStateListOf(*initial.toTypedArray()) }
    val dragState = rememberDragDropState(listState) { draggedKey, targetKey ->
        val from = working.indexOf(draggedKey)
        val to = working.indexOf(targetKey)
        if (from != -1 && to != -1) {
            working.add(to, working.removeAt(from))
            onOrderChanged(working.toList())
        }
    }
    LaunchedEffect(dragState) { onStateReady(dragState) }
    LazyColumn(state = listState) {
        itemsIndexed(working, key = { _, item -> item }) { index, item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ITEM_HEIGHT)
                    .testTag("item_$item")
                    .dragHandle(dragState, index, onDragStopped),
            ) {
                Text(item)
            }
        }
    }
}

class ReorderableListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun draggingAnItemPastTheNextOneSwapsTheirOrder() {
        var order = listOf("A", "B", "C")
        composeTestRule.setContent {
            ReorderableTestList(
                initial = listOf("A", "B", "C"),
                onOrderChanged = { order = it },
            )
        }

        val itemHeightPx = with(composeTestRule.density) { ITEM_HEIGHT.toPx() }
        composeTestRule.onNodeWithTag("item_A").performTouchInput {
            down(center)
            moveBy(Offset(0f, itemHeightPx * 1.2f))
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf("B", "A", "C"), order)
    }

    @Test
    fun draggingSetsTheDraggingItemKeyAndClearsItOnRelease() {
        var dragState: DragDropState? = null
        composeTestRule.setContent {
            ReorderableTestList(
                initial = listOf("A", "B", "C"),
                onStateReady = { dragState = it },
            )
        }
        composeTestRule.waitForIdle()
        assertNull(dragState!!.draggingItemKey)

        val dragDistancePx = with(composeTestRule.density) { 40.dp.toPx() }
        composeTestRule.onNodeWithTag("item_A").performTouchInput {
            down(center)
            moveBy(Offset(0f, dragDistancePx))
        }
        composeTestRule.waitForIdle()
        assertEquals("A", dragState!!.draggingItemKey)

        composeTestRule.onNodeWithTag("item_A").performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertNull(dragState!!.draggingItemKey)
    }

    @Test
    fun droppingAnItemInvokesOnDragStopped() {
        var stopped = false
        composeTestRule.setContent {
            ReorderableTestList(
                initial = listOf("A", "B", "C"),
                onDragStopped = { stopped = true },
            )
        }

        val dragDistancePx = with(composeTestRule.density) { 40.dp.toPx() }
        composeTestRule.onNodeWithTag("item_A").performTouchInput {
            down(center)
            moveBy(Offset(0f, dragDistancePx))
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue(stopped)
    }

    @Test
    fun draggingWithinTheSameItemDoesNotReorder() {
        var order = listOf("A", "B", "C")
        composeTestRule.setContent {
            ReorderableTestList(
                initial = listOf("A", "B", "C"),
                onOrderChanged = { order = it },
            )
        }

        val withinItemDragDistancePx = with(composeTestRule.density) { 24.dp.toPx() }
        composeTestRule.onNodeWithTag("item_A").performTouchInput {
            down(center)
            moveBy(Offset(0f, withinItemDragDistancePx))
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf("A", "B", "C"), order)
    }
}
