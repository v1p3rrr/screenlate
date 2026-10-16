package com.vpr.screenlate.experiments.ocrcompare

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

/**
 * PP-OCR. A [rightToLeft] recognizer (Arabic) reads its crop from left to right, so each line's words and their
 * letters are turned into reading order, as PaddleOCR does.
 */
class PaddleReader(
    private val detector: PaddleModel,
    private val recognizer: PaddleModel,
    dictionary: List<String>,
    detectionWidth: Int,
    private val rightToLeft: Boolean = false,
) : Reader {
    private val pipeline = PaddlePipeline(detector, recognizer, PaddlePipeline.dictionary(dictionary), detectionWidth)

    override fun recognize(image: Bitmap): OcrPage {
        val page = pipeline.recognize(image)
        if (!rightToLeft) return page
        return page.copy(paragraphs = page.paragraphs.map { paragraph ->
            paragraph.copy(lines = paragraph.lines.map { line ->
                line.copy(
                    words = line.words.reversed().map { word ->
                        word.copy(text = word.text.reversed(), characterBoxes = word.characterBoxes?.reversed())
                    },
                    rightToLeft = true,
                )
            })
        })
    }

    override fun close() {
        detector.close()
        recognizer.close()
    }
}

/**
 * ML Kit as the app uses it for [language]: the Japanese, Chinese or Korean model; Latin text (English) goes to the
 * Japanese model, which also reads Latin letters.
 */
class MlKitReader(private val language: Language) : Reader {
    private val engine = MlKitOcrEngine()

    override fun recognize(image: Bitmap): OcrPage = runBlocking { engine.recognize(image, language) }

    override fun close() = engine.release()
}

/** ML Kit's Latin model, which the app does not carry. Symbol boxes as ML Kit gives them, without the app's checks. */
class MlKitLatinReader : Reader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun recognize(image: Bitmap): OcrPage {
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)))
        val paragraphs = text.textBlocks.map { block ->
            OcrParagraph(block.lines.mapNotNull { line ->
                val lineBox = line.boundingBox?.toBox() ?: return@mapNotNull null
                val words = line.elements.mapNotNull { element ->
                    val box = element.boundingBox?.toBox() ?: return@mapNotNull null
                    val symbols = element.symbols.mapNotNull { it.boundingBox?.toBox() }
                    val count = element.text.codePointCount(0, element.text.length)
                    OcrWord(element.text, " ", box, symbols.takeIf { it.size == count })
                }
                if (words.isEmpty()) null else OcrLine(words, lineBox, vertical = false)
            })
        }.filter { it.lines.isNotEmpty() }
        return OcrPage(image.width, image.height, paragraphs, OcrEngineType.ML_KIT)
    }

    override fun close() = recognizer.close()
}

/**
 * Tesseract with its LSTM models and automatic page segmentation. A dark screen (mean luminance below half) is
 * inverted first, as Tesseract expects dark text on a light background.
 *
 * Words come with their symbols' boxes. With a `_vert` model a line clearly taller than wide is a column.
 *
 * @param dataPath the folder holding `tessdata/<language>.traineddata`.
 */
class TesseractReader(dataPath: String, language: String) : Reader {
    private val vertical = language.endsWith("_vert")

    private val api = TessBaseAPI().apply {
        check(init(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)) { "Tesseract could not load $language" }
        pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
    }

    override fun recognize(image: Bitmap): OcrPage {
        val input = if (isDark(image)) inverted(image) else image
        api.setImage(input)
        api.getUTF8Text()
        if (input !== image) input.recycle()
        val iterator = api.resultIterator ?: return OcrPage(image.width, image.height, emptyList(), OcrEngineType.DEVICE_MODEL)
        val lines = ArrayList<OcrLine>()
        val words = ArrayList<OcrWord>()
        var lineBox: Box? = null
        var wordText: String? = null
        var wordBox: Box? = null
        val symbols = ArrayList<Box>()
        fun endWord() {
            val text = wordText
            if (!text.isNullOrBlank() && wordBox != null) {
                val count = text.codePointCount(0, text.length)
                words += OcrWord(text, " ", wordBox!!, symbols.toList().takeIf { it.size == count })
            }
            wordText = null
            symbols.clear()
        }
        fun endLine() {
            endWord()
            if (words.isEmpty()) return
            val box = lineBox ?: words.map { it.box }.reduce(Box::union)
            val text = words.joinToString("") { it.text }
            val vertical = this.vertical && text.codePointCount(0, text.length) >= 2 && box.height > box.width
            lines += OcrLine(words.toList(), box, vertical)
            words.clear()
        }
        iterator.begin()
        do {
            if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)) {
                endLine()
                lineBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE).toBox()
            }
            if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_WORD)) {
                endWord()
                wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                wordBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD).toBox()
            }
            symbols += iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL).toBox()
        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL))
        endLine()
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

private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
