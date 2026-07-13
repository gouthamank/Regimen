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
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel

/**
 * Self-contained drag-to-reorder support for a [LazyListState]-backed list. No third-party
 * dependency: the caller owns a mutable working list, [onMove] applies each in-flight swap to it,
 * and [Modifier.dragHandle] wires a handle's drag gestures to this state. The item currently being
 * dragged is offset visually via [draggingItemKey] / [draggingItemOffset].
 *
 * The dragged item is tracked by its stable item **key** (not its list index), and [onMove] is
 * handed the dragged/target *keys*, not indices, so the caller resolves them against its own
 * always-current working list. This matters because [LazyListState.layoutInfo] can be a
 * recomposition frame stale relative to a just-applied swap (state writes here don't relayout
 * synchronously) — if the caller trusted this class's *indices* for the next swap instead of
 * looking the keys up fresh, a burst of touch-move events arriving faster than layout catches up
 * would compound stale swaps into wildly wrong positions (observed as the dragged item rocketing
 * to the end of the list after the very first swap).
 *
 * [draggableIndices] bounds which *global* LazyColumn item indices are valid swap targets (and
 * valid drag starts) — needed whenever the list has non-reorderable items (headers, trailing
 * "add" buttons, etc.) alongside the reorderable ones, so a drag can't match one of those as a
 * target. Defaults to unbounded (every visible item is a valid target), which is correct when the
 * whole list is reorderable.
 */
class DragDropState internal constructor(
    private val state: LazyListState,
    private val draggableIndices: IntRange,
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
            // No offset recalibration here — draggingItemInitialOffset stays fixed at the value
            // captured once in onDragStart. draggingItemOffset's formula (initial position + total
            // raw finger delta - item's current reported offset) already self-corrects as layout
            // catches up, regardless of swap count; recalibrating on every match compounded
            // incorrectly when several matches hit the same stale layout snapshot (touch-move
            // outpacing recomposition/relayout), rocketing the dragged item past the finger.
            onMove(draggingItem.key, target.key)
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
    val state = remember(lazyListState, draggableIndices) {
        DragDropState(lazyListState, draggableIndices) { a, b -> latestOnMove(a, b) }
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
