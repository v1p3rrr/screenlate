package com.vpr.screenlate.experiments.ocrcompare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrPage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs each engine over the screenshots in the test assets (`images/<language>-<name>.png`) and writes what it read
 * and how long it took to `<external files>/ocr-compare/<engine>.json`, for `scripts/ocr-compare/score.py`.
 *
 * ML Kit runs only on Latin-script languages.
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
        val images = assets.list("images").orEmpty().filter { it.endsWith(".png") && it.startsWith(prefix) }.sorted()
        val out = File(context.getExternalFilesDir(null), "ocr-compare").apply { mkdirs() }
        for (engine in engines) {
            val results = JSONArray()
            for ((language, files) in images.groupBy { it.substringBefore('-') }) {
                val reader = reader(engine, language) ?: continue
                reader.use {
                    for (file in files) {
                        val image = assets.open("images/$file").use { stream ->
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
                    )
                } else {
                    PaddleReader(
                        MnnModel(File(models, "$DETECTOR/model.mnn").path, threads),
                        MnnModel(File(models, "$recognizer/model.mnn").path, threads),
                        dictionary,
                        detectionWidth,
                    )
                }
            }
            "mlkit" -> if (script.paddle.startsWith("latin")) MlKitReader() else null
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
                put("words", JSONArray(line.words.map { word ->
                    JSONObject().apply {
                        put("text", word.text)
                        put("box", box(word.box))
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

    private class Script(val paddle: String, val tesseract: String)

    private companion object {
        const val DETECTOR = "PP-OCRv5_mobile_det"
        val ENGINES = listOf("paddle-ort", "paddle-mnn", "tesseract-fast", "tesseract-best", "mlkit")
        val SCRIPTS = mapOf(
            "ru" to Script("eslav_PP-OCRv5_mobile_rec", "rus"),
            "pl" to Script("latin_PP-OCRv5_mobile_rec", "pol"),
            "tr" to Script("latin_PP-OCRv5_mobile_rec", "tur"),
            "vi" to Script("latin_PP-OCRv5_mobile_rec", "vie"),
            "lv" to Script("latin_PP-OCRv5_mobile_rec", "lav"),
            "el" to Script("el_PP-OCRv5_mobile_rec", "ell"),
            "th" to Script("th_PP-OCRv5_mobile_rec", "tha"),
            "ar" to Script("arabic_PP-OCRv5_mobile_rec", "ara"),
        )
    }
}
