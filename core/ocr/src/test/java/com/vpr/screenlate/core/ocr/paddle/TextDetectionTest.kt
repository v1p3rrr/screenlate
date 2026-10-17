package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import java.util.Properties
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

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
    fun `a turned line becomes a turned rectangle and its bounding box`() {
        // A band 80 long and 6 thick from (10, 10), turned 10 degrees clockwise.
        val width = 100
        val height = 40
        val c = cos(Math.toRadians(10.0)).toFloat()
        val s = sin(Math.toRadians(10.0)).toFloat()
        val map = FloatArray(width * height) { i ->
            val x = i % width + 0.5f - 10f
            val y = i / width + 0.5f - 10f
            val along = x * c + y * s
            val across = -x * s + y * c
            if (along in 0f..80f && across in 0f..6f) 0.9f else 0f
        }
        val line = TextDetection.lines(map, width, height, width, height).single()
        assertThat(line.area.alongX).isWithin(0.02f).of(c)
        assertThat(line.area.alongY).isWithin(0.02f).of(s)
        // The pixels' edges make the region about 81 × 7; unclipped by 81 * 7 * 1.5 / 176 = 4.8 on each side.
        assertThat(line.area.length).isWithin(1.5f).of(90.6f)
        assertThat(line.area.thickness).isWithin(1.5f).of(16.6f)
        assertThat(line.box).isEqualTo(line.area.bounds().within(Box(0f, 0f, 100f, 40f)))
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

    @Test
    fun `a lower threshold keeps fainter regions`() {
        val map = map(30, 10, intArrayOf(1, 1, 20, 6) to 0.5f)
        val thresholds = DetectionThresholds(threshold = 0.2f, boxThreshold = 0.45f)
        assertThat(TextDetection.lines(map, 30, 10, 30, 10, thresholds)).hasSize(1)
    }

    @Test
    fun `thresholds come from properties and invalid ones keep the defaults`() {
        val properties = Properties().apply {
            setProperty("threshold", "0.2")
            setProperty("box_threshold", "1.5")
            setProperty("unclip_ratio", "x")
        }
        assertThat(DetectionThresholds.parse(properties)).isEqualTo(DetectionThresholds(threshold = 0.2f))
        assertThat(DetectionThresholds.parse(Properties())).isEqualTo(DetectionThresholds())
    }
}
