package com.vpr.screenlate.camera

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoViewport
import org.junit.Test

class PhotoDrawingTest {
    @Test
    fun drawingAndHitTestingAgreeForEveryExifOrientation() {
        for (orientation in PhotoOrientation.entries) {
            val document = photo().copy(orientation = orientation)
            val view = PhotoViewport.fit(document.width, document.height, Box(80f, 190f, 1080f, 1800f))
            val matrix = imageToViewer(document, view, 80f, 190f)
            for ((x, y) in listOf(0f to 0f, 4000f to 3000f, 180f to 750f, 2000f to 1500f)) {
                val (uprightX, uprightY) = orientation.toOriented(x, y, 4000, 3000)
                val actual = matrix.map(Offset(x, y))
                assertThat(actual.x).isWithin(0.001f).of(uprightX * view.scale + view.offsetX - 80f)
                assertThat(actual.y).isWithin(0.001f).of(uprightY * view.scale + view.offsetY - 190f)
            }
        }
    }

    @Test
    fun samplingKeepsAtLeastTheDisplayedPixels() {
        for (scale in listOf(0.01f, 0.125f, 0.18f, 0.5f, 1f, 2f, 0.0000000001f)) {
            val sample = displaySampleSize(scale)
            assertThat(sample).isGreaterThan(0)
            assertThat(sample and (sample - 1)).isEqualTo(0)
            if (scale <= 1f) assertThat(sample * scale).isAtMost(1f)
            if (sample < (1 shl 30)) assertThat(sample * 2L * scale).isGreaterThan(1f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroScaleCannotLoopForever() {
        displaySampleSize(0f)
    }

    @Test
    fun flashCyclesThroughEveryMode() {
        assertThat(FlashMode.OFF.next).isEqualTo(FlashMode.AUTO)
        assertThat(FlashMode.AUTO.next).isEqualTo(FlashMode.ON)
        assertThat(FlashMode.ON.next).isEqualTo(FlashMode.OFF)
    }
}
