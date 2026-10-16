package com.vpr.screenlate.core.ocr.model

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OcrEngine
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrPage
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceOcrTest {
    private class FakeMlKit(private val scripts: Set<Language>) : OcrEngine {
        override val type = OcrEngineType.ML_KIT
        override fun reads(language: Language) = language in scripts
        override suspend fun recognize(image: Bitmap, language: Language) = OcrPage(1, 1, emptyList(), type)
    }

    private class FakeModelEngine(override val id: String) : ModelOcrEngine {
        var seen: List<String> = emptyList()

        override suspend fun recognize(image: Bitmap, language: Language, model: InstalledOcrModel, required: List<InstalledOcrModel>): OcrPage {
            seen = listOf(model.id) + required.map { it.id }
            return OcrPage(1, 1, emptyList(), OcrEngineType.DEVICE_MODEL)
        }
    }

    private val paddle = FakeModelEngine("paddle")
    private val tesseract = FakeModelEngine("tesseract")
    private var installed = emptyList<InstalledOcrModel>()
    private var chosen: String? = null
    private val listed = mapOf(
        Language.ENGLISH to listOf(LanguageModel("tesseract-fast-eng", "tesseract"), LanguageModel("paddle-latin", "paddle", listOf("paddle-detector"))),
        Language.RUSSIAN to listOf(
            LanguageModel("paddle-cyrillic", "paddle", listOf("paddle-detector"), recommended = true),
            LanguageModel("tesseract-fast-rus", "tesseract"),
            LanguageModel("onnx-rus", "onnx"),
        ),
    )
    private val device = DeviceOcr(
        FakeMlKit(setOf(Language.JAPANESE, Language.ENGLISH)),
        setOf(paddle, tesseract),
        { installed },
        { listed[it].orEmpty() },
        { chosen },
    )

    /** The engines are fakes and never look at the image; the Android stub cannot be constructed normally. */
    private val image: Bitmap = unsafe().allocateInstance(Bitmap::class.java) as Bitmap

    private fun unsafe(): sun.misc.Unsafe =
        sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe

    private fun install(vararg ids: String) {
        installed = ids.map { InstalledOcrModel(it, "1", it.substringBefore('-'), File(it)) }
    }

    @Test
    fun `ml kit reads its scripts unless a downloaded model is chosen`() = runTest {
        install("tesseract-fast-eng")
        assertThat(device.reads(Language.JAPANESE)).isTrue()
        assertThat(device.recognize(image, Language.JAPANESE).engine).isEqualTo(OcrEngineType.ML_KIT)
        assertThat(device.recognize(image, Language.ENGLISH).engine).isEqualTo(OcrEngineType.ML_KIT)

        chosen = "tesseract-fast-eng"
        assertThat(device.recognize(image, Language.ENGLISH).engine).isEqualTo(OcrEngineType.DEVICE_MODEL)
        assertThat(tesseract.seen).containsExactly("tesseract-fast-eng")
        assertThat(device.active(Language.ENGLISH)?.id).isEqualTo("tesseract-fast-eng")
    }

    @Test
    fun `another script needs a listed model with everything it requires`() = runTest {
        assertThat(device.reads(Language.RUSSIAN)).isFalse()
        assertThrows(NoDeviceOcrException::class.java) { kotlinx.coroutines.runBlocking { device.recognize(image, Language.RUSSIAN) } }

        // Without the detector the recommended model cannot read; a model of an engine the app lacks never can.
        install("paddle-cyrillic", "onnx-rus")
        assertThat(device.reads(Language.RUSSIAN)).isFalse()

        install("paddle-cyrillic", "paddle-detector", "tesseract-fast-rus")
        assertThat(device.reads(Language.RUSSIAN)).isTrue()
        device.recognize(image, Language.RUSSIAN)
        assertThat(paddle.seen).containsExactly("paddle-cyrillic", "paddle-detector").inOrder()

        chosen = "tesseract-fast-rus"
        device.recognize(image, Language.RUSSIAN)
        assertThat(tesseract.seen).containsExactly("tesseract-fast-rus")
    }

    @Test
    fun `the choice falls back to the recommended model, then ml kit, then any usable one`() {
        val fast = LanguageModel("fast", "tesseract")
        val best = LanguageModel("best", "tesseract", recommended = true)
        val models = listOf(fast, best)
        fun select(chosen: String?, mlKit: Boolean, vararg usable: LanguageModel) =
            selectDeviceModel(chosen, models, mlKit) { it in usable }?.id

        assertThat(select("fast", mlKit = true, fast, best)).isEqualTo("fast")
        assertThat(select("mlkit", mlKit = true, fast, best)).isEqualTo("mlkit")
        assertThat(select(null, mlKit = true, fast, best)).isEqualTo("best")
        assertThat(select("gone", mlKit = true, fast)).isEqualTo("mlkit")
        assertThat(select("mlkit", mlKit = false, fast)).isEqualTo("fast")
        assertThat(select(null, mlKit = false)).isNull()
    }
}
