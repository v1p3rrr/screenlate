package com.vpr.screenlate.core.ocr.paddle

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph

/**
 * PP-OCR on a bitmap: the [detector] finds the lines, the [recognizer] reads each with its [dictionary] (blank first,
 * space last, see [dictionary]). Each line is cropped along its rectangle, so turned lines are read upright; words and
 * characters get the bounding boxes of their parts of it. One line per paragraph. Not for concurrent use.
 *
 * @param detectionWidth the width the image is scaled down to for the detector.
 */
class PaddlePipeline(
    private val detector: PaddleModel,
    private val recognizer: PaddleModel,
    private val dictionary: List<String>,
    private val detectionWidth: Int = DETECTION_WIDTH,
) {
    fun recognize(image: Bitmap): OcrPage {
        val (width, height) = TextDetection.inputSize(image.width, image.height, detectionWidth)
        val map = detector.run(TextDetection.input(pixels(image, width, height), width, height))
        val lines = TextDetection.lines(map.data, map.shape[3], map.shape[2], image.width, image.height)
        val bounds = Box(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val read = lines.mapNotNull { read(image, it, bounds) }
        return OcrPage(image.width, image.height, read.map { OcrParagraph(listOf(it)) }, OcrEngineType.DEVICE_MODEL)
    }

    private fun read(image: Bitmap, line: DetectedLine, bounds: Box): OcrLine? {
        if (line.area.length < 2 || line.area.thickness < 2) return null
        // A column is read turned a quarter counterclockwise, its top first, as PP-OCR does for tall crops.
        val vertical = line.area.thickness >= VERTICAL_RATIO * line.area.length
        val area = if (vertical) line.area.turned() else line.area
        val (content, width) = TextRecognition.widths(area.length, area.thickness)
        val pixels = crop(image, area, content, TextRecognition.HEIGHT)
        val output = recognizer.run(TextRecognition.input(pixels, content, width))
        val decoded = TextRecognition.decode(output, dictionary)
        val words = TextRecognition.words(decoded, area, content.toFloat() / width, bounds)
        return if (words.isEmpty()) null else OcrLine(words, line.box, vertical)
    }

    companion object {
        const val DETECTION_WIDTH = 960

        /** A line at least this many times taller than wide is a column. */
        const val VERTICAL_RATIO = 1.5f

        /** The recognizer's classes from its dictionary file's lines (one character each): blank, those, space. */
        fun dictionary(characters: List<String>): List<String> = listOf("") + characters + " "
    }
}

/**
 * ARGB pixels of [area] of [image], upright and scaled to [width] × [height]. Where the area reaches past the image,
 * its edge pixels repeat, as in PP-OCR's crop.
 */
private fun crop(image: Bitmap, area: OrientedBox, width: Int, height: Int): IntArray {
    val toCrop = Matrix().apply { setValues(area.toCrop(width, height) + floatArrayOf(0f, 0f, 1f)) }
    val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(toCrop) }
    val crop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(crop).drawPaint(Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
    val pixels = IntArray(width * height)
    crop.getPixels(pixels, 0, width, 0, 0, width, height)
    crop.recycle()
    return pixels
}

/** ARGB pixels of [bitmap] scaled to [width] × [height]. */
private fun pixels(bitmap: Bitmap, width: Int, height: Int): IntArray {
    val scaled = if (bitmap.width == width && bitmap.height == height) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    if (scaled !== bitmap) scaled.recycle()
    return pixels
}
