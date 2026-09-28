package com.vpr.screenlate.core.ocr

import kotlin.math.roundToInt

/**
 * Full-width bands around the aim that the on-device draft reads before the whole image. ML Kit's time grows with the
 * amount of text rather than with the image size (a screen read at two thirds of its size takes as long), so a few
 * lines around the aim are ready several times sooner than the whole screen. The aim sits in the upper part of the
 * band: lookups read on to the right and downwards.
 */
internal object FocusBand {

    /**
     * The band of rows to read for an aim at row [y] of an image [height] rows tall, or null when there is no aim, when
     * one of the [done] bands already covers it away from its cut edges, or when the image is too small to split.
     */
    fun next(height: Int, y: Float?, done: List<IntRange>): IntRange? {
        if (y == null || height <= 0) return null
        val size = (height * FRACTION).roundToInt()
        if (size < MIN_ROWS) return null
        val aim = y.roundToInt().coerceIn(0, height - 1)
        if (done.any { aim in inner(it, height) }) return null
        val top = (aim - (size * AIM_POSITION).roundToInt()).coerceIn(0, height - size)
        return top until top + size
    }

    /** A band's rows away from its cut edges; rows next to the image's own edges count as inner. */
    private fun inner(band: IntRange, height: Int): IntRange {
        val margin = ((band.last - band.first + 1) * EDGE_MARGIN).roundToInt()
        val top = if (band.first == 0) 0 else band.first + margin
        val bottom = if (band.last == height - 1) band.last else band.last - margin
        return top..bottom
    }

    private const val FRACTION = 0.15f
    private const val AIM_POSITION = 0.4f
    private const val EDGE_MARGIN = 0.15f

    /** Below this band height the whole image is read at once. */
    private const val MIN_ROWS = 200
}
