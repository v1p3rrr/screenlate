package com.vpr.screenlate.core.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.OcrScript
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.ocr.OcrEngine
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.SymbolLag
import com.vpr.screenlate.core.ocr.isVerticalLine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * On-device OCR with ML Kit. Only the Japanese model is bundled for now; it also reads Latin text. Fast but weaker
 * on vertical text and manga.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    override val type = OcrEngineType.ML_KIT

    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

    private fun recognizer(script: OcrScript): TextRecognizer = synchronized(recognizers) {
        recognizers.getOrPut(script) {
            when (script) {
                OcrScript.JAPANESE, OcrScript.LATIN, OcrScript.CHINESE, OcrScript.DEVANAGARI, OcrScript.KOREAN ->
                    TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            }
        }
    }

    override fun release() = synchronized(recognizers) {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        val support = language.support
        val task = recognizer(support.ocrScript).process(InputImage.fromBitmap(image, 0))
        // A running task cannot be stopped and keeps reading the pixels, so a cancelled call still waits for it.
        val text = withContext(NonCancellable) { task.await() }
        val separator = support.wordSeparator
        val paragraphs = text.textBlocks.map { block ->
            OcrParagraph(block.lines.mapNotNull { line -> line.toOcrLine(image, separator) })
        }.filter { it.lines.isNotEmpty() }
        return OcrPage(image.width, image.height, paragraphs, OcrEngineType.ML_KIT)
    }

    private fun Text.Line.toOcrLine(image: Bitmap, separator: String): OcrLine? {
        val lineBox = boundingBox?.toBox() ?: return null
        val vertical = isVerticalLine(lineBox, text)
        val words = elements.mapNotNull { element ->
            val rect = element.boundingBox ?: return@mapNotNull null
            val count = element.text.codePointCount(0, element.text.length)
            val symbols = element.symbols.mapNotNull { it.boundingBox?.toBox() }.takeIf { it.size == count }
            OcrWord(
                text = element.text,
                separator = separator,
                box = rect.toBox(),
                characterBoxes = symbols?.let { aligned(image, rect, it, vertical) },
            )
        }
        if (words.isEmpty()) return null
        return OcrLine(words, lineBox, vertical)
    }

    /** Symbol boxes checked against the pixels, see [SymbolLag]. */
    private fun aligned(image: Bitmap, rect: Rect, symbols: List<Box>, vertical: Boolean): List<Box> {
        val area = Rect(rect)
        if (symbols.size < 3 || image.config == Bitmap.Config.HARDWARE) return symbols
        if (!area.intersect(0, 0, image.width, image.height)) return symbols
        val pixels = IntArray(area.width() * area.height())
        image.getPixels(pixels, 0, area.width(), area.left, area.top, area.width(), area.height())
        val contrast = SymbolLag.contrast(pixels, area.width(), area.height(), vertical)
        return SymbolLag.align(symbols, contrast, if (vertical) area.top else area.left, vertical)
    }

    private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
