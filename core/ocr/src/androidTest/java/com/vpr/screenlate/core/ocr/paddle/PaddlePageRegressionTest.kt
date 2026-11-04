package com.vpr.screenlate.core.ocr.paddle

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import com.vpr.screenlate.core.ocr.mnn.MnnNetwork
import com.vpr.screenlate.core.ocr.mnn.MnnTensor
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in replay with local generated pages and cached v5 models; never captures or sends the device's screen. */
@RunWith(AndroidJUnit4::class)
class PaddlePageRegressionTest {
    @Test
    fun russianLightAndDarkPagesKeepTheirPrintedRowsAndParagraphs() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("paddleRegression") == "run")
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "paddle-regression")
        Network(File(directory, "detector.mnn")).use { detector ->
            Network(File(directory, "recognizer.mnn")).use { recognizer ->
                val dictionary = PaddlePipeline.dictionary(File(directory, "dict.txt").readLines())
                val pipeline = PaddlePipeline(detector, recognizer, dictionary)
                for (name in listOf("ru-page-light.png", "ru-page-dark.png")) {
                    val image = checkNotNull(BitmapFactory.decodeFile(File(directory, name).path))
                    try {
                        val page = pipeline.recognize(image)
                        val paragraphs = page.paragraphs.map { paragraph ->
                            paragraph.lines.filter { it.box.centerY in 180f..1700f }
                        }.filter { it.isNotEmpty() }
                        val bounds = paragraphs.map { rows -> rows.map { listOf(it.box.left, it.box.top, it.box.right, it.box.bottom) } }
                        val sizes = paragraphs.map { rows -> rows.map { row ->
                            row.words.flatMap { it.characterBoxes.orEmpty() }.map { it.height }.sorted().let { it.getOrNull(it.size / 2) }
                        } }
                        assertWithMessage("$name rows per paragraph; bounds=$bounds; heights=$sizes").that(paragraphs.map { it.size })
                            .containsExactly(3, 3, 4, 3, 3, 2).inOrder()
                        val text = paragraphs.flatten().joinToString("") { it.text }
                        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
                            .filter(Char::isLetterOrDigit)
                        assertWithMessage("$name character count").that(normalized.length).isEqualTo(537)
                        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
                            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        // Only a digest is reported on failure, never recognized text.
                        assertWithMessage("$name normalized text digest").that(digest)
                            .isEqualTo("6a10f58deee3eb957880f99248333a84ba7879c3a3c69415ea5c9e9943d58c63")
                    } finally {
                        image.recycle()
                    }
                }
            }
        }
    }

    private class Network(file: File) : PaddleModel {
        private val network = MnnNetwork(file.path, 4)
        override fun run(input: Tensor): Tensor = network.run(MnnTensor(input.data, input.shape)).let { Tensor(it.data, it.shape) }
        override fun close() = network.close()
    }
}
