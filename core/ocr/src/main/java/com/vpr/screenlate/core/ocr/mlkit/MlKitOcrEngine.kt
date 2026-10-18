package com.vpr.screenlate.core.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.OcrScript
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.ocr.OcrEngine
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.SymbolLag
import com.vpr.screenlate.core.ocr.isVerticalLine
import com.vpr.screenlate.diagnostics.Diag
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * On-device OCR with ML Kit. The Latin, Japanese, Chinese and Korean models are bundled (the Latin one adds little: the
 * Japanese model carries its recognizer). Latin-script languages use the Latin model, which reads them as well as the
 * Japanese one in less than half the time. Fast but weaker on vertical text and manga.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    override val type = OcrEngineType.ML_KIT

    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

    override fun reads(language: Language): Boolean = model(language.support.ocrScript) != null

    /** The bundled model that reads [script]. */
    private fun model(script: OcrScript): OcrScript? = when (script) {
        OcrScript.LATIN -> OcrScript.LATIN
        OcrScript.JAPANESE -> OcrScript.JAPANESE
        OcrScript.CHINESE -> OcrScript.CHINESE
        OcrScript.KOREAN -> OcrScript.KOREAN
        OcrScript.CYRILLIC, OcrScript.DEVANAGARI -> null
    }

    /** One recognizer per bundled model; scripts with a model of their own do not share (Japanese misreads Chinese). */
    private fun recognizer(script: OcrScript): TextRecognizer = synchronized(recognizers) {
        val model = model(script) ?: throw NoDeviceOcrException()
        recognizers.getOrPut(model) {
            Log.i(TAG, "Loading the ML Kit model for $model")
            // TEMPORARY DIAGNOSTICS BUILD: model loads and first runs (ai/notes/diagnostics.md).
            val (client, ms) = Diag.timed { Diag.trace("mlkit_load") { TextRecognition.getClient(options(model)) } }
            Diag.event("model_load", "engine" to "mlkit", "model" to model.name, "ms" to ms)
            diagCold += model
            client
        }
    }

    private fun options(model: OcrScript): TextRecognizerOptionsInterface = when (model) {
        OcrScript.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
        OcrScript.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
        OcrScript.LATIN -> TextRecognizerOptions.DEFAULT_OPTIONS
        else -> JapaneseTextRecognizerOptions.Builder().build()
    }

    override fun release() = synchronized(recognizers) {
        Diag.event("model_release", "engine" to "mlkit", "models" to recognizers.keys.map { it.name }) // TEMPORARY DIAGNOSTICS BUILD
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        val support = language.support
        val started = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        val recognizer = recognizer(support.ocrScript)
        val model = model(support.ocrScript)
        val cold = synchronized(recognizers) { model != null && diagCold.remove(model) }
        val cookie = diagCookie.incrementAndGet()
        Diag.beginAsync("mlkit_task", cookie)
        val task = recognizer.process(InputImage.fromBitmap(image, 0))
        // A running task cannot be stopped and keeps reading the pixels, so a cancelled call still waits for it.
        val text = withContext(NonCancellable) { task.await() }
        Diag.endAsync("mlkit_task", cookie)
        val taskMs = SystemClock.elapsedRealtime() - started
        val separator = support.wordSeparator
        // Checking every character against the pixels takes a while on a dense page; the caller may be the main thread.
        val paragraphs = withContext(Dispatchers.Default) {
            Diag.trace("mlkit_lines") {
                text.textBlocks.map { block ->
                    OcrParagraph(block.lines.mapNotNull { line -> line.toOcrLine(image, separator) })
                }.filter { it.lines.isNotEmpty() }
            }
        }
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        val ms = SystemClock.elapsedRealtime() - started
        Diag.ocrPass(
            "mlkit", "recognize", model?.name, image.width, image.height, ms,
            "task_ms" to taskMs, "lines_ms" to ms - taskMs, "cold" to cold,
            "paragraphs" to paragraphs.size, "lines" to paragraphs.sumOf { it.lines.size },
        )
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

    // TEMPORARY DIAGNOSTICS BUILD: models loaded but not yet run, and trace cookies (ai/notes/diagnostics.md).
    private val diagCold = mutableSetOf<OcrScript>()
    private val diagCookie = AtomicInteger()

    private companion object {
        const val TAG = "MlKitOcr"
    }
}

/** A cancelled task fails the call: cancelling the coroutine would pass for a cancellation of the caller. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.resumeWithException(IllegalStateException("ML Kit task cancelled")) }
}
