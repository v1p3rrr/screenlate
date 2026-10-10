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

    /** Reads English once its model `en-rec` is installed. */
    private class FakeModelEngine : ModelOcrEngine {
        override val id = "fake"
        var seen: List<InstalledOcrModel> = emptyList()

        override fun reads(language: Language, models: List<InstalledOcrModel>) =
            language == Language.ENGLISH && models.any { it.id == "en-rec" }

        override suspend fun recognize(image: Bitmap, language: Language, models: List<InstalledOcrModel>): OcrPage {
            seen = models
            return OcrPage(1, 1, emptyList(), OcrEngineType.DEVICE_MODEL)
        }
    }

    private val engine = FakeModelEngine()
    private var installed = emptyList<InstalledOcrModel>()
    private val device = DeviceOcr(FakeMlKit(setOf(Language.JAPANESE)), setOf(engine)) { installed }

    /** The engines are fakes and never look at the image; the Android stub cannot be constructed normally. */
    private val image: Bitmap = unsafe().allocateInstance(Bitmap::class.java) as Bitmap

    private fun unsafe(): sun.misc.Unsafe =
        sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe

    private fun model(id: String, engine: String = "fake") = InstalledOcrModel(id, "1", engine, File(id))

    @Test
    fun `ml kit reads the scripts it has a model for`() = runTest {
        installed = listOf(model("en-rec"))
        assertThat(device.reads(Language.JAPANESE)).isTrue()
        assertThat(device.recognize(image, Language.JAPANESE).engine).isEqualTo(OcrEngineType.ML_KIT)
    }

    @Test
    fun `another language needs an engine with its models installed`() = runTest {
        assertThat(device.reads(Language.ENGLISH)).isFalse()
        assertThrows(NoDeviceOcrException::class.java) { kotlinx.coroutines.runBlocking { device.recognize(image, Language.ENGLISH) } }

        installed = listOf(model("det"), model("en-rec"), model("other", engine = "else"))
        assertThat(device.reads(Language.ENGLISH)).isTrue()
        assertThat(device.recognize(image, Language.ENGLISH).engine).isEqualTo(OcrEngineType.DEVICE_MODEL)
        assertThat(engine.seen.map { it.id }).containsExactly("det", "en-rec")
    }
}
