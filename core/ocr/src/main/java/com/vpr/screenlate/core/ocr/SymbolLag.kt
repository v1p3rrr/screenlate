package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Corrects per-character boxes that lag behind the glyphs. ML Kit's symbol boxes are often exact, but in some words
 * every boundary between characters sits the same distance too far along the reading direction (up to about half a
 * character): the first box is too wide, the middle ones cut into the next glyph, the last is too narrow. The inner
 * boundaries are moved back by the distance at which they cross the least ink, only when that is clearly better
 * than where they are; outer edges stay.
 */
internal object SymbolLag {

    /** Weakest light/dark spread across a column that counts as ink. */
    private const val MIN_CONTRAST = 32

    /** How far back (and forward) boundaries are tried, in characters. */
    private const val MAX_BACK = 0.6f
    private const val MAX_FORWARD = 0.15f

    /** A shift is taken when it lowers the ink under the boundaries by this much, and at least by half. */
    private const val MIN_GAIN = 0.25f

    /**
     * Light/dark spread of each column (row when [vertical]) of [pixels], ARGB row by row, [width] × [height]. Gaps
     * between glyphs have a low spread whatever the colors.
     */
    fun contrast(pixels: IntArray, width: Int, height: Int, vertical: Boolean): IntArray {
        val length = if (vertical) height else width
        val across = if (vertical) width else height
        return IntArray(length) { position ->
            var min = 255
            var max = 0
            for (other in 0 until across) {
                val luma = luma(if (vertical) pixels[position * width + other] else pixels[other * width + position])
                if (luma < min) min = luma
                if (luma > max) max = luma
            }
            max - min
        }
    }

    /**
     * Returns [boxes] with the inner boundaries moved onto the gaps, or [boxes] itself when they already sit there or
     * the image gives no clear answer. [contrast] covers the word along the reading direction from [origin].
     */
    fun align(boxes: List<Box>, contrast: IntArray, origin: Int, vertical: Boolean): List<Box> {
        if (boxes.size < 3) return boxes
        val strongest = contrast.maxOrNull() ?: return boxes
        if (strongest < MIN_CONTRAST) return boxes
        val boundaries = (1 until boxes.size).map { index ->
            (end(boxes[index - 1], vertical) + start(boxes[index], vertical)) / 2f - origin
        }
        val cell = (end(boxes.last(), vertical) - start(boxes.first(), vertical)) / boxes.size
        val current = inkAt(contrast, boundaries, 0, strongest) ?: return boxes
        val back = -ceil(cell * MAX_BACK).toInt()
        val forward = (cell * MAX_FORWARD).toInt()
        var best = current
        var found = 0
        for (step in 1..-back) {
            for (candidate in intArrayOf(-step, step)) {
                if (candidate > forward) continue
                val ink = inkAt(contrast, boundaries, candidate, strongest) ?: continue
                if (ink < best) {
                    best = ink
                    found = candidate
                }
            }
        }
        if (found == 0 || current - best < MIN_GAIN || best > current / 2) return boxes
        // The best shift is usually a range as wide as the gaps; its middle puts the boundaries mid-gap.
        fun isBest(candidate: Int) = candidate in back..forward && inkAt(contrast, boundaries, candidate, strongest) == best
        var low = found
        while (isBest(low - 1)) low--
        var high = found
        while (isBest(high + 1)) high++
        val shift = (low + high) / 2
        return boxes.mapIndexed { index, box ->
            val from = if (index == 0) start(box, vertical) else boundaries[index - 1] + origin + shift
            val to = if (index == boxes.lastIndex) end(box, vertical) else boundaries[index] + origin + shift
            if (vertical) box.copy(top = from, bottom = to) else box.copy(left = from, right = to)
        }
    }

    /** Mean ink under the boundaries moved by [shift], from 0 (all in gaps) to 1; null when one falls outside. */
    private fun inkAt(contrast: IntArray, boundaries: List<Float>, shift: Int, strongest: Int): Float? {
        var sum = 0
        for (boundary in boundaries) {
            val position = (boundary + shift).roundToInt()
            if (position < 1 || position > contrast.size - 2) return null
            sum += minOf(contrast[position - 1], contrast[position], contrast[position + 1])
        }
        return sum.toFloat() / boundaries.size / strongest
    }

    private fun start(box: Box, vertical: Boolean) = if (vertical) box.top else box.left

    private fun end(box: Box, vertical: Boolean) = if (vertical) box.bottom else box.right

    private fun luma(pixel: Int): Int {
        val red = pixel shr 16 and 0xFF
        val green = pixel shr 8 and 0xFF
        val blue = pixel and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }
}
