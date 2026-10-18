package com.vpr.screenlate.core.ocr.tesseract

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.isRightToLeftText
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.ModelOcrEngine
import com.vpr.screenlate.diagnostics.Diag
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
                val started = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
                val cold = loaded?.let { it.directory != model.directory || it.version != model.version } ?: true
                val api = api(model)
                val loadedAt = SystemClock.elapsedRealtime()
                val input = prepared(image)
                try {
                    val preparedAt = SystemClock.elapsedRealtime()
                    api.setImage(input)
                    Diag.trace("tesseract_recognize") { api.getUTF8Text() }
                    val recognizedAt = SystemClock.elapsedRealtime()
                    val paragraphs = api.resultIterator?.let { iterator ->
                        try {
                            read(iterator)
                        } finally {
                            iterator.delete()
                        }
                    }.orEmpty()
                    // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
                    val now = SystemClock.elapsedRealtime()
                    Diag.ocrPass(
                        "tesseract", "recognize", "${model.id} ${model.version}", image.width, image.height, now - started,
                        "load_ms" to loadedAt - started, "prepare_ms" to preparedAt - loadedAt,
                        "recognize_ms" to recognizedAt - preparedAt, "read_ms" to now - recognizedAt, "cold" to cold,
                        "inverted" to (input !== image && image.config == Bitmap.Config.ARGB_8888), "paragraphs" to paragraphs.size,
                    )
                    tesseractPage(paragraphs, image.width, image.height, language.support.wordSeparator)
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
            loaded?.let { Diag.event("model_release", "engine" to "tesseract", "model" to it.directory.name) } // TEMPORARY DIAGNOSTICS BUILD
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
        val started = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        if (!Diag.trace("tesseract_load") { api.init(model.directory.path, data.name.removeSuffix(DATA_SUFFIX), TessBaseAPI.OEM_LSTM_ONLY) }) {
            api.recycle()
            throw IOException("Tesseract could not load the model")
        }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        Log.i(TAG, "Loaded ${model.id} ${model.version}")
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        Diag.event(
            "model_load", "engine" to "tesseract", "model" to "${model.id} ${model.version}",
            "ms" to SystemClock.elapsedRealtime() - started, "file_kb" to (data.length() shr 10),
        )
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
 * The page of what Tesseract read. Blank words are left out, and so are lines and paragraphs left empty. A word keeps its
 * symbol boxes only when there is one per character, each inside the word. The models offered read horizontal text.
 */
internal fun tesseractPage(paragraphs: List<List<TesseractLine>>, width: Int, height: Int, separator: String): OcrPage =
    OcrPage(
        width,
        height,
        paragraphs.mapNotNull { lines ->
            lines.mapNotNull { line ->
                val words = line.words.filter { it.text.isNotBlank() }.map { word ->
                    val count = word.text.codePointCount(0, word.text.length)
                    val symbols = word.symbols.takeIf { boxes -> boxes.size == count && boxes.all { it.isInside(word.box) } }
                    OcrWord(word.text, separator, word.box, symbols)
                }
                if (words.isEmpty()) return@mapNotNull null
                OcrLine(words, line.box, vertical = false, rightToLeft = isRightToLeftText(words.joinToString("") { it.text }))
            }.takeIf { it.isNotEmpty() }?.let(::OcrParagraph)
        },
        OcrEngineType.DEVICE_MODEL,
    )

private fun Box.isInside(other: Box) =
    width > 0f && height > 0f && left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TesseractModule {
    @Binds
    @IntoSet
    abstract fun bindEngine(engine: TesseractOcrEngine): ModelOcrEngine
}
