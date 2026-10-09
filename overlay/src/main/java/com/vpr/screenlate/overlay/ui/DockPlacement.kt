package com.vpr.screenlate.overlay.ui

import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.settings.DockSide
import kotlin.math.ceil
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
     * @property shownBars the edges where a system bar shows now; a fullscreen app hides them.
     */
    data class Screen(
        val width: Float,
        val height: Float,
        val usable: Box,
        val gestures: Box,
        val keyboardTop: Float? = null,
        val shownBars: Set<DockSide> = DockSide.entries.toSet(),
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
     * The docked bubble's window: only the part of the disc that shows, so it takes no touches beyond it. [position]
     * is along the edge, as a fraction of the screen.
     */
    fun window(side: DockSide, position: Float, size: Int, screen: Screen): Window {
        val (inset, visible) = cap(side, size, screen)
        return when (side) {
            DockSide.LEFT, DockSide.RIGHT -> {
                val centerY = (position * screen.height).coerceIn(screen.usable.top + size, screen.usable.bottom - size)
                val x = if (side == DockSide.RIGHT) screen.width - inset - visible else inset
                Window(x.roundToInt(), (centerY - size / 2f).roundToInt(), visible, size)
            }
            DockSide.TOP, DockSide.BOTTOM -> {
                val centerX = (position * screen.width).coerceIn(screen.usable.left + size, screen.usable.right - size)
                val y = if (side == DockSide.TOP) inset else screen.height - inset - visible
                Window((centerX - size / 2f).roundToInt(), y.roundToInt(), size, visible)
            }
        }
    }

    /**
     * Where the part that shows starts, as a distance from [side]'s screen edge, and how much of the disc shows.
     *
     * Where nothing takes a touch at the screen's edge, the bubble goes to it and [VISIBLE_FRACTION] shows. A system
     * bar that shows, or a keyboard at the bottom, moves it to the dock line past them. In a fullscreen app, at an
     * edge with a mandatory gesture strip (the notifications at the top, Home at the bottom), the bubble goes to the
     * very edge and shows enough for a third of the disc to stand past the strip, where pulling it out works; when
     * the strip is too wide for that, it stays at the line.
     */
    internal fun cap(side: DockSide, size: Int, screen: Screen): Pair<Float, Int> {
        val usual = (size * VISIBLE_FRACTION).roundToInt()
        val line = lineInset(side, screen)
        val keyboard = side == DockSide.BOTTOM && screen.keyboardTop != null
        if (side in screen.shownBars || keyboard) return line to usual
        val strip = stripInset(side, screen)
        if (strip <= 0f) return 0f to usual
        val reach = ceil(strip + size / GRAB_PARTS).toInt()
        return if (reach <= size) 0f to maxOf((size * STRIP_VISIBLE_FRACTION).roundToInt(), reach) else line to usual
    }

    /** The dock line's distance from [side]'s screen edge: past the bars, the cutout, the gesture strip, a keyboard. */
    private fun lineInset(side: DockSide, screen: Screen): Float = when (side) {
        DockSide.LEFT -> maxOf(screen.usable.left, screen.gestures.left)
        DockSide.TOP -> topLine(screen)
        DockSide.RIGHT -> screen.width - minOf(screen.usable.right, screen.gestures.right)
        DockSide.BOTTOM -> screen.height - bottomLine(screen)
    }

    /** The mandatory gesture strip's width at [side]. */
    private fun stripInset(side: DockSide, screen: Screen): Float = when (side) {
        DockSide.LEFT -> screen.gestures.left
        DockSide.TOP -> screen.gestures.top
        DockSide.RIGHT -> screen.width - screen.gestures.right
        DockSide.BOTTOM -> screen.height - screen.gestures.bottom
    }

    /**
     * The disc's center for the bubble's [window], the inverse of [window]: a left or top dock's window holds the
     * disc's far part, any other window starts at the disc's edge.
     */
    fun center(window: Window, size: Int, side: DockSide): Pair<Float, Float> {
        val left = if (side == DockSide.LEFT) window.x + window.width - size else window.x
        val top = if (side == DockSide.TOP) window.y + window.height - size else window.y
        return left + size / 2f to top + size / 2f
    }

    /** Part of the docked bubble that shows where nothing takes a touch at the screen's edge. */
    const val VISIBLE_FRACTION = 0.4f

    /** The least part that shows at an edge where the bubble stands in a system gesture strip. */
    private const val STRIP_VISIBLE_FRACTION = 0.6f

    /** At a gesture strip, 1 / this of the disc stands past it for the finger. */
    private const val GRAB_PARTS = 3f

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
