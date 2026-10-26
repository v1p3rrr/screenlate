package com.vpr.screenlate.core.photos.store

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.photos.FailedRegion
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoRecognition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.CRC32

/**
 * The stored form of a [PhotoRecognition]: everything the page holds (separators, character boxes, directions,
 * paragraph engines), so the text layout is rebuilt exactly. Binary, versioned and checksummed; [decode] returns null
 * for a damaged file or one of another version, and the photo is then kept without its text.
 */
internal object RecognitionCodec {
    private const val MAGIC = 0x534C5052 // "SLPR"
    const val VERSION = 2

    fun encode(recognition: PhotoRecognition): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeRecognition(recognition)
        }
        val checksum = CRC32().apply { update(bytes.toByteArray()) }.value
        DataOutputStream(bytes).use { it.writeLong(checksum) }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): PhotoRecognition? {
        if (bytes.size < HEADER_BYTES + CHECKSUM_BYTES) return null
        val body = bytes.size - CHECKSUM_BYTES
        val checksum = CRC32().apply { update(bytes, 0, body) }.value
        val stored = DataInputStream(ByteArrayInputStream(bytes, body, CHECKSUM_BYTES)).readLong()
        if (checksum != stored) return null
        return try {
            val input = DataInputStream(ByteArrayInputStream(bytes, 0, body))
            if (input.readInt() != MAGIC) return null
            val version = input.readInt()
            if (version !in 1..VERSION) return null
            input.readRecognition(version).takeIf { input.available() == 0 }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun DataOutputStream.writeRecognition(recognition: PhotoRecognition) {
        writeString(recognition.language.code)
        writeString(recognition.settings.engines.name)
        writeOptionalString(recognition.settings.deviceModel)
        writeLong(recognition.recognizedAt)
        val page = recognition.page
        writeInt(page.width)
        writeInt(page.height)
        writeString(page.engine.name)
        writeInt(page.paragraphs.size)
        page.paragraphs.forEach { paragraph ->
            writeOptionalString(paragraph.engine?.name)
            writeInt(paragraph.lines.size)
            paragraph.lines.forEach { line ->
                writeBoolean(line.vertical)
                writeBoolean(line.rightToLeft)
                writeBox(line.box)
                writeInt(line.words.size)
                line.words.forEach { word ->
                    writeString(word.text)
                    writeString(word.separator)
                    writeBox(word.box)
                    val characters = word.characterBoxes
                    writeInt(characters?.size ?: -1)
                    characters?.forEach { writeBox(it) }
                    writeOptionalString(word.engine?.name)
                }
            }
        }
        writeInt(recognition.failed.size)
        recognition.failed.forEach { region ->
            writeBox(region.box)
            writeString(region.cause.name)
        }
    }

    private fun DataInputStream.readRecognition(version: Int): PhotoRecognition {
        val language = requireNotNull(Language.of(readString())) { "Unknown language" }
        val engines = OcrEngines.valueOf(readString())
        val deviceModel = readOptionalString()
        val recognizedAt = readLong()
        val width = readInt()
        val height = readInt()
        val engine = OcrEngineType.valueOf(readString())
        val paragraphs = List(readCount()) {
            val paragraphEngine = readOptionalString()?.let(OcrEngineType::valueOf)
            val lines = List(readCount()) {
                val vertical = readBoolean()
                val rightToLeft = readBoolean()
                val box = readBox()
                val words = List(readCount()) {
                    val text = readString()
                    val separator = readString()
                    val box = readBox()
                    val characters = readInt().takeIf { it >= 0 }?.let { count -> List(checkCount(count)) { readBox() } }
                    val wordEngine = if (version >= 2) readOptionalString()?.let(OcrEngineType::valueOf) else null
                    OcrWord(text, separator, box, characters, wordEngine)
                }
                OcrLine(words, box, vertical, rightToLeft)
            }
            OcrParagraph(lines, paragraphEngine)
        }
        val failed = List(readCount()) {
            val box = readBox()
            val cause = readString().let { name -> FailureCause.entries.firstOrNull { it.name == name } ?: FailureCause.OTHER }
            FailedRegion(box, cause)
        }
        return PhotoRecognition(
            language = language,
            settings = PhotoOcrSettings(engines, deviceModel),
            page = OcrPage(width, height, paragraphs, engine),
            failed = failed,
            recognizedAt = recognizedAt,
        )
    }

    private fun DataOutputStream.writeString(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeOptionalString(text: String?) {
        writeBoolean(text != null)
        if (text != null) writeString(text)
    }

    private fun DataOutputStream.writeBox(box: Box) {
        writeFloat(box.left)
        writeFloat(box.top)
        writeFloat(box.right)
        writeFloat(box.bottom)
    }

    private fun DataInputStream.readString(): String {
        val bytes = ByteArray(checkCount(readInt()))
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataInputStream.readOptionalString(): String? = if (readBoolean()) readString() else null

    private fun DataInputStream.readBox(): Box = Box(readFloat(), readFloat(), readFloat(), readFloat())

    private fun DataInputStream.readCount(): Int = checkCount(readInt())

    /** A count larger than the bytes left is damage that the checksum missed; it must not allocate a huge list. */
    private fun DataInputStream.checkCount(count: Int): Int {
        require(count in 0..available()) { "Bad count" }
        return count
    }

    private const val HEADER_BYTES = 8
    private const val CHECKSUM_BYTES = 8
}
