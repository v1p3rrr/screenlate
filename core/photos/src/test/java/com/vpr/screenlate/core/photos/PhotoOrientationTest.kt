package com.vpr.screenlate.core.photos

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PhotoOrientationTest {
    private val width = 400
    private val height = 300

    @Test
    fun `every orientation maps a point back to where it was`() {
        for (orientation in PhotoOrientation.entries) {
            for ((x, y) in listOf(0f to 0f, 10f to 20f, 399f to 1f, 123.5f to 299f)) {
                val (ox, oy) = orientation.toOriented(x, y, width, height)
                assertThat(orientation.toRaw(ox, oy, width, height)).isEqualTo(x to y)
            }
        }
    }

    @Test
    fun `the corners of the stored image stay inside the upright photo`() {
        for (orientation in PhotoOrientation.entries) {
            val orientedWidth = if (orientation.swapsAxes) height else width
            val orientedHeight = if (orientation.swapsAxes) width else height
            for ((x, y) in listOf(0f to 0f, width.toFloat() to 0f, 0f to height.toFloat(), width.toFloat() to height.toFloat())) {
                val (ox, oy) = orientation.toOriented(x, y, width, height)
                assertThat(ox).isIn(Range.closed(0f, orientedWidth.toFloat()))
                assertThat(oy).isIn(Range.closed(0f, orientedHeight.toFloat()))
            }
        }
    }

    @Test
    fun `a quarter turn to the right puts the stored top left corner at the upright top right`() {
        // EXIF 6: the stored image is shown turned 90° clockwise.
        assertThat(PhotoOrientation.ROTATE_90.toOriented(0f, 0f, width, height)).isEqualTo(height.toFloat() to 0f)
        assertThat(PhotoOrientation.ROTATE_270.toOriented(0f, 0f, width, height)).isEqualTo(0f to width.toFloat())
    }

    @Test
    fun `a region of the upright photo becomes the stored region that shows it`() {
        // The upright photo of a ROTATE_90 image is 300 × 400; its top band is the stored image's left band.
        val top = Box(0f, 0f, 300f, 100f)
        assertThat(PhotoOrientation.ROTATE_90.toRaw(top, width, height)).isEqualTo(Box(0f, 0f, 100f, 300f))
        assertThat(PhotoOrientation.ROTATE_180.toRaw(Box(0f, 0f, 100f, 50f), width, height))
            .isEqualTo(Box(300f, 250f, 400f, 300f))
    }

    @Test
    fun `unknown values read as normal`() {
        assertThat(PhotoOrientation.fromExif(0)).isEqualTo(PhotoOrientation.NORMAL)
        assertThat(PhotoOrientation.fromExif(9)).isEqualTo(PhotoOrientation.NORMAL)
        assertThat(PhotoOrientation.fromExif(6)).isEqualTo(PhotoOrientation.ROTATE_90)
        assertThat(PhotoOrientation.ROTATE_90.swapsAxes).isTrue()
        assertThat(PhotoOrientation.FLIP_VERTICAL.swapsAxes).isFalse()
    }
}
