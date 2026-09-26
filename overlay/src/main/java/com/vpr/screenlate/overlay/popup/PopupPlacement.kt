package com.vpr.screenlate.overlay.popup

import com.vpr.screenlate.core.common.geometry.Box

/** Chooses where the popup goes relative to the word under the aim. */
object PopupPlacement {

    private enum class Side { ABOVE, BELOW, LEFT, RIGHT }

    private class Candidate(val side: Side, val space: Float, val box: Box?, val full: Boolean)

    /**
     * Returns the popup bounds next to [word], never covering [bubble]: the word and the bubble are kept out together,
     * so a popup below the word goes below the bubble when the bubble is there (as in Poe). Vertical text prefers the
     * left/right sides so the column stays visible; horizontal text uses above/below. A popup placed above or below
     * shrinks to the free space, but not below [minHeight].
     *
     * @param screen usable screen area, excluding system bars.
     */
    fun place(
        word: Box,
        vertical: Boolean,
        bubble: Box?,
        popupWidth: Float,
        popupHeight: Float,
        minHeight: Float,
        screen: Box,
        margin: Float,
    ): Box {
        val keepOut = bubble?.let(word::union) ?: word
        val primary = if (vertical) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.ABOVE, Side.BELOW)
        val secondary = if (vertical) listOf(Side.ABOVE, Side.BELOW) else emptyList()
        val candidates = (primary + secondary).map { candidate(it, word, keepOut, popupWidth, popupHeight, minHeight, screen, margin) }

        for (group in listOf(primary, secondary)) {
            val fitting = candidates.filter { it.side in group && it.box != null }
            val best = fitting.filter { it.full }.maxByOrNull { it.space } ?: fitting.maxByOrNull { it.space }
            if (best?.box != null) return best.box
        }
        // Nothing fits beside the word and the bubble: use the roomiest side and let the popup cover what it must.
        val side = candidates.filter { it.side in primary }.maxBy { it.space }.side
        return clampInto(overlapping(side, word, popupWidth, popupHeight, minHeight, screen, margin), screen)
    }

    private fun candidate(
        side: Side,
        word: Box,
        keepOut: Box,
        w: Float,
        h: Float,
        minHeight: Float,
        screen: Box,
        margin: Float,
    ): Candidate {
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
                val left = keepOut.left - margin - w
                Candidate(side, space, if (space >= w) Box(left, centeredY, left + w, centeredY + h) else null, true)
            }
            Side.RIGHT -> {
                val space = screen.right - keepOut.right - margin
                val left = keepOut.right + margin
                Candidate(side, space, if (space >= w) Box(left, centeredY, left + w, centeredY + h) else null, true)
            }
        }
    }

    /** A popup on [side] of the word alone, for when the bubble cannot be avoided. */
    private fun overlapping(side: Side, word: Box, w: Float, h: Float, minHeight: Float, screen: Box, margin: Float): Box {
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
