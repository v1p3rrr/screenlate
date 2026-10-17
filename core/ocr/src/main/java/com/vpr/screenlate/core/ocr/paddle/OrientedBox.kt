package com.vpr.screenlate.core.ocr.paddle

import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.sqrt

/**
 * A rectangle at any angle, in image pixels. From its corner ([x], [y]) it runs [length] along the unit direction
 * ([alongX], [alongY]), the way its text is read, and [thickness] across it, along the direction turned a quarter
 * clockwise on the screen ([acrossX], [acrossY]): from the text's top to its bottom.
 */
internal data class OrientedBox(
    val x: Float,
    val y: Float,
    val alongX: Float,
    val alongY: Float,
    val length: Float,
    val thickness: Float,
) {
    val acrossX: Float get() = -alongY
    val acrossY: Float get() = alongX

    /** The bounding box of the stretch from [from] to [to] along the box, all across it. */
    fun part(from: Float, to: Float): Box {
        val xs = floatArrayOf(pointX(from, 0f), pointX(to, 0f), pointX(from, thickness), pointX(to, thickness))
        val ys = floatArrayOf(pointY(from, 0f), pointY(to, 0f), pointY(from, thickness), pointY(to, thickness))
        return Box(xs.min(), ys.min(), xs.max(), ys.max())
    }

    fun bounds(): Box = part(0f, length)

    /** The box with [distance] added on every side. */
    fun widened(distance: Float): OrientedBox = copy(
        x = x - distance * (alongX + acrossX),
        y = y - distance * (alongY + acrossY),
        length = length + 2 * distance,
        thickness = thickness + 2 * distance,
    )

    /** The same rectangle read as a column, top first: along its thickness, from its far end's corner. */
    fun turned(): OrientedBox =
        OrientedBox(pointX(length, 0f), pointY(length, 0f), acrossX, acrossY, thickness, length)

    /**
     * The affine map from image coordinates to a crop of [width] × [height] that holds the box upright, as the six
     * values of a 2 × 3 matrix by rows.
     */
    fun toCrop(width: Int, height: Int): FloatArray {
        val scaleX = width / length
        val scaleY = height / thickness
        return floatArrayOf(
            scaleX * alongX, scaleX * alongY, -scaleX * (x * alongX + y * alongY),
            scaleY * acrossX, scaleY * acrossY, -scaleY * (x * acrossX + y * acrossY),
        )
    }

    private fun pointX(along: Float, across: Float) = x + along * alongX + across * acrossX
    private fun pointY(along: Float, across: Float) = y + along * alongY + across * acrossY

    companion object {
        /**
         * The smallest rectangle around [points] (x and y in turn), turned so that it is read along the side closer to
         * horizontal, from left to right.
         */
        fun enclosing(points: FloatArray): OrientedBox {
            val hull = hull(points)
            val count = hull.size / 2
            var bestArea = Float.POSITIVE_INFINITY
            var edgeX = 1f
            var edgeY = 0f
            for (i in 0 until count) {
                val j = (i + 1) % count
                val dx = hull[2 * j] - hull[2 * i]
                val dy = hull[2 * j + 1] - hull[2 * i + 1]
                val norm = sqrt(dx * dx + dy * dy)
                if (norm == 0f) continue
                val (along, across) = extents(hull, dx / norm, dy / norm)
                val area = along * across
                if (area < bestArea) {
                    bestArea = area
                    edgeX = dx / norm
                    edgeY = dy / norm
                }
            }
            // Of the edge's direction and its quarter turns, the one pointing most to the right.
            val (alongX, alongY) = listOf(edgeX to edgeY, -edgeY to edgeX, -edgeX to -edgeY, edgeY to -edgeX)
                .maxBy { it.first }
            var minAlong = Float.POSITIVE_INFINITY
            var maxAlong = Float.NEGATIVE_INFINITY
            var minAcross = Float.POSITIVE_INFINITY
            var maxAcross = Float.NEGATIVE_INFINITY
            for (i in 0 until count) {
                val along = hull[2 * i] * alongX + hull[2 * i + 1] * alongY
                val across = -hull[2 * i] * alongY + hull[2 * i + 1] * alongX
                minAlong = minOf(minAlong, along)
                maxAlong = maxOf(maxAlong, along)
                minAcross = minOf(minAcross, across)
                maxAcross = maxOf(maxAcross, across)
            }
            return OrientedBox(
                x = minAlong * alongX - minAcross * alongY,
                y = minAlong * alongY + minAcross * alongX,
                alongX = alongX,
                alongY = alongY,
                length = maxAlong - minAlong,
                thickness = maxAcross - minAcross,
            )
        }

        /** The extents of [points] along the unit direction ([dx], [dy]) and across it. */
        private fun extents(points: FloatArray, dx: Float, dy: Float): Pair<Float, Float> {
            var minAlong = Float.POSITIVE_INFINITY
            var maxAlong = Float.NEGATIVE_INFINITY
            var minAcross = Float.POSITIVE_INFINITY
            var maxAcross = Float.NEGATIVE_INFINITY
            for (i in 0 until points.size / 2) {
                val along = points[2 * i] * dx + points[2 * i + 1] * dy
                val across = -points[2 * i] * dy + points[2 * i + 1] * dx
                minAlong = minOf(minAlong, along)
                maxAlong = maxOf(maxAlong, along)
                minAcross = minOf(minAcross, across)
                maxAcross = maxOf(maxAcross, across)
            }
            return (maxAlong - minAlong) to (maxAcross - minAcross)
        }

        /** The convex hull of [points] (x and y in turn), without repeated or collinear points (monotone chain). */
        private fun hull(points: FloatArray): FloatArray {
            val order = (0 until points.size / 2).sortedWith(compareBy({ points[2 * it] }, { points[2 * it + 1] }))
            val chain = IntArray(2 * order.size)
            var size = 0
            fun turn(o: Int, a: Int, b: Int) =
                (points[2 * a] - points[2 * o]) * (points[2 * b + 1] - points[2 * o + 1]) -
                    (points[2 * a + 1] - points[2 * o + 1]) * (points[2 * b] - points[2 * o])
            for (i in order) {
                while (size >= 2 && turn(chain[size - 2], chain[size - 1], i) <= 0) size--
                chain[size++] = i
            }
            val lower = size + 1
            for (i in order.asReversed().drop(1)) {
                while (size >= lower && turn(chain[size - 2], chain[size - 1], i) <= 0) size--
                chain[size++] = i
            }
            val count = maxOf(1, size - 1)
            return FloatArray(2 * count) { points[2 * chain[it / 2] + it % 2] }
        }
    }
}

/** This box cut to [limit]; a box outside it shrinks to its edge. */
internal fun Box.within(limit: Box): Box = Box(
    left.coerceIn(limit.left, limit.right),
    top.coerceIn(limit.top, limit.bottom),
    right.coerceIn(limit.left, limit.right),
    bottom.coerceIn(limit.top, limit.bottom),
)
