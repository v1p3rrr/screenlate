package com.vpr.screenlate.overlay.ui

import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.settings.DockSide
import kotlin.math.roundToInt

/** Where the docked bubble's window goes, and which edge a dropped bubble docks at. */
object DockPlacement {

    /**
     * The screen as the dock sees it, in pixels.
     *
     * @property usable the screen without the system bars and the display cutout, whether the bars show or not.
     * @property gestures the screen without the mandatory system gesture areas, where a swipe from the edge goes to
     *   the system whatever window is under the finger: the notifications at the top, Home at the bottom with gesture
     *   navigation. In fullscreen apps too, where the swipe shows the bars or goes Home at once.
     * @property keyboardTop the top of an open keyboard; null without one.
     */
    data class Screen(
        val width: Float,
        val height: Float,
        val usable: Box,
        val gestures: Box,
        val keyboardTop: Float? = null,
    )

    /** A window position and size in pixels. */
    data class Window(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * The line a top dock hangs from: under the status bar, the cutout and the system gesture area, so the bubble
     * covers none of them and the system does not take a pull that starts on it. The same in fullscreen apps: a
     * swipe from the very edge shows the bars or opens the notifications and cancels the pull.
     */
    fun topLine(screen: Screen): Float = maxOf(screen.usable.top, screen.gestures.top)

    /**
     * The line a bottom dock stands on: above the navigation buttons and the gesture area, where pulling the bubble
     * up would be the Home gesture, in fullscreen apps too. An open keyboard lifts it.
     */
    fun bottomLine(screen: Screen): Float {
        val line = minOf(screen.usable.bottom, screen.gestures.bottom)
        return screen.keyboardTop?.let { minOf(line, it) } ?: line
    }

    /**
     * The docked bubble's window. At the sides the whole bubble's window stands past the screen's edge, which hides
     * the rest. At the top and bottom the window holds only the part that shows, cut off at the dock line, so it
     * takes no touches over the system bars. [position] is along the edge, as a fraction of the screen.
     */
    fun window(side: DockSide, position: Float, size: Int, screen: Screen): Window {
        val visible = (size * BubbleView.DOCK_VISIBLE_FRACTION).roundToInt()
        return when (side) {
            DockSide.LEFT, DockSide.RIGHT -> {
                val centerY = (position * screen.height).coerceIn(screen.usable.top + size, screen.usable.bottom - size)
                val x = if (side == DockSide.RIGHT) screen.width - visible else visible - size.toFloat()
                Window(x.roundToInt(), (centerY - size / 2f).roundToInt(), size, size)
            }
            DockSide.TOP, DockSide.BOTTOM -> {
                val centerX = (position * screen.width).coerceIn(screen.usable.left + size, screen.usable.right - size)
                val y = if (side == DockSide.TOP) topLine(screen) else bottomLine(screen) - visible
                Window((centerX - size / 2f).roundToInt(), y.roundToInt(), size, visible)
            }
        }
    }

    /**
     * The disc's center for the bubble's [window], the inverse of [window]: a top dock's window, lower than the disc,
     * holds only the disc's lower part; any other window starts at the disc's top.
     */
    fun center(window: Window, size: Int, side: DockSide): Pair<Float, Float> {
        val top = if (window.height < size && side == DockSide.TOP) window.y + window.height - size else window.y
        return window.x + size / 2f to top + size / 2f
    }

    /** Where a bubble centered at ([centerX], [centerY]) sits along [side], as a fraction of the screen. */
    fun position(side: DockSide, centerX: Float, centerY: Float, screen: Screen): Float =
        if (side.horizontal) centerX / screen.width else centerY / screen.height

    /**
     * The edge a bubble dropped with the finger at ([fingerX], [fingerY]) docks at: its center went past that edge,
     * or the finger was lifted within [zone] of it, so words next to the edge stay reachable. The center counts too
     * because the finger may hold the bubble off-center, and curved screens often report no touches at the edge
     * itself. Top and bottom only when [topBottom]; at a corner the edge nearer to the finger wins. Null: the bubble
     * floats.
     */
    fun edgeAt(
        fingerX: Float,
        fingerY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        zone: Float,
        topBottom: Boolean,
    ): DockSide? {
        val candidates = buildList {
            if (fingerX <= zone || centerX <= 0f) add(DockSide.LEFT to fingerX)
            if (fingerX >= width - zone || centerX >= width) add(DockSide.RIGHT to width - fingerX)
            if (topBottom && (fingerY <= zone || centerY <= 0f)) add(DockSide.TOP to fingerY)
            if (topBottom && (fingerY >= height - zone || centerY >= height)) add(DockSide.BOTTOM to height - fingerY)
        }
        return candidates.minByOrNull { it.second }?.first
    }
}
