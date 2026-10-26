package com.vpr.screenlate.core.photos

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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

    private fun color(x: Int, y: Int) = Color.rgb(x % 251, y % 251, (x + y) % 251)
}
