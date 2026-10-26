package com.vpr.screenlate.core.photos.store

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.photos.FailedRegion
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.TestPhotos
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import org.junit.Test

class RecognitionCodecTest {

    @Test
    fun wordProvenanceSurvivesStorageAndLayoutReconstruction() {
        val original = TestPhotos.recognition()
        val recognition = original.copy(page = original.page.copy(paragraphs = original.page.paragraphs.map { paragraph ->
            paragraph.copy(lines = paragraph.lines.map { line ->
                line.copy(words = line.words.map { it.copy(engine = OcrEngineType.ML_KIT) })
            })
        }))
        val restored = RecognitionCodec.decode(RecognitionCodec.encode(recognition))!!
        assertThat(restored).isEqualTo(recognition)
        assertThat(TextLayout(restored.page).paragraphs.flatten().all { it.engine == OcrEngineType.ML_KIT }).isTrue()
    }

    @Test
    fun versionOneWithoutWordProvenanceStillReads() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x534C5052)
            out.writeInt(1)
            out.writeString("en")
            out.writeString("CLOUD")
            out.writeBoolean(false)
            out.writeLong(12L)
            out.writeInt(100)
            out.writeInt(200)
            out.writeString("LENS")
            out.writeInt(1) // paragraph
            out.writeBoolean(false)
            out.writeInt(1) // line
            out.writeBoolean(false)
            out.writeBoolean(false)
            repeat(4) { out.writeFloat(it.toFloat()) }
            out.writeInt(1) // word
            out.writeString("word")
            out.writeString(" ")
            repeat(4) { out.writeFloat(it.toFloat()) }
            out.writeInt(-1) // no character boxes; v1 has no engine after this
            out.writeInt(0) // failures
        }
        val restored = RecognitionCodec.decode(withChecksum(bytes.toByteArray()))!!
        assertThat(restored.language).isEqualTo(Language.ENGLISH)
        assertThat(restored.page.paragraphs.single().lines.single().words.single().engine).isNull()
        assertThat(restored.page.text).isEqualTo("word")
    }

    @Test
    fun `a recognition reads back as it was written`() {
        val recognition = TestPhotos.recognition(
            language = Language.ARABIC,
            failed = listOf(FailedRegion(Box(0f, 0f, 1500f, 1500f), FailureCause.OFFLINE)),
        )
        assertThat(RecognitionCodec.decode(RecognitionCodec.encode(recognition))).isEqualTo(recognition)
        val withoutModel = recognition.copy(settings = PhotoOcrSettings(OcrEngines.CLOUD))
        assertThat(RecognitionCodec.decode(RecognitionCodec.encode(withoutModel))).isEqualTo(withoutModel)
    }

    @Test
    fun `the text layout rebuilt from the stored page is the one recognized`() {
        val recognition = TestPhotos.recognition()
        val stored = RecognitionCodec.decode(RecognitionCodec.encode(recognition))!!
        val before = TextLayout(recognition.page)
        val after = TextLayout(stored.page)
        assertThat(after.paragraphs).isEqualTo(before.paragraphs)
        assertThat(after.readingParagraphs).isEqualTo(before.readingParagraphs)
    }

    @Test
    fun `a damaged file reads as nothing`() {
        val bytes = RecognitionCodec.encode(TestPhotos.recognition())
        for (index in listOf(0, 9, bytes.size / 2, bytes.size - 1)) {
            val damaged = bytes.copyOf().also { it[index] = (it[index] + 1).toByte() }
            assertThat(RecognitionCodec.decode(damaged)).isNull()
        }
        assertThat(RecognitionCodec.decode(bytes.copyOf(bytes.size - 20))).isNull()
        assertThat(RecognitionCodec.decode(ByteArray(0))).isNull()
    }

    @Test
    fun `a file of another version reads as nothing`() {
        val bytes = RecognitionCodec.encode(TestPhotos.recognition())
        val newer = bytes.copyOf(bytes.size - 8).also { it[7] = (RecognitionCodec.VERSION + 1).toByte() }
        assertThat(RecognitionCodec.decode(withChecksum(newer))).isNull()
    }

    @Test
    fun `a language or engine unknown to this version reads as nothing, an unknown failure as other`() {
        assertThat(RecognitionCodec.decode(withChecksum(header { writeString("xx") }))).isNull()
        val bytes = RecognitionCodec.encode(
            TestPhotos.recognition(failed = listOf(FailedRegion(Box(1f, 2f, 3f, 4f), FailureCause.SERVER))),
        )
        val renamed = String(bytes, Charsets.ISO_8859_1).replace("SERVER", "FUTURE").toByteArray(Charsets.ISO_8859_1)
        val decoded = RecognitionCodec.decode(withChecksum(renamed.copyOf(renamed.size - 8)))
        assertThat(decoded!!.failed.single().cause).isEqualTo(FailureCause.OTHER)
    }

    private fun header(body: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(0x534C5052)
            it.writeInt(RecognitionCodec.VERSION)
            it.body()
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writeString(text: String) {
        val bytes = text.toByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun withChecksum(body: ByteArray): ByteArray {
        val checksum = CRC32().apply { update(body) }.value
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.write(body)
            it.writeLong(checksum)
        }
        return bytes.toByteArray()
    }
}
