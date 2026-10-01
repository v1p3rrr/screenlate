package com.vpr.screenlate.overlay.popup

import com.vpr.screenlate.core.common.geometry.Box

/** Chooses where the popup goes relative to the word under the aim. */
object PopupPlacement {

    private enum class Side { ABOVE, BELOW, LEFT, RIGHT }

    private class Candidate(val side: Side, val space: Float, val box: Box?)

    /**
     * Popup size. Above or below the word the popup keeps it whole; [beside] puts it beside the word instead, where
     * its width may shrink to the free space, down to [minWidth].
     */
    data class Size(
        val width: Float,
        val height: Float,
        val minWidth: Float = width,
        val beside: Boolean = false,
    )

    /**
     * About a third of a portrait screen, above or below the word, vertical text included. A landscape screen is so
     * low that a strip above or below holds hardly one entry, so there the popup goes beside the word, up to half the
     * width and nearly the whole height.
     *
     * @param screen usable screen area in pixels.
     */
    fun size(screen: Box, maxWidth: Float): Size {
        if (screen.width > screen.height) {
            val width = minOf(screen.width * LANDSCAPE_WIDTH_FRACTION, maxWidth)
            return Size(
                width = width,
                height = screen.height * LANDSCAPE_HEIGHT_FRACTION,
                minWidth = width * LANDSCAPE_MIN_WIDTH_FRACTION,
                beside = true,
            )
        }
        return Size(width = minOf(screen.width * WIDTH_FRACTION, maxWidth), height = screen.height * HEIGHT_FRACTION)
    }

    private const val WIDTH_FRACTION = 0.85f
    private const val HEIGHT_FRACTION = 0.35f
    private const val LANDSCAPE_WIDTH_FRACTION = 0.5f
    private const val LANDSCAPE_HEIGHT_FRACTION = 0.9f
    private const val LANDSCAPE_MIN_WIDTH_FRACTION = 0.8f

    /**
     * Returns the popup bounds next to [word] and [bubble], which are kept out together, so a popup below the word
     * goes below the bubble when the bubble is there. The popup goes above when it fits there, else below; with
     * [Size.beside], to the roomier side, shrinking to the free width down to [Size.minWidth]. When that does not fit,
     * the popup keeps its size at the screen's edge on the roomier side and covers part of the word or the bubble:
     * that reads better than a low or narrow popup.
     *
     * @param screen usable screen area, excluding system bars.
     */
    fun place(word: Box, bubble: Box?, size: Size, screen: Box, margin: Float): Box {
        val keepOut = bubble?.let(word::union) ?: word
        // Above or below, the popup is centered on the word horizontally; beside it, vertically.
        val x = centered(word.centerX, size.width, screen.left, screen.right)
        val y = centered(word.centerY, size.height, screen.top, screen.bottom)
        val sides = if (size.beside) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.ABOVE, Side.BELOW)
        val candidates = sides.map { candidate(it, keepOut, x, y, size, screen, margin) }
        val fitting = candidates.filter { it.box != null }
        // Above wins over below; of the sides the roomier one.
        val best = if (size.beside) fitting.maxByOrNull { it.space } else fitting.firstOrNull()
        return best?.box ?: atEdge(candidates.maxBy { it.space }.side, x, y, size, screen)
    }

    private fun candidate(side: Side, keepOut: Box, x: Float, y: Float, size: Size, screen: Box, margin: Float): Candidate {
        val w = size.width
        val h = size.height
        return when (side) {
            Side.ABOVE -> {
                val space = keepOut.top - margin - screen.top
                val bottom = keepOut.top - margin
                Candidate(side, space, if (space >= h) Box(x, bottom - h, x + w, bottom) else null)
            }
            Side.BELOW -> {
                val space = screen.bottom - keepOut.bottom - margin
                val top = keepOut.bottom + margin
                Candidate(side, space, if (space >= h) Box(x, top, x + w, top + h) else null)
            }
            Side.LEFT -> {
                val space = keepOut.left - margin - screen.left
                val width = minOf(w, space)
                val right = keepOut.left - margin
                Candidate(side, space, if (width >= size.minWidth) Box(right - width, y, right, y + h) else null)
            }
            Side.RIGHT -> {
                val space = screen.right - keepOut.right - margin
                val width = minOf(w, space)
                val left = keepOut.right + margin
                Candidate(side, space, if (width >= size.minWidth) Box(left, y, left + width, y + h) else null)
            }
        }
    }

    /**
     * The whole popup against the screen's edge on [side], at [x] or [y] along that edge. A popup larger than the
     * screen still starts inside it, so its header stays visible.
     */
    private fun atEdge(side: Side, x: Float, y: Float, size: Size, screen: Box): Box {
        val w = size.width
        val h = size.height
        val left = when (side) {
            Side.LEFT -> screen.left
            Side.RIGHT -> (screen.right - w).coerceAtLeast(screen.left)
            else -> x
        }
        val top = when (side) {
            Side.ABOVE -> screen.top
            Side.BELOW -> (screen.bottom - h).coerceAtLeast(screen.top)
            else -> y
        }
        return Box(left, top, left + w, top + h)
    }

    /** Start of a [length] long span centered on [center] and kept between [start] and [end] when it fits. */
    private fun centered(center: Float, length: Float, start: Float, end: Float): Float =
        (center - length / 2f).coerceIn(start, (end - length).coerceAtLeast(start))
}
