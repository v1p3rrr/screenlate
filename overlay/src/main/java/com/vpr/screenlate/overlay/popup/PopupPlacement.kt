package com.vpr.screenlate.overlay.popup

import com.vpr.screenlate.core.common.geometry.Box

/** Chooses where the popup goes relative to the word under the aim. */
object PopupPlacement {

    private enum class Side { ABOVE, BELOW, LEFT, RIGHT }

    private class Candidate(val box: Box, val space: Float, val fits: Boolean)

    /**
     * Returns the popup bounds next to [word] on the side with more room that does not cover [bubble].
     * Vertical text prefers the left/right sides so the column stays visible; horizontal text uses above/below.
     *
     * @param screen usable screen area, excluding system bars.
     */
    fun place(
        word: Box,
        vertical: Boolean,
        bubble: Box?,
        popupWidth: Float,
        popupHeight: Float,
        screen: Box,
        margin: Float,
    ): Box {
        val primary = if (vertical) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.ABOVE, Side.BELOW)
        val secondary = if (vertical) listOf(Side.ABOVE, Side.BELOW) else emptyList()
        val candidates = (primary + secondary).associateWith { candidate(it, word, popupWidth, popupHeight, screen, margin) }

        fun Candidate.clear() = fits && (bubble == null || !box.intersects(bubble))

        listOf(primary, secondary).forEach { group ->
            group.mapNotNull { candidates[it] }.filter { it.clear() }.maxByOrNull { it.space }?.let { return it.box }
        }
        candidates.values.filter { it.fits }.maxByOrNull { it.space }?.let { return it.box }
        val fallback = primary.mapNotNull { candidates[it] }.maxBy { it.space }.box
        return clampInto(fallback, screen)
    }

    private fun candidate(side: Side, word: Box, w: Float, h: Float, screen: Box, margin: Float): Candidate {
        val centeredX = (word.centerX - w / 2f).coerceIn(screen.left, (screen.right - w).coerceAtLeast(screen.left))
        val centeredY = (word.centerY - h / 2f).coerceIn(screen.top, (screen.bottom - h).coerceAtLeast(screen.top))
        return when (side) {
            Side.ABOVE -> {
                val top = word.top - margin - h
                Candidate(Box(centeredX, top, centeredX + w, top + h), word.top - screen.top, top >= screen.top)
            }
            Side.BELOW -> {
                val top = word.bottom + margin
                Candidate(Box(centeredX, top, centeredX + w, top + h), screen.bottom - word.bottom, top + h <= screen.bottom)
            }
            Side.LEFT -> {
                val left = word.left - margin - w
                Candidate(Box(left, centeredY, left + w, centeredY + h), word.left - screen.left, left >= screen.left)
            }
            Side.RIGHT -> {
                val left = word.right + margin
                Candidate(Box(left, centeredY, left + w, centeredY + h), screen.right - word.right, left + w <= screen.right)
            }
        }
    }

    private fun clampInto(box: Box, screen: Box): Box {
        val left = box.left.coerceIn(screen.left, (screen.right - box.width).coerceAtLeast(screen.left))
        val top = box.top.coerceIn(screen.top, (screen.bottom - box.height).coerceAtLeast(screen.top))
        return Box(left, top, left + box.width, top + box.height)
    }
}
