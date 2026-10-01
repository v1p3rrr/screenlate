package com.vpr.screenlate.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * Shows content drawn from a last known state while that state is checked again: taps do not reach it, so nothing
 * changes under the finger, and when the check takes longer than a moment it is dimmed. The layout stays as it is,
 * so nothing jumps when the check ends; a quick check leaves no visible trace.
 */
fun Modifier.pending(pending: Boolean): Modifier = composed {
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        dimmed = false
        if (pending) {
            delay(DIM_AFTER_MS)
            dimmed = true
        }
    }
    val dim = if (pending && dimmed) alpha(PENDING_ALPHA) else Modifier
    if (!pending) {
        dim
    } else {
        dim.pointerInput(Unit) {
            awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}

private const val DIM_AFTER_MS = 300L
private const val PENDING_ALPHA = 0.5f
