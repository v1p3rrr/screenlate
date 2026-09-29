package com.vpr.screenlate.overlay.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CropViewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Immutable, as screenshots are: for these Android returns the source itself for a crop of the whole. */
    private val screenshot = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.RED) }
        .copy(Bitmap.Config.ARGB_8888, false)

    @Test
    fun theWholeScreenshotIsCopied() {
        val view = CropView(context, screenshot, 0f, 100f)
        view.selectAll()

        val cropped = view.cropped()
        assertThat(cropped).isNotSameInstanceAs(screenshot)
        assertThat(cropped.width).isEqualTo(40)
        assertThat(cropped.height).isEqualTo(30)

        // The note frees its picture; the screenshot stays usable for the next note.
        cropped.recycle()
        assertThat(screenshot.isRecycled).isFalse()
        assertThat(screenshot.getPixel(0, 0)).isEqualTo(Color.RED)
    }

    @Test
    fun aFrameIsCroppedInScreenCoordinates() {
        val view = CropView(context, screenshot, 0f, 100f)
        view.setFrame(RectF(10f, 110f, 30f, 120f))

        val cropped = view.cropped()
        assertThat(cropped.width).isEqualTo(20)
        assertThat(cropped.height).isEqualTo(10)
    }
}
