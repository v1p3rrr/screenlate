package com.vpr.screenlate.experiments.ocrcompare

import android.graphics.Bitmap
import android.graphics.Color
import com.googlecode.tesseract.android.TessBaseAPI
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import com.vpr.screenlate.core.ocr.paddle.PaddleModel
import com.vpr.screenlate.core.ocr.paddle.PaddlePipeline
import kotlinx.coroutines.runBlocking

/** An engine under comparison, set up for one language. */
interface Reader : AutoCloseable {
    fun recognize(image: Bitmap): OcrPage
}

class PaddleReader(
    private val detector: PaddleModel,
    private val recognizer: PaddleModel,
    dictionary: List<String>,
    detectionWidth: Int,
) : Reader {
    private val pipeline = PaddlePipeline(detector, recognizer, PaddlePipeline.dictionary(dictionary), detectionWidth)

    override fun recognize(image: Bitmap): OcrPage = pipeline.recognize(image)

    override fun close() {
        detector.close()
        recognizer.close()
    }
}

/** ML Kit as the app uses it for Latin text: its bundled Japanese model, which also reads Latin letters. */
class MlKitReader : Reader {
    private val engine = MlKitOcrEngine()

    override fun recognize(image: Bitmap): OcrPage = runBlocking { engine.recognize(image, Language.ENGLISH) }

    override fun close() = engine.release()
}

/**
 * Tesseract with its LSTM models and automatic page segmentation. A dark screen (mean luminance below half) is
 * inverted first, as Tesseract expects dark text on a light background.
 *
 * @param dataPath the folder holding `tessdata/<language>.traineddata`.
 */
class TesseractReader(dataPath: String, language: String) : Reader {
    private val api = TessBaseAPI().apply {
        check(init(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)) { "Tesseract could not load $language" }
        pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
    }

    override fun recognize(image: Bitmap): OcrPage {
        val input = if (isDark(image)) inverted(image) else image
        api.setImage(input)
        api.getUTF8Text()
        if (input !== image) input.recycle()
        val lines = ArrayList<OcrLine>()
        val iterator = api.resultIterator ?: return OcrPage(image.width, image.height, emptyList(), OcrEngineType.DEVICE_MODEL)
        val words = ArrayList<OcrWord>()
        var lineBox: Box? = null
        iterator.begin()
        do {
            if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)) {
                lineBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE).let {
                    Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                }
            }
            val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            val last = iterator.isAtFinalElement(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE, TessBaseAPI.PageIteratorLevel.RIL_WORD)
            if (!text.isNullOrBlank()) {
                val box = Box(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
                words += OcrWord(text, if (last) "" else " ", box)
            }
            if (last && words.isNotEmpty()) {
                lines += OcrLine(words.toList(), lineBox ?: words.map { it.box }.reduce(Box::union), vertical = false)
                words.clear()
            }
        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        iterator.delete()
        return OcrPage(image.width, image.height, lines.map { OcrParagraph(listOf(it)) }, OcrEngineType.DEVICE_MODEL)
    }

    override fun close() = api.recycle()

    private fun isDark(image: Bitmap): Boolean {
        val step = 8
        var sum = 0L
        var count = 0
        for (y in 0 until image.height step step) {
            for (x in 0 until image.width step step) {
                val pixel = image.getPixel(x, y)
                sum += (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                count++
            }
        }
        return count > 0 && sum / count < 128
    }

    private fun inverted(image: Bitmap): Bitmap {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        for (i in pixels.indices) pixels[i] = pixels[i] xor 0x00FFFFFF
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }
}
