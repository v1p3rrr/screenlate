package com.vpr.screenlate.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * A column whose items are reordered by dragging a handle. Meant for short lists inside a scrolling screen;
 * it is not lazy.
 *
 * @param onReorder called once when a drag ends, with the new order.
 * @param itemContent receives the modifier to put on the drag handle and whether the item is being dragged.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    itemContent: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    // Gesture handlers outlive recompositions, so everything they touch is a stable state holder.
    val state = remember { ReorderState(items) }
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }

    LaunchedEffect(items) {
        if (state.draggedKey == null) state.order = items
    }

    fun moveBy(itemKey: Any, delta: Float) {
        state.dragOffset += delta
        val order = state.order
        val index = order.indexOfFirst { currentKey(it) == itemKey }
        if (index < 0) return
        val neighbor = if (state.dragOffset > 0) index + 1 else index - 1
        if (neighbor !in order.indices) return
        val step = (state.heights[currentKey(order[neighbor])] ?: return) + spacingPx
        if (abs(state.dragOffset) > step / 2f) {
            state.order = order.toMutableList().apply { add(neighbor, removeAt(index)) }
            state.dragOffset += if (neighbor > index) -step else step
        }
    }

    fun finish() {
        state.draggedKey = null
        state.dragOffset = 0f
        if (state.order != currentItems) currentOnReorder(state.order)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        state.order.forEach { item ->
            val itemKey = key(item)
            key(itemKey) {
                val dragging = itemKey == state.draggedKey
                val handle = Modifier.pointerInput(itemKey) {
                    detectDragGestures(
                        onDragStart = {
                            state.draggedKey = itemKey
                            state.dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            moveBy(itemKey, amount.y)
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                    )
                }
                Box(
                    modifier = Modifier
                        .onSizeChanged { state.heights[itemKey] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) state.dragOffset else 0f },
                ) {
                    itemContent(item, handle, dragging)
                }
            }
        }
    }
}

private class ReorderState<T>(initial: List<T>) {
    var order by mutableStateOf(initial)
    var draggedKey by mutableStateOf<Any?>(null)
    var dragOffset by mutableFloatStateOf(0f)
    val heights = mutableStateMapOf<Any, Int>()
}
