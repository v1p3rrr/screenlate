package com.vpr.screenlate.overlay.anki

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.capture.CapturedScreen
import com.vpr.screenlate.overlay.capture.SharedScreenshot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenNoteImageTest {
    /** Immutable, as screenshots are: for these Android returns the source itself for a crop of the whole. */
    private val screenshot = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        .apply {
            eraseColor(Color.RED)
            setPixel(10, 10, Color.BLUE)
        }
        .copy(Bitmap.Config.ARGB_8888, false)

    /** Taken 100 px below the screen's top, as a capture beside a bar may be. */
    private val image = ScreenNoteImage(SharedScreenshot(CapturedScreen(screenshot, 0, 100)))

    @Test
    fun theWholeScreenshotIsCopied() = runBlocking {
        val cropped = image.crop(image.bounds)
        assertThat(cropped).isNotSameInstanceAs(screenshot)
        assertThat(cropped.width).isEqualTo(40)
        assertThat(cropped.height).isEqualTo(30)

        // The note frees its picture; the screenshot stays usable for the next note.
        cropped.recycle()
        assertThat(screenshot.isRecycled).isFalse()
        assertThat(screenshot.getPixel(0, 0)).isEqualTo(Color.RED)
    }

    @Test
    fun aFrameIsCroppedInScreenCoordinates() = runBlocking {
        val cropped = image.crop(Box(10f, 110f, 30f, 120f))
        assertThat(cropped.width).isEqualTo(20)
        assertThat(cropped.height).isEqualTo(10)
        assertThat(cropped.getPixel(0, 0)).isEqualTo(Color.BLUE)
    }

    @Test
    fun aFrameOverTheEdgeIsCutToTheScreenshot() = runBlocking {
        val cropped = image.crop(Box(30f, 90f, 60f, 110f))
        assertThat(cropped.width).isEqualTo(10)
        assertThat(cropped.height).isEqualTo(10)
    }

    @Test
    fun closingGivesTheScreenshotBackOnce() {
        val shared = SharedScreenshot(CapturedScreen(screenshot, 0, 0)) { it.recycle() }
        check(shared.retain())
        val note = ScreenNoteImage(shared)
        note.close()
        note.close()
        assertThat(screenshot.isRecycled).isFalse()
        shared.release()
        assertThat(screenshot.isRecycled).isTrue()
    }
}
