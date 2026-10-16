package com.vpr.screenlate.core.ocr.paddle

import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.roundToInt

/** A text line the detector found, in image pixels, with its mean probability. */
data class DetectedLine(val box: Box, val score: Float)

/**
 * Input and output of PP-OCR's text detector (DB). The input is the image scaled to a width limit, as normalized BGR
 * planes; the output is a map of text probabilities at the input's size. Its
 * regions above [THRESHOLD] are the lines: each region's bounding box, widened by [UNCLIP_RATIO] the way PP-OCR's
 * post-processing unclips its polygons, kept when the region's mean probability reaches [BOX_THRESHOLD]. Boxes are
 * axis-aligned: screen text is horizontal.
 */
internal object TextDetection {
    const val THRESHOLD = 0.3f
    const val BOX_THRESHOLD = 0.6f
    const val UNCLIP_RATIO = 1.5f

    /** Regions narrower or lower than this many map pixels are noise. */
    private const val MIN_SIDE = 3

    // In plane order: PP-OCR applies ImageNet's values, listed for RGB, to its BGR planes as they are.
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** The input size for an image of [width] × [height]: at most [limit] wide, both sides multiples of 32. */
    fun inputSize(width: Int, height: Int, limit: Int): Pair<Int, Int> {
        val ratio = minOf(1f, limit.toFloat() / width)
        fun round(side: Float) = maxOf(32, (side / 32).roundToInt() * 32)
        return round(width * ratio) to round(height * ratio)
    }

    /** ARGB [pixels] of the scaled image as the detector's input. */
    fun input(pixels: IntArray, width: Int, height: Int): Tensor {
        val plane = width * height
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val pixel = pixels[i]
            data[i] = ((pixel and 0xFF) / 255f - MEAN[0]) / STD[0]
            data[plane + i] = ((pixel shr 8 and 0xFF) / 255f - MEAN[1]) / STD[1]
            data[2 * plane + i] = ((pixel shr 16 and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return Tensor(data, intArrayOf(1, 3, height, width))
    }

    /**
     * The lines of a probability [map] of [width] × [height], in the coordinates of an image of [imageWidth] ×
     * [imageHeight], top to bottom.
     */
    fun lines(map: FloatArray, width: Int, height: Int, imageWidth: Int, imageHeight: Int): List<DetectedLine> {
        val scaleX = imageWidth.toFloat() / width
        val scaleY = imageHeight.toFloat() / height
        val visited = BooleanArray(map.size)
        var stack = IntArray(256)
        val lines = ArrayList<DetectedLine>()
        for (start in map.indices) {
            if (visited[start] || map[start] <= THRESHOLD) continue
            visited[start] = true
            stack[0] = start
            var size = 1
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            var sum = 0.0
            var count = 0
            while (size > 0) {
                val index = stack[--size]
                val x = index % width
                val y = index / width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
                sum += map[index]
                count++
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val next = ny * width + nx
                        if (visited[next] || map[next] <= THRESHOLD) continue
                        visited[next] = true
                        if (size == stack.size) stack = stack.copyOf(size * 2)
                        stack[size++] = next
                    }
                }
            }
            val regionWidth = right - left + 1
            val regionHeight = bottom - top + 1
            if (minOf(regionWidth, regionHeight) < MIN_SIDE) continue
            val score = (sum / count).toFloat()
            if (score < BOX_THRESHOLD) continue
            val distance = regionWidth * regionHeight * UNCLIP_RATIO / (2f * (regionWidth + regionHeight))
            val box = Box(
                ((left - distance) * scaleX).coerceAtLeast(0f),
                ((top - distance) * scaleY).coerceAtLeast(0f),
                ((right + 1 + distance) * scaleX).coerceAtMost(imageWidth.toFloat()),
                ((bottom + 1 + distance) * scaleY).coerceAtMost(imageHeight.toFloat()),
            )
            lines += DetectedLine(box, score)
        }
        return lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }
}
