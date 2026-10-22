package com.vpr.screenlate.core.ocr.tesseract

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.isRightToLeftText
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.ModelOcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tesseract with its LSTM models (tessdata_fast, tessdata_best). A model's directory holds `tessdata/<code>.traineddata`,
 * the layout Tesseract reads, and the code names the language to Tesseract. One model is loaded at a time.
 */
@Singleton
class TesseractOcrEngine @Inject constructor() : ModelOcrEngine {
    override val id = ID

    private val mutex = Mutex()
    private var loaded: Loaded? = null

    private class Loaded(val directory: File, val version: String, val api: TessBaseAPI)

    // Tesseract keeps reading its copy of the image until it is done; like ML Kit, a cancelled call waits for it.
    override suspend fun recognize(image: Bitmap, language: Language, model: InstalledOcrModel, required: List<InstalledOcrModel>): OcrPage =
        withContext(Dispatchers.Default + NonCancellable) {
            mutex.withLock {
                val api = api(model)
                val input = prepared(image)
                try {
                    api.setImage(input)
                    api.getUTF8Text()
                    val paragraphs = api.resultIterator?.let { iterator ->
                        try {
                            read(iterator)
                        } finally {
                            iterator.delete()
                        }
                    }.orEmpty()
                    val support = language.support
                    tesseractPage(paragraphs, image.width, image.height, support.wordSeparator, support.ocrSpaceGap)
                } finally {
                    api.clear()
                    if (input !== image) input.recycle()
                }
            }
        }

    override fun release() {
        // Not under the mutex: a scan in progress keeps its model and frees it at the next release or model change.
        if (!mutex.tryLock()) return
        try {
            loaded?.api?.recycle()
            loaded = null
        } finally {
            mutex.unlock()
        }
    }

    private fun api(model: InstalledOcrModel): TessBaseAPI {
        loaded?.takeIf { it.directory == model.directory && it.version == model.version }?.let { return it.api }
        loaded?.api?.recycle()
        loaded = null
        val data = File(model.directory, DATA_DIRECTORY).listFiles { file -> file.name.endsWith(DATA_SUFFIX) }
            ?.singleOrNull() ?: throw IOException("The model has no traineddata file")
        val api = TessBaseAPI()
        if (!api.init(model.directory.path, data.name.removeSuffix(DATA_SUFFIX), TessBaseAPI.OEM_LSTM_ONLY)) {
            api.recycle()
            throw IOException("Tesseract could not load the model")
        }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        Log.i(TAG, "Loaded ${model.id} ${model.version}")
        loaded = Loaded(model.directory, model.version, api)
        return api
    }

    /** Tesseract reads dark text on a light background; a dark image is inverted first. */
    private fun prepared(image: Bitmap): Bitmap {
        val readable = if (image.config == Bitmap.Config.ARGB_8888) image else image.copy(Bitmap.Config.ARGB_8888, false)
        if (!isDark(readable)) return readable
        val pixels = IntArray(readable.width * readable.height)
        readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
        for (i in pixels.indices) pixels[i] = pixels[i] xor 0x00FFFFFF
        if (readable !== image) readable.recycle()
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    /** Whether the mean luminance of every [SAMPLE_STEP]th pixel in both directions is below the middle. */
    private fun isDark(image: Bitmap): Boolean {
        var sum = 0L
        var count = 0
        for (y in 0 until image.height step SAMPLE_STEP) {
            for (x in 0 until image.width step SAMPLE_STEP) {
                val pixel = image.getPixel(x, y)
                sum += (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                count++
            }
        }
        return count > 0 && sum / count < 128
    }

    /** Paragraphs of lines of words, in reading order, from the iterator over symbols. */
    private fun read(iterator: ResultIterator): List<List<TesseractLine>> {
        val paragraphs = ArrayList<List<TesseractLine>>()
        val lines = ArrayList<TesseractLine>()
        val words = ArrayList<TesseractWord>()
        var lineBox: Box? = null
        var word: Pair<String, Box>? = null
        val symbols = ArrayList<Box>()
        fun endWord() {
            word?.let { (text, box) -> words += TesseractWord(text, box, symbols.toList()) }
            word = null
            symbols.clear()
        }
        fun endLine() {
            endWord()
            lineBox?.let { box -> if (words.isNotEmpty()) lines += TesseractLine(box, words.toList()) }
            words.clear()
        }
        fun endParagraph() {
            endLine()
            if (lines.isNotEmpty()) paragraphs += lines.toList()
            lines.clear()
        }
        iterator.begin()
        do {
            if (iterator.isAtBeginningOf(PageIteratorLevel.RIL_PARA)) endParagraph()
            if (iterator.isAtBeginningOf(PageIteratorLevel.RIL_TEXTLINE)) {
                endLine()
                lineBox = iterator.getBoundingRect(PageIteratorLevel.RIL_TEXTLINE).toBox()
            }
            if (iterator.isAtBeginningOf(PageIteratorLevel.RIL_WORD)) {
                endWord()
                val text = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD)
                if (text != null) word = text to iterator.getBoundingRect(PageIteratorLevel.RIL_WORD).toBox()
            }
            symbols += iterator.getBoundingRect(PageIteratorLevel.RIL_SYMBOL).toBox()
        } while (iterator.next(PageIteratorLevel.RIL_SYMBOL))
        endParagraph()
        return paragraphs
    }

    private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    companion object {
        /** The engine of Tesseract's models in the catalog. */
        const val ID = "tesseract"
        private const val TAG = "TesseractOcr"
        private const val DATA_DIRECTORY = "tessdata"
        private const val DATA_SUFFIX = ".traineddata"
        private const val SAMPLE_STEP = 8
    }
}

internal class TesseractWord(val text: String, val box: Box, val symbols: List<Box>)

internal class TesseractLine(val box: Box, val words: List<TesseractWord>)

/**
 * The page of what Tesseract read. Blank words are left out, and so are lines and paragraphs left empty. A word box that
 * reaches over the word before it in reading order is cut at that word: the Arabic model gave the first letter of a
 * vowelled word a box from the line's start (كِتَابًا spanning the two words before it). A word keeps its symbol boxes
 * only when there is one per character, each inside its (cut) box. The models offered read horizontal text.
 *
 * @param spaceGap with a language whose words Tesseract cuts into clusters (`LanguageSupport.ocrSpaceGap`), the gap in
 *   line heights from which two words are parted by a space; closer ones are joined, the last of a line gets nothing.
 *   Null: every word gets [separator].
 */
internal fun tesseractPage(
    paragraphs: List<List<TesseractLine>>,
    width: Int,
    height: Int,
    separator: String,
    spaceGap: Float? = null,
): OcrPage =
    OcrPage(
        width,
        height,
        paragraphs.mapNotNull { lines ->
            lines.mapNotNull { line ->
                val read = line.words.filter { it.text.isNotBlank() }
                val rightToLeft = isRightToLeftText(read.joinToString("") { it.text })
                var previous: Box? = null
                val words = read.mapIndexed { index, word ->
                    val box = previous?.let { word.box.cutAt(it, rightToLeft) } ?: word.box
                    previous = box
                    val count = word.text.codePointCount(0, word.text.length)
                    val symbols = word.symbols.takeIf { boxes -> boxes.size == count && boxes.all { it.isInside(box) } }
                    val next = read.getOrNull(index + 1)
                    val after = when {
                        spaceGap == null -> separator
                        next != null && next.box.left - word.box.right > spaceGap * line.box.height -> " "
                        else -> ""
                    }
                    OcrWord(word.text, after, box, symbols)
                }
                if (words.isEmpty()) return@mapNotNull null
                OcrLine(words, line.box, vertical = false, rightToLeft = rightToLeft)
            }.takeIf { it.isNotEmpty() }?.let(::OcrParagraph)
        },
        OcrEngineType.DEVICE_MODEL,
    )

/**
 * This word box without the part over [previous], the word before it in reading order, when the box ends past the
 * middle of that word in the reading direction (its left edge left of it in a right-to-left line); unchanged when
 * nothing would be left. A Latin run inside a right-to-left line, whose words come in reading order from left to right,
 * is left as read.
 */
private fun Box.cutAt(previous: Box, rightToLeft: Boolean): Box {
    val cut = when {
        rightToLeft && left < previous.centerX -> copy(right = minOf(right, previous.left))
        !rightToLeft && right > previous.centerX -> copy(left = maxOf(left, previous.right))
        else -> return this
    }
    return if (cut.width > 0f) cut else this
}

private fun Box.isInside(other: Box) =
    width > 0f && height > 0f && left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TesseractModule {
    @Binds
    @IntoSet
    abstract fun bindEngine(engine: TesseractOcrEngine): ModelOcrEngine
}
