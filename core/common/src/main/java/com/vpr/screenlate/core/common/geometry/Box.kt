package com.vpr.screenlate.core.common.geometry

import kotlin.math.max
import kotlin.math.sqrt

/** Axis-aligned rectangle. Independent of android.graphics so logic using it runs in plain JVM tests. */
data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** Distance from the point to the nearest edge; 0 when inside. */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = max(max(left - x, 0f), x - right)
        val dy = max(max(top - y, 0f), y - bottom)
        return sqrt(dx * dx + dy * dy)
    }

    fun offset(dx: Float, dy: Float): Box = Box(left + dx, top + dy, right + dx, bottom + dy)

    fun scale(factor: Float): Box = Box(left * factor, top * factor, right * factor, bottom * factor)

    fun union(other: Box): Box = Box(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

    fun intersects(other: Box): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    companion object {
        fun fromCenter(centerX: Float, centerY: Float, width: Float, height: Float): Box =
            Box(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)

        fun unionOf(boxes: Iterable<Box>): Box? = boxes.reduceOrNull(Box::union)
    }
}
