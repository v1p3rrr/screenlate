package com.vpr.screenlate.experiments.ocrcompare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrPage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs each engine over the screenshots (`<language>-<name>.png`) and writes what it read and how long it took to
 * `<external files>/ocr-compare/<engine>.json`, for `scripts/ocr-compare/score.py`. The screenshots are those copied
 * to `<files>/images/` (`run-as`: files that adb pushes to external storage are not readable here), or else the test
 * assets' `images/`.
 *
 * `mlkit` is ML Kit as the app uses it (Japanese, Chinese, Korean and Latin text), `mlkit-latin` its Latin model.
 * Image names start with the language: `javert` is vertical Japanese, `zht` Traditional Chinese.
 *
 * Instrumentation arguments: `engines` (comma-separated, default all), `images` (a name prefix), `runs` (timed runs
 * after a warm-up, default 3), `threads` (default 4), `detWidth` (default 960).
 */
@RunWith(AndroidJUnit4::class)
class OcrCompareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private val threads = arguments.getString("threads")?.toInt() ?: 4
    private val detectionWidth = arguments.getString("detWidth")?.toInt() ?: 960
    private val models by lazy { copyAssets("models", File(context.filesDir, "models")) }

    @Test
    fun compare() {
        val engines = (arguments.getString("engines") ?: ENGINES.joinToString(",")).split(",")
        val runs = arguments.getString("runs")?.toInt() ?: 3
        val prefix = arguments.getString("images") ?: ""
        val out = File(context.getExternalFilesDir(null), "ocr-compare").apply { mkdirs() }
        val copied = File(context.filesDir, "images")
        val pushed = copied.list().orEmpty().toList()
        val names = pushed.ifEmpty { assets.list("images").orEmpty().toList() }
        val images = names.filter { it.endsWith(".png") && it.startsWith(prefix) }.sorted()
        fun open(file: String) = if (pushed.isEmpty()) assets.open("images/$file") else File(copied, file).inputStream()
        for (engine in engines) {
            val results = JSONArray()
            for ((language, files) in images.groupBy { it.substringBefore('-') }) {
                val reader = reader(engine, language) ?: continue
                reader.use {
                    for (file in files) {
                        val image = open(file).use { stream ->
                            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            })
                        }!!
                        reader.recognize(image)
                        var page: OcrPage? = null
                        val times = (1..runs).map {
                            val start = System.nanoTime()
                            page = reader.recognize(image)
                            (System.nanoTime() - start) / 1_000_000
                        }
                        results.put(result(file, language, page!!, times))
                        image.recycle()
                    }
                }
            }
            File(out, "$engine.json").writeText(results.toString())
        }
    }

    private fun reader(engine: String, language: String): Reader? {
        val script = SCRIPTS[language] ?: return null
        return when (engine) {
            "paddle-ort", "paddle-mnn", "paddle-ort-cyrillic", "paddle-mnn-cyrillic" -> {
                val recognizer = if (engine.endsWith("cyrillic")) "cyrillic_PP-OCRv5_mobile_rec" else script.paddle
                val dictionary = File(models, "$recognizer/dict.txt").readLines(Charsets.UTF_8)
                if (engine.startsWith("paddle-ort")) {
                    PaddleReader(
                        OrtModel(File(models, "$DETECTOR/inference.onnx").readBytes(), threads),
                        OrtModel(File(models, "$recognizer/inference.onnx").readBytes(), threads),
                        dictionary,
                        detectionWidth,
                        rightToLeft = language == "ar",
                    )
                } else {
                    PaddleReader(
                        MnnModel(File(models, "$DETECTOR/model.mnn").path, threads),
                        MnnModel(File(models, "$recognizer/model.mnn").path, threads),
                        dictionary,
                        detectionWidth,
                        rightToLeft = language == "ar",
                    )
                }
            }
            "mlkit" -> script.mlKit?.let { MlKitReader(it) }
            "mlkit-latin" -> if (script.paddle == LATIN) MlKitLatinReader() else null
            "tesseract-fast" -> TesseractReader(File(models, "tessdata_fast").path, script.tesseract)
            "tesseract-best" -> TesseractReader(File(models, "tessdata_best").path, script.tesseract)
            else -> error("Unknown engine $engine")
        }
    }

    private fun result(file: String, language: String, page: OcrPage, times: List<Long>) = JSONObject().apply {
        put("image", file)
        put("language", language)
        put("width", page.width)
        put("height", page.height)
        put("times_ms", JSONArray(times))
        put("lines", JSONArray(page.paragraphs.flatMap { it.lines }.map { line ->
            JSONObject().apply {
                put("box", box(line.box))
                put("text", line.text)
                put("vertical", line.vertical)
                put("words", JSONArray(line.words.map { word ->
                    JSONObject().apply {
                        put("text", word.text)
                        put("box", box(word.box))
                        word.characterBoxes?.let { boxes -> put("chars", JSONArray(boxes.map(::box))) }
                    }
                }))
            }
        }))
    }

    private fun box(box: Box) = JSONArray(listOf(box.left, box.top, box.right, box.bottom).map { it.toDouble() })

    /** Copies an asset folder to [target] once; Tesseract and MNN read files. */
    private fun copyAssets(path: String, target: File): File {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        } else {
            children.forEach { copyAssets("$path/$it", File(target, it)) }
        }
        return target
    }

    /** The Paddle recognizer, the Tesseract model and the language ML Kit reads it as (null: no ML Kit model). */
    private class Script(val paddle: String, val tesseract: String, val mlKit: Language? = null)

    private companion object {
        const val DETECTOR = "PP-OCRv5_mobile_det"
        const val MAIN = "PP-OCRv5_mobile_rec"
        const val LATIN = "latin_PP-OCRv5_mobile_rec"
        val ENGINES = listOf("paddle-ort", "paddle-mnn", "tesseract-fast", "tesseract-best", "mlkit", "mlkit-latin")
        val SCRIPTS = mapOf(
            "ja" to Script(MAIN, "jpn", Language.JAPANESE),
            "javert" to Script(MAIN, "jpn_vert", Language.JAPANESE),
            "zh" to Script(MAIN, "chi_sim", Language.CHINESE),
            "zht" to Script(MAIN, "chi_tra", Language.CHINESE),
            "ko" to Script("korean_PP-OCRv5_mobile_rec", "kor", Language.KOREAN),
            "en" to Script(LATIN, "eng", Language.ENGLISH),
            "es" to Script(LATIN, "spa", Language.ENGLISH),
            "fr" to Script(LATIN, "fra", Language.ENGLISH),
            "de" to Script(LATIN, "deu", Language.ENGLISH),
            "it" to Script(LATIN, "ita", Language.ENGLISH),
            "pt" to Script(LATIN, "por", Language.ENGLISH),
            "pl" to Script(LATIN, "pol", Language.ENGLISH),
            "tr" to Script(LATIN, "tur", Language.ENGLISH),
            "vi" to Script(LATIN, "vie", Language.ENGLISH),
            "lv" to Script(LATIN, "lav", Language.ENGLISH),
            "ru" to Script("eslav_PP-OCRv5_mobile_rec", "rus"),
            "el" to Script("el_PP-OCRv5_mobile_rec", "ell"),
            "th" to Script("th_PP-OCRv5_mobile_rec", "tha"),
            "ar" to Script("arabic_PP-OCRv5_mobile_rec", "ara"),
        )
    }
}
