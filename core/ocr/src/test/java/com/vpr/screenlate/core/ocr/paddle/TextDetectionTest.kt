package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class TextDetectionTest {

    @Test
    fun `the input is at most the width limit with sides in multiples of 32`() {
        assertThat(TextDetection.inputSize(1280, 2856, 960)).isEqualTo(960 to 2144)
        assertThat(TextDetection.inputSize(640, 1000, 960)).isEqualTo(640 to 992)
        assertThat(TextDetection.inputSize(20, 10, 960)).isEqualTo(32 to 32)
    }

    @Test
    fun `the input holds blue green and red planes normalized in that order`() {
        val red = 0xFFFF0000.toInt()
        val input = TextDetection.input(intArrayOf(red, red), width = 2, height = 1)
        assertThat(input.shape).asList().containsExactly(1, 3, 1, 2).inOrder()
        assertThat(input.data[0]).isWithin(1e-4f).of((0f - 0.485f) / 0.229f)
        assertThat(input.data[2]).isWithin(1e-4f).of((0f - 0.456f) / 0.224f)
        assertThat(input.data[4]).isWithin(1e-4f).of((1f - 0.406f) / 0.225f)
    }

    /** A map of [width] × [height] with the given value inside each rectangle (left, top, right, bottom exclusive). */
    private fun map(width: Int, height: Int, vararg regions: Pair<IntArray, Float>): FloatArray {
        val map = FloatArray(width * height)
        for ((rect, value) in regions) {
            for (y in rect[1] until rect[3]) for (x in rect[0] until rect[2]) map[y * width + x] = value
        }
        return map
    }

    @Test
    fun `each confident region becomes a line widened and scaled to the image`() {
        val map = map(
            40, 20,
            intArrayOf(6, 12, 36, 16) to 0.9f,
            intArrayOf(4, 2, 24, 6) to 0.8f,
        )
        val lines = TextDetection.lines(map, 40, 20, imageWidth = 80, imageHeight = 40)
        assertThat(lines.map { it.score }).containsExactly(0.8f, 0.9f).inOrder()
        // 20 × 4 region: unclipped by 20 * 4 * 1.5 / (2 * 24) = 2.5 on each side, then doubled; the top stops at 0.
        assertThat(lines[0].box).isEqualTo(Box(3f, 0f, 53f, 17f))
        assertThat(lines[1].box.left).isWithin(1e-3f).of((6 - 30f * 4 * 1.5f / 68) * 2)
    }

    @Test
    fun `faint regions and thin specks are dropped`() {
        val map = map(
            30, 10,
            intArrayOf(1, 1, 20, 6) to 0.5f,
            intArrayOf(22, 1, 30, 3) to 0.95f,
        )
        assertThat(TextDetection.lines(map, 30, 10, 30, 10)).isEmpty()
    }

    @Test
    fun `touching pixels on a diagonal belong to one region`() {
        val map = map(
            20, 10,
            intArrayOf(0, 0, 8, 4) to 0.9f,
            intArrayOf(8, 4, 16, 8) to 0.9f,
        )
        assertThat(TextDetection.lines(map, 20, 10, 20, 10)).hasSize(1)
    }
}
