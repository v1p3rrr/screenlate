package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientedBoxTest {

    /** The corners and the middle of a [length] × [thickness] rectangle from ([x], [y]), turned by [degrees] clockwise. */
    private fun rectangle(x: Float, y: Float, length: Float, thickness: Float, degrees: Double): FloatArray {
        val c = cos(Math.toRadians(degrees)).toFloat()
        val s = sin(Math.toRadians(degrees)).toFloat()
        val along = listOf(0f, length, 0f, length, length / 2)
        val across = listOf(0f, 0f, thickness, thickness, thickness / 2)
        return along.indices.flatMap { listOf(x + along[it] * c - across[it] * s, y + along[it] * s + across[it] * c) }
            .toFloatArray()
    }

    @Test
    fun `an upright rectangle is read along its width from its top left corner`() {
        val box = OrientedBox.enclosing(rectangle(10f, 20f, 100f, 30f, 0.0))
        assertThat(box.x).isWithin(1e-3f).of(10f)
        assertThat(box.y).isWithin(1e-3f).of(20f)
        assertThat(box.alongX).isWithin(1e-6f).of(1f)
        assertThat(box.alongY).isWithin(1e-6f).of(0f)
        assertThat(box.length).isWithin(1e-3f).of(100f)
        assertThat(box.thickness).isWithin(1e-3f).of(30f)
        assertThat(box.bounds()).isEqualTo(Box(10f, 20f, 110f, 50f))
    }

    @Test
    fun `a line turned either way is read along its slope`() {
        for (degrees in listOf(10.0, -30.0)) {
            val box = OrientedBox.enclosing(rectangle(50f, 80f, 200f, 24f, degrees))
            assertThat(box.alongX).isWithin(1e-4f).of(cos(Math.toRadians(degrees)).toFloat())
            assertThat(box.alongY).isWithin(1e-4f).of(sin(Math.toRadians(degrees)).toFloat())
            assertThat(box.x).isWithin(1e-2f).of(50f)
            assertThat(box.y).isWithin(1e-2f).of(80f)
            assertThat(box.length).isWithin(1e-2f).of(200f)
            assertThat(box.thickness).isWithin(1e-2f).of(24f)
        }
    }

    @Test
    fun `a turned column keeps the side closer to horizontal as its length`() {
        val box = OrientedBox.enclosing(rectangle(0f, 0f, 30f, 150f, 5.0))
        assertThat(box.length).isWithin(1e-2f).of(30f)
        assertThat(box.thickness).isWithin(1e-2f).of(150f)
        val column = box.turned()
        assertThat(column.length).isWithin(1e-2f).of(150f)
        // Read top first: down the column, from the corner at the far end of the top side.
        assertThat(column.alongX).isWithin(1e-4f).of(-sin(Math.toRadians(5.0)).toFloat())
        assertThat(column.alongY).isWithin(1e-4f).of(cos(Math.toRadians(5.0)).toFloat())
        assertThat(column.x).isWithin(1e-2f).of(30f * cos(Math.toRadians(5.0)).toFloat())
        assertThat(column.y).isWithin(1e-2f).of(30f * sin(Math.toRadians(5.0)).toFloat())
    }

    @Test
    fun `the crop map puts the box's corners at the crop's corners`() {
        val box = OrientedBox.enclosing(rectangle(40f, 60f, 120f, 20f, -8.0))
        val map = box.toCrop(width = 288, height = 48)
        fun crop(x: Float, y: Float) = (map[0] * x + map[1] * y + map[2]) to (map[3] * x + map[4] * y + map[5])
        val start = crop(box.x, box.y)
        val end = crop(box.x + box.length * box.alongX, box.y + box.length * box.alongY)
        val below = crop(box.x + box.thickness * box.acrossX, box.y + box.thickness * box.acrossY)
        assertThat(start.first).isWithin(1e-2f).of(0f)
        assertThat(start.second).isWithin(1e-2f).of(0f)
        assertThat(end.first).isWithin(1e-2f).of(288f)
        assertThat(end.second).isWithin(1e-2f).of(0f)
        assertThat(below.first).isWithin(1e-2f).of(0f)
        assertThat(below.second).isWithin(1e-2f).of(48f)
    }

    @Test
    fun `widening adds the distance on every side`() {
        val box = OrientedBox(10f, 20f, 1f, 0f, 100f, 30f).widened(5f)
        assertThat(box.bounds()).isEqualTo(Box(5f, 15f, 115f, 55f))
    }

    @Test
    fun `a part of a turned box is the bounding box of that stretch`() {
        // Along (0.6, 0.8), across (-0.8, 0.6).
        val box = OrientedBox(100f, 0f, 0.6f, 0.8f, 50f, 10f)
        val part = box.part(10f, 20f)
        assertThat(part.left).isWithin(1e-4f).of(100f + 6f - 8f)
        assertThat(part.top).isWithin(1e-4f).of(8f)
        assertThat(part.right).isWithin(1e-4f).of(100f + 12f)
        assertThat(part.bottom).isWithin(1e-4f).of(16f + 6f)
    }

    @Test
    fun `a box is cut to its limit and one outside shrinks to the edge`() {
        val limit = Box(0f, 0f, 100f, 50f)
        assertThat(Box(-5f, 10f, 20f, 60f).within(limit)).isEqualTo(Box(0f, 10f, 20f, 50f))
        assertThat(Box(110f, 10f, 120f, 20f).within(limit)).isEqualTo(Box(100f, 10f, 100f, 20f))
    }
}
