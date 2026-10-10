package com.vpr.screenlate.overlay.ui

import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.max

/**
 * The bubble's window and what it shows, in screen pixels: the disc's center and the part of the disc that shows, the
 * whole disc when [shown] is null. The window may be larger than what shows, while it is on its way to another frame.
 */
data class BubbleFrame(val window: DockPlacement.Window, val centerX: Float, val centerY: Float, val shown: Box?)

/**
 * Takes the bubble's window to the frames it is given without changing its size and its position in one update. The
 * window manager animates a window that moves and changes size at once (about 400 ms): a bubble pulled out of the
 * dock, whose window holds only the part that shows, stayed at the edge behind the finger and then flew to it.
 *
 * Such a frame is reached in [steps], one window update each: [update] applies a step, and [afterUpdate] runs the next
 * one once the window manager has laid out the window at that step. A new frame while stepping takes over from the
 * step the window is at.
 */
class BubbleWindowMover(
    initial: BubbleFrame,
    private val update: (BubbleFrame) -> Unit,
    private val afterUpdate: (next: () -> Unit) -> Unit,
) {
    /** The frame the window is at. */
    var current: BubbleFrame = initial
        private set

    /** The frame the window goes to; [current] once it is there. */
    var target: BubbleFrame = initial
        private set

    private var waiting = false

    fun moveTo(frame: BubbleFrame) {
        target = frame
        if (!waiting) step()
    }

    private fun step() {
        val next = steps(current, target).first()
        if (next != current) {
            current = next
            update(next)
        }
        if (next != target) {
            waiting = true
            afterUpdate {
                waiting = false
                step()
            }
        }
    }

    companion object {
        /**
         * The frames from [from] to [to], each changing the window's size or its position, never both. The window first
         * grows at its place to hold both frames' sizes, still showing [from]; then it moves, showing [to]; then it
         * shrinks to [to]'s size. Each step's window holds what it shows.
         */
        fun steps(from: BubbleFrame, to: BubbleFrame): List<BubbleFrame> {
            val a = from.window
            val b = to.window
            val sameSize = a.width == b.width && a.height == b.height
            if (sameSize || a.x == b.x && a.y == b.y) return listOf(to)
            val width = max(a.width, b.width)
            val height = max(a.height, b.height)
            return buildList {
                if (width != a.width || height != a.height) add(from.copy(window = a.copy(width = width, height = height)))
                add(to.copy(window = b.copy(width = width, height = height)))
                if (width != b.width || height != b.height) add(to)
            }
        }
    }
}
