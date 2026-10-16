package com.vpr.screenlate.core.ocr.paddle

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import kotlin.math.ceil

/**
 * PP-OCR on a bitmap: the [detector] finds the lines, the [recognizer] reads each with its [dictionary] (blank first,
 * space last, see [dictionary]). One line per paragraph. Not for concurrent use.
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
        val read = lines.mapNotNull { read(image, it.box) }
        return OcrPage(image.width, image.height, read.map { OcrParagraph(listOf(it)) }, OcrEngineType.DEVICE_MODEL)
    }

    private fun read(image: Bitmap, box: Box): OcrLine? {
        val left = box.left.toInt()
        val top = box.top.toInt()
        val right = ceil(box.right).toInt().coerceAtMost(image.width)
        val bottom = ceil(box.bottom).toInt().coerceAtMost(image.height)
        if (right - left < 2 || bottom - top < 2) return null
        val crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top)
        val (content, width) = TextRecognition.widths(crop.width, crop.height)
        val pixels = pixels(crop, content, TextRecognition.HEIGHT)
        if (crop !== image) crop.recycle()
        val output = recognizer.run(TextRecognition.input(pixels, content, width))
        val decoded = TextRecognition.decode(output, dictionary)
        val lineBox = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        val words = TextRecognition.words(decoded, lineBox, content.toFloat() / width)
        return if (words.isEmpty()) null else OcrLine(words, lineBox, vertical = false)
    }

    companion object {
        const val DETECTION_WIDTH = 960

        /** The recognizer's classes from its dictionary file's lines (one character each): blank, those, space. */
        fun dictionary(characters: List<String>): List<String> = listOf("") + characters + " "
    }
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
