package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Overlapping full-width horizontal bands of a screen image. Lens skips small text on a whole-screen image but reads
 * it in a smaller crop; each band overlaps its neighbours by half, so short text near a border lies wholly in one band.
 */
object ScreenBands {

    fun of(width: Int, height: Int): List<Box> {
        val bandHeight = minOf(height / 3f, width * MAX_ASPECT).coerceAtLeast(1f)
        if (bandHeight >= height) return listOf(Box(0f, 0f, width.toFloat(), height.toFloat()))
        val step = bandHeight / 2f
        val count = ceil((height - bandHeight) / step).toInt() + 1
        return (0 until count).map { index ->
            val top = minOf(index * step, height - bandHeight).roundToInt().toFloat()
            Box(0f, top, width.toFloat(), (top + bandHeight).roundToInt().toFloat().coerceAtMost(height.toFloat()))
        }
    }

    /** The band whose center is nearest to [y], so a point is never at a band's edge. */
    fun nearest(bands: List<Box>, y: Float): Int = bands.indices.minBy { kotlin.math.abs(bands[it].centerY - y) }

    /** Bands no taller than this fraction of the width keep small text large relative to the image. */
    private const val MAX_ASPECT = 0.75f
}
