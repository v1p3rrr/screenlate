package com.vpr.screenlate.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoImageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun fractionalCropsUseOriginalUprightPixelsForEveryOrientation() = runBlocking {
        for (orientation in PhotoOrientation.entries) {
            val file = pattern(320, 240)
            PhotoImage(PhotoDocument(1, file, 320, 240, orientation, 0, 0, PhotoText.None)) { file.delete() }.use { image ->
                val preview = image.preview(40, 40)
                assertThat(preview.width).isAtMost(40)
                assertThat(preview.height).isAtMost(40)
                preview.recycle()
                val crop = image.crop(Box(50.2f, 30.3f, 90.1f, 60.7f))
                try {
                    assertThat(crop.width to crop.height).isEqualTo(41 to 31)
                    for ((x, y) in listOf(0 to 0, 20 to 15, 40 to 30)) {
                        val raw = orientation.toRaw(50 + x + 0.5f, 30 + y + 0.5f, 320, 240)
                        assertThat(crop.getPixel(x, y)).isEqualTo(color(raw.first.toInt(), raw.second.toInt()))
                    }
                } finally { crop.recycle() }
            }
            assertThat(file.exists()).isFalse()
        }
    }

    @Test
    fun aLargePhotoCropIsNotLimitedByItsSampledPreview() = runBlocking {
        val file = pattern(3200, 2400)
        PhotoImage(PhotoDocument(1, file, 3200, 2400, PhotoOrientation.NORMAL, 0, 0, PhotoText.None)) { file.delete() }.use { image ->
            val preview = image.preview(400, 300)
            assertThat(preview.width to preview.height).isEqualTo(400 to 300)
            val crop = image.crop(Box(1800f, 1200f, 2400f, 1600f))
            assertThat(crop.width to crop.height).isEqualTo(600 to 400)
            assertThat(crop.getPixel(0, 0)).isEqualTo(color(1800, 1200))
            preview.recycle()
            crop.recycle()
        }
    }

    @Test
    fun aCropOverThePixelLimitIsScaledDownUprightForEveryOrientation() = runBlocking {
        for (orientation in PhotoOrientation.entries) {
            val file = quadrants(640, 480)
            PhotoImage(PhotoDocument(1, file, 640, 480, orientation, 0, 0, PhotoText.None)) { file.delete() }.use { image ->
                val crop = image.crop(image.bounds, maxPixels = 40_000)
                try {
                    assertThat(crop.width to crop.height).isEqualTo(fittedSize(image.width, image.height, 40_000))
                    // The upright corner shows the quadrant of the raw pixel it came from.
                    for ((x, y) in listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.1f to 0.9f, 0.9f to 0.9f)) {
                        val raw = orientation.toRaw(x * image.width, y * image.height, 640, 480)
                        val pixel = crop.getPixel((x * crop.width).toInt(), (y * crop.height).toInt())
                        assertThat(pixel).isEqualTo(quadrant(raw.first < 320, raw.second < 240))
                    }
                } finally { crop.recycle() }
            }
        }
    }

    private fun quadrants(width: Int, height: Int): File {
        val file = File.createTempFile("photo-scaled-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { quadrant(it % width < width / 2, it / width < height / 2) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun quadrant(left: Boolean, top: Boolean) = when {
        left && top -> Color.RED
        top -> Color.GREEN
        left -> Color.BLUE
        else -> Color.WHITE
    }

    private fun pattern(width: Int, height: Int): File {
        val file = File.createTempFile("photo-crop-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { color(it % width, it / width) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
    private fun color(x: Int, y: Int) = Color.rgb(x % 251, y % 251, (x + y) % 251)
}
