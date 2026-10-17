package com.vpr.screenlate.experiments.ocrcompare

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Times the two ways of cropping a line for PP-OCR's recognizer on a 1280 × 2856 screen: the axis-aligned crop then
 * scale it replaced, and the warp along the line's rectangle (`core:ocr`'s `PaddlePipeline`), upright and turned by 5°.
 * Runs only when named (`-e class …CropTimingTest`); logs the mean milliseconds per line under [TAG].
 */
@RunWith(AndroidJUnit4::class)
class CropTimingTest {

    @Test
    fun time() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("class").orEmpty().contains("CropTimingTest"))
        val image = screen()
        // Lines 900 × 40 and 300 × 28 down the screen, as on a page of text.
        val lines = (0 until 30).map { i ->
            if (i % 2 == 0) Line(100f, 80f + i * 90f, 900f, 40f) else Line(400f, 80f + i * 90f, 300f, 28f)
        }
        repeat(ROUNDS) { round ->
            val axis = lines.sumOf { line -> timed { axisAligned(image, line) } }
            val upright = lines.sumOf { line -> timed { warped(image, line, 0.0) } }
            val turned = lines.sumOf { line -> timed { warped(image, line, 5.0) } }
            if (round > 0) {
                Log.w(TAG, "round $round ms per line: axis-aligned %.3f, warp %.3f, warp 5° %.3f".format(
                    axis / 1e6 / lines.size, upright / 1e6 / lines.size, turned / 1e6 / lines.size))
            }
        }
    }

    private data class Line(val x: Float, val y: Float, val length: Float, val thickness: Float)

    private fun screen(): Bitmap {
        val bitmap = Bitmap.createBitmap(1280, 2856, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; color = Color.BLACK }
        for (row in 0 until 30) {
            canvas.drawText("The quick brown fox jumps over the lazy dog $row", 100f, 110f + row * 90f, paint)
        }
        return bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
    }

    private inline fun timed(block: () -> IntArray): Long {
        val start = System.nanoTime()
        check(block().isNotEmpty())
        return System.nanoTime() - start
    }

    private fun content(line: Line) = maxOf(1, ceil(HEIGHT * line.length / line.thickness).toInt())

    private fun axisAligned(image: Bitmap, line: Line): IntArray {
        val crop =
            Bitmap.createBitmap(image, line.x.toInt(), line.y.toInt(), line.length.toInt(), line.thickness.toInt())
        val width = content(line)
        val scaled = Bitmap.createScaledBitmap(crop, width, HEIGHT, true)
        crop.recycle()
        val pixels = IntArray(width * HEIGHT)
        scaled.getPixels(pixels, 0, width, 0, 0, width, HEIGHT)
        scaled.recycle()
        return pixels
    }

    private fun warped(image: Bitmap, line: Line, degrees: Double): IntArray {
        val width = content(line)
        val alongX = cos(Math.toRadians(degrees)).toFloat()
        val alongY = sin(Math.toRadians(degrees)).toFloat()
        val scaleX = width / line.length
        val scaleY = HEIGHT / line.thickness
        val values = floatArrayOf(
            scaleX * alongX, scaleX * alongY, -scaleX * (line.x * alongX + line.y * alongY),
            -scaleY * alongY, scaleY * alongX, -scaleY * (-line.x * alongY + line.y * alongX),
            0f, 0f, 1f,
        )
        val toCrop = Matrix().apply { setValues(values) }
        val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(toCrop) }
        val crop = Bitmap.createBitmap(width, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(crop).drawPaint(Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        val pixels = IntArray(width * HEIGHT)
        crop.getPixels(pixels, 0, width, 0, 0, width, HEIGHT)
        crop.recycle()
        return pixels
    }

    private companion object {
        const val TAG = "CropTiming"
        const val HEIGHT = 48
        const val ROUNDS = 11
    }
}
