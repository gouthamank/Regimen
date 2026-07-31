package dev.gouthaman.regimen.designsystem.dragdrop

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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.channels.Channel

/**
 * Self-contained drag-to-reorder support for a [LazyListState]-backed list. The caller owns a
 * mutable working list; [onMove] applies each in-flight swap to it, and [Modifier.dragHandle]
 * wires a handle's drag gestures to this state.
 *
 * Tracks the dragged item by its stable **key**, not list index, and hands [onMove] keys rather
 * than indices - [LazyListState.layoutInfo] can be a frame stale relative to a just-applied swap,
 * so trusting indices would compound stale swaps into wildly wrong positions under fast drags.
 *
 * [draggableIndices] bounds which global item indices are valid swap targets/drag starts, for
 * lists with non-reorderable items (headers, trailing buttons) mixed in. Defaults to unbounded.
 */
class DragDropState internal constructor(
    private val state: LazyListState,
    private val draggableIndices: IntRange,
    private val haptics: HapticFeedback,
    private val onMove: (draggedKey: Any, targetKey: Any) -> Unit,
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    internal val scrollChannel = Channel<Float>()

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }

    internal fun onDragStart(index: Int) {
        if (index !in draggableIndices) return
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.also {
            draggingItemKey = it.key
            draggingItemInitialOffset = it.offset
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    internal fun onDragInterrupted() {
        draggingItemKey = null
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
                    draggingItem.key != item.key &&
                    item.index in draggableIndices
        }

        if (target != null) {
            // No offset recalibration here - draggingItemOffset's formula already self-corrects
            // as layout catches up; recalibrating on every match compounded incorrectly when
            // several matches hit the same stale layout snapshot, rocketing the item past the finger.
            onMove(draggingItem.key, target.key)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
    draggableIndices: IntRange = 0..Int.MAX_VALUE,
    onMove: (draggedKey: Any, targetKey: Any) -> Unit,
): DragDropState {
    val latestOnMove by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current
    val state = remember(lazyListState, draggableIndices) {
        DragDropState(lazyListState, draggableIndices, haptics) { a, b -> latestOnMove(a, b) }
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
 * once the gesture ends so the caller can persist the final order.
 *
 * Keyed only on [state], never [index] - re-keying on index would cancel the in-flight gesture
 * mid-drag (a reorder changes the index) and swallow the pointer-up. Latest index/callback are
 * read via [rememberUpdatedState] instead.
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
