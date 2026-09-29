package com.vpr.screenlate.overlay.popup

import com.vpr.screenlate.core.common.geometry.Box

/** Chooses where the popup goes relative to the word under the aim. */
object PopupPlacement {

    private enum class Side { ABOVE, BELOW, LEFT, RIGHT }

    private class Candidate(val side: Side, val space: Float, val box: Box?, val full: Boolean)

    /**
     * Preferred popup size. Above or below the word the popup is [height] tall and may shrink to the free space, down
     * to [minHeight]; beside it the popup is [besideHeight] tall and its width may shrink, down to [minWidth].
     * [besideFirst] tries the sides for horizontal text too. Vertical text whose sides both lack [minWidth] still goes
     * beside the column, on the roomier side, while that side has [narrowMinWidth].
     */
    data class Size(
        val width: Float,
        val height: Float,
        val minHeight: Float,
        val minWidth: Float = width,
        val besideFirst: Boolean = false,
        val besideHeight: Float = height,
        val narrowMinWidth: Float = minWidth,
    )

    /**
     * About a third of a portrait screen, above or below the word. Vertical text in portrait goes beside its column
     * when a side has room for at least a narrow popup, which is taller to make up for the width; a column in the
     * middle of a narrow screen gets an even narrower one on the roomier side, so the columns next to it stay
     * visible. A landscape screen
     * is so low that a strip above or below holds hardly one entry, so there the popup goes beside the word, up to
     * half the width and nearly the whole height.
     *
     * @param screen usable screen area in pixels; [density] converts dp to pixels.
     */
    fun size(screen: Box, density: Float, maxWidth: Float): Size {
        val minHeight = maxOf(screen.height * MIN_HEIGHT_FRACTION, MIN_HEIGHT_DP * density)
        if (screen.width > screen.height) {
            val width = minOf(screen.width * LANDSCAPE_WIDTH_FRACTION, maxWidth)
            val height = screen.height * LANDSCAPE_HEIGHT_FRACTION
            return Size(
                width = width,
                height = height,
                minHeight = minHeight.coerceAtMost(height),
                minWidth = minOf(LANDSCAPE_MIN_WIDTH_DP * density, width),
                besideFirst = true,
            )
        }
        val width = minOf(screen.width * WIDTH_FRACTION, maxWidth)
        val height = screen.height * HEIGHT_FRACTION
        return Size(
            width = width,
            height = height,
            minHeight = minHeight.coerceAtMost(height),
            minWidth = minOf(PORTRAIT_MIN_WIDTH_DP * density, width),
            besideHeight = screen.height * PORTRAIT_BESIDE_HEIGHT_FRACTION,
            narrowMinWidth = minOf(PORTRAIT_NARROW_MIN_WIDTH_DP * density, width),
        )
    }

    private const val WIDTH_FRACTION = 0.85f
    private const val HEIGHT_FRACTION = 0.35f
    private const val PORTRAIT_MIN_WIDTH_DP = 200f
    private const val PORTRAIT_NARROW_MIN_WIDTH_DP = 140f
    private const val PORTRAIT_BESIDE_HEIGHT_FRACTION = 0.6f
    private const val LANDSCAPE_WIDTH_FRACTION = 0.5f
    private const val LANDSCAPE_HEIGHT_FRACTION = 0.9f
    private const val LANDSCAPE_MIN_WIDTH_DP = 260f
    private const val MIN_HEIGHT_FRACTION = 0.2f
    private const val MIN_HEIGHT_DP = 160f

    /**
     * Returns the popup bounds next to [word], never covering [bubble]: the word and the bubble are kept out together,
     * so a popup below the word goes below the bubble when the bubble is there. Vertical text, and any
     * text with [Size.besideFirst], prefers the left/right sides so the text stays visible; horizontal text otherwise
     * uses above/below. A popup above or below shrinks to the free height, one beside the word to the free width
     * (for vertical text down to [Size.narrowMinWidth] before it moves above or below).
     *
     * @param screen usable screen area, excluding system bars.
     */
    fun place(word: Box, vertical: Boolean, bubble: Box?, size: Size, screen: Box, margin: Float): Box {
        val keepOut = bubble?.let(word::union) ?: word
        val beside = vertical || size.besideFirst
        val primary = if (beside) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.ABOVE, Side.BELOW)
        val secondary = if (beside) listOf(Side.ABOVE, Side.BELOW) else emptyList()
        val candidates = (primary + secondary).map { candidate(it, word, keepOut, size, screen, margin, size.minWidth) }
        val narrow = if (vertical && size.narrowMinWidth < size.minWidth) {
            primary.map { candidate(it, word, keepOut, size, screen, margin, size.narrowMinWidth) }
        } else {
            emptyList()
        }
        val groups = listOf(
            candidates.filter { it.side in primary },
            narrow,
            candidates.filter { it.side in secondary },
        )
        for (group in groups) {
            val fitting = group.filter { it.box != null }
            val best = fitting.filter { it.full }.maxByOrNull { it.space } ?: fitting.maxByOrNull { it.space }
            if (best?.box != null) return best.box
        }
        // Nothing fits beside the word and the bubble: use the roomiest side and let the popup cover what it must.
        val side = candidates.filter { it.side in primary }.maxBy { it.space }.side
        return clampInto(overlapping(side, word, size, screen, margin), screen)
    }

    private fun candidate(side: Side, word: Box, keepOut: Box, size: Size, screen: Box, margin: Float, minWidth: Float): Candidate {
        val w = size.width
        val h = if (side == Side.LEFT || side == Side.RIGHT) size.besideHeight else size.height
        val minHeight = size.minHeight
        val centeredX = (word.centerX - w / 2f).coerceIn(screen.left, (screen.right - w).coerceAtLeast(screen.left))
        val centeredY = (word.centerY - h / 2f).coerceIn(screen.top, (screen.bottom - h).coerceAtLeast(screen.top))
        return when (side) {
            Side.ABOVE -> {
                val space = keepOut.top - margin - screen.top
                val height = minOf(h, space)
                val box = if (height >= minHeight) Box(centeredX, keepOut.top - margin - height, centeredX + w, keepOut.top - margin) else null
                Candidate(side, space, box, space >= h)
            }
            Side.BELOW -> {
                val space = screen.bottom - keepOut.bottom - margin
                val height = minOf(h, space)
                val top = keepOut.bottom + margin
                val box = if (height >= minHeight) Box(centeredX, top, centeredX + w, top + height) else null
                Candidate(side, space, box, space >= h)
            }
            Side.LEFT -> {
                val space = keepOut.left - margin - screen.left
                val width = minOf(w, space)
                val right = keepOut.left - margin
                val box = if (width >= minWidth) Box(right - width, centeredY, right, centeredY + h) else null
                Candidate(side, space, box, space >= w)
            }
            Side.RIGHT -> {
                val space = screen.right - keepOut.right - margin
                val width = minOf(w, space)
                val left = keepOut.right + margin
                val box = if (width >= minWidth) Box(left, centeredY, left + width, centeredY + h) else null
                Candidate(side, space, box, space >= w)
            }
        }
    }

    /** A popup on [side] of the word alone, for when the bubble cannot be avoided. */
    private fun overlapping(side: Side, word: Box, size: Size, screen: Box, margin: Float): Box {
        val w = size.width
        val h = if (side == Side.LEFT || side == Side.RIGHT) size.besideHeight else size.height
        val minHeight = size.minHeight
        val x = word.centerX - w / 2f
        val y = word.centerY - h / 2f
        return when (side) {
            Side.ABOVE -> {
                val height = (word.top - margin - screen.top).coerceIn(minHeight, h)
                Box(x, word.top - margin - height, x + w, word.top - margin)
            }
            Side.BELOW -> {
                val height = (screen.bottom - word.bottom - margin).coerceIn(minHeight, h)
                Box(x, word.bottom + margin, x + w, word.bottom + margin + height)
            }
            Side.LEFT -> Box(word.left - margin - w, y, word.left - margin, y + h)
            Side.RIGHT -> Box(word.right + margin, y, word.right + margin + w, y + h)
        }
    }

    private fun clampInto(box: Box, screen: Box): Box {
        val left = box.left.coerceIn(screen.left, (screen.right - box.width).coerceAtLeast(screen.left))
        val top = box.top.coerceIn(screen.top, (screen.bottom - box.height).coerceAtLeast(screen.top))
        return Box(left, top, left + box.width, top + box.height)
    }
}
