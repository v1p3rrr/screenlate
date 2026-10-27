package com.vpr.screenlate.core.photos

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTiles
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoRegionsTest {
    @Test
    fun fullResolutionTilePixelsMatchEveryExifOrientationAndFarEdge() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("region-test-", ".png", context.cacheDir)
        val width = 2200
        val height = 1800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { i -> color(i % width, i / width) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            for (orientation in PhotoOrientation.entries) {
                val document = PhotoDocument(1, file, width, height, orientation, 0, 0, PhotoText.None)
                PhotoRegions(document).use { source ->
                    for (tile in PhotoTiles.grid(document.width, document.height)) {
                        val decoded = source.read(tile)
                        try {
                            assertThat(decoded.width to decoded.height).isEqualTo(tile.width to tile.height)
                            for ((x, y) in listOf(0 to 0, (tile.width - 1) to (tile.height - 1), tile.width / 2 to tile.height / 2)) {
                                val raw = orientation.toRaw(tile.left + x + 0.5f, tile.top + y + 0.5f, width, height)
                                assertThat(decoded.getPixel(x, y)).isEqualTo(color(raw.first.toInt(), raw.second.toInt()))
                            }
                        } finally { decoded.recycle() }
                    }
                }
            }
        } finally { file.delete() }
    }

    @Test
    fun aScaledReadHasTheAskedSizeAndKeepsTheUprightLayout() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("region-scaled-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2200, 1800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixels(IntArray(1100 * 900) { Color.RED }, 0, 1100, 0, 0, 1100, 900)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            for (orientation in PhotoOrientation.entries) {
                val document = PhotoDocument(1, file, 2200, 1800, orientation, 0, 0, PhotoText.None)
                PhotoRegions(document).use { source ->
                    val whole = PhotoTile(0, 0, document.width, document.height)
                    val size = fittedSize(whole.width, whole.height, maxSide = PhotoTiles.MAX_SIDE)
                    val decoded = source.read(whole, size = size)
                    try {
                        assertThat(decoded.width to decoded.height).isEqualTo(size)
                        for ((x, y) in listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.1f to 0.9f, 0.9f to 0.9f)) {
                            val raw = orientation.toRaw(x * document.width, y * document.height, 2200, 1800)
                            val red = raw.first < 1100 && raw.second < 900
                            assertThat(decoded.getPixel((x * size.first).toInt(), (y * size.second).toInt()))
                                .isEqualTo(if (red) Color.RED else Color.WHITE)
                        }
                    } finally { decoded.recycle() }
                }
            }
        } finally { file.delete() }
    }

    private fun color(x: Int, y: Int) = Color.rgb(x % 251, y % 251, (x + y) % 251)
}
