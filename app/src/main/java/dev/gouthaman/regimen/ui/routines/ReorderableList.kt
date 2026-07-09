package dev.gouthaman.regimen.ui.routines

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel

/**
 * Self-contained drag-to-reorder support for a [LazyListState]-backed list. No third-party
 * dependency: the caller owns a mutable working list, [onMove] applies each in-flight swap to it,
 * and [Modifier.dragHandle] wires a handle's drag gestures to this state. The item currently being
 * dragged is offset visually via [draggingItemIndex] / [draggingItemOffset].
 */
class DragDropState internal constructor(
    private val state: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal val scrollChannel = Channel<Float>()

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal fun onDragStart(index: Int) {
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.also {
            draggingItemIndex = index
            draggingItemInitialOffset = it.offset
        }
    }

    internal fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
        draggingItemInitialOffset = 0
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val target = state.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                draggingItem.index != item.index
        }

        if (target != null) {
            onMove(draggingItem.index, target.index)
            draggingItemIndex = target.index
            draggingItemInitialOffset += target.offset - draggingItem.offset
        } else {
            // Near a viewport edge with nowhere to swap → autoscroll to reveal more.
            val startToTop = startOffset - state.layoutInfo.viewportStartOffset
            val endToBottom = endOffset - state.layoutInfo.viewportEndOffset
            val scroll = when {
                draggingItemDraggedDelta > 0 && endToBottom > 0 -> endToBottom
                draggingItemDraggedDelta < 0 && startToTop < 0 -> startToTop
                else -> 0f
            }
            if (scroll != 0f) scrollChannel.trySend(scroll)
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): DragDropState {
    val latestOnMove by rememberUpdatedState(onMove)
    val state = remember(lazyListState) {
        DragDropState(lazyListState) { from, to -> latestOnMove(from, to) }
    }
    LaunchedEffect(state) {
        while (true) {
            val diff = state.scrollChannel.receive()
            lazyListState.scrollBy(diff)
        }
    }
    return state
}

/**
 * Attach to a drag-handle composable. [index] is the item's current index; [onDragStopped] fires
 * once the gesture ends (drop or cancel) so the caller can persist the final order.
 *
 * The gesture detector is keyed only on [state] — never on [index] — because a reorder changes an
 * item's index mid-drag, and re-keying `pointerInput` there would cancel the in-flight gesture and
 * swallow the pointer-up (leaving the item stuck lifted). We read the latest index/callback via
 * [rememberUpdatedState] instead. Only the index at drag *start* matters; subsequent swaps are
 * tracked inside [DragDropState].
 */
fun Modifier.dragHandle(
    state: DragDropState,
    index: Int,
    onDragStopped: () -> Unit,
): Modifier = composed {
    val currentIndex by rememberUpdatedState(index)
    val currentOnStopped by rememberUpdatedState(onDragStopped)
    Modifier.pointerInput(state) {
        detectDragGestures(
            onDragStart = { state.onDragStart(currentIndex) },
            onDragEnd = {
                state.onDragInterrupted()
                currentOnStopped()
            },
            onDragCancel = {
                state.onDragInterrupted()
                currentOnStopped()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount)
            },
        )
    }
}
