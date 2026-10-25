package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.geometry.Box

/**
 * How a stored image is turned upright, as its EXIF orientation says. Coordinates on a photo are in oriented pixels:
 * the image as it is shown. A decoder reads stored (raw) pixels, so a region of the photo is mapped with [toRaw].
 *
 * @property exif the EXIF orientation value.
 */
enum class PhotoOrientation(val exif: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8),
    ;

    /** The upright image is the stored one turned a quarter: its width is the stored height. */
    val swapsAxes: Boolean get() = exif >= TRANSPOSE.exif

    /** The point ([x], [y]) of a stored [rawWidth] × [rawHeight] image on the upright photo. */
    fun toOriented(x: Float, y: Float, rawWidth: Int, rawHeight: Int): Pair<Float, Float> {
        val w = rawWidth.toFloat()
        val h = rawHeight.toFloat()
        return when (this) {
            NORMAL -> x to y
            FLIP_HORIZONTAL -> w - x to y
            ROTATE_180 -> w - x to h - y
            FLIP_VERTICAL -> x to h - y
            TRANSPOSE -> y to x
            ROTATE_90 -> h - y to x
            TRANSVERSE -> h - y to w - x
            ROTATE_270 -> y to w - x
        }
    }

    /** The point ([x], [y]) of the upright photo in the stored [rawWidth] × [rawHeight] image. */
    fun toRaw(x: Float, y: Float, rawWidth: Int, rawHeight: Int): Pair<Float, Float> {
        val w = rawWidth.toFloat()
        val h = rawHeight.toFloat()
        return when (this) {
            NORMAL -> x to y
            FLIP_HORIZONTAL -> w - x to y
            ROTATE_180 -> w - x to h - y
            FLIP_VERTICAL -> x to h - y
            TRANSPOSE -> y to x
            ROTATE_90 -> y to h - x
            TRANSVERSE -> w - y to h - x
            ROTATE_270 -> w - y to x
        }
    }

    /** [box] of the upright photo as a box of the stored [rawWidth] × [rawHeight] image. */
    fun toRaw(box: Box, rawWidth: Int, rawHeight: Int): Box {
        val (x1, y1) = toRaw(box.left, box.top, rawWidth, rawHeight)
        val (x2, y2) = toRaw(box.right, box.bottom, rawWidth, rawHeight)
        return Box(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
    }

    companion object {
        /** The orientation with EXIF value [exif]; [NORMAL] for a missing or unknown value, as image viewers take it. */
        fun fromExif(exif: Int): PhotoOrientation = entries.firstOrNull { it.exif == exif } ?: NORMAL
    }
}
