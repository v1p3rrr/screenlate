package com.vpr.screenlate.core.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngine
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.isVerticalLine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** On-device OCR with the bundled ML Kit Japanese model. Fast but weaker on vertical text and manga. */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    override val type = OcrEngineType.ML_KIT

    private val recognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        val text = recognizer.process(InputImage.fromBitmap(image, 0)).await()
        val separator = if (language == Language.JAPANESE) "" else " "
        val paragraphs = text.textBlocks.map { block ->
            OcrParagraph(block.lines.mapNotNull { line -> line.toOcrLine(separator) })
        }.filter { it.lines.isNotEmpty() }
        return OcrPage(image.width, image.height, paragraphs, OcrEngineType.ML_KIT)
    }

    private fun Text.Line.toOcrLine(separator: String): OcrLine? {
        val lineBox = boundingBox?.toBox() ?: return null
        val words = elements.mapNotNull { element ->
            val box = element.boundingBox?.toBox() ?: return@mapNotNull null
            val symbolBoxes = element.symbols.mapNotNull { it.boundingBox?.toBox() }
            val characterCount = element.text.codePointCount(0, element.text.length)
            OcrWord(
                text = element.text,
                separator = separator,
                box = box,
                characterBoxes = symbolBoxes.takeIf { it.size == characterCount },
            )
        }
        if (words.isEmpty()) return null
        return OcrLine(words, lineBox, isVerticalLine(lineBox, text))
    }

    private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
