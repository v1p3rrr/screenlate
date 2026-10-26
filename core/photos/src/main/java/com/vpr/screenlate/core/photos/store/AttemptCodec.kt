package com.vpr.screenlate.core.photos.store

import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTilePage
import com.vpr.screenlate.core.photos.FailedRegion
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoTileResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.CRC32

/** Checksummed attempt metadata and terminal tile outcomes; never interpreted as committed lookup text. */
internal object AttemptCodec {
    fun encode(attempt: PhotoAttempt): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x534C5041) // SLPA
            out.writeInt(1)
            out.writeUTF(attempt.id)
            out.writeInt(attempt.basedOn)
            out.writeLong(attempt.startedAt)
            out.writeBoolean(attempt.finished)
            out.writeInt(attempt.tiles.size)
            attempt.tiles.forEach { tile ->
                out.writeInt(tile.left); out.writeInt(tile.top); out.writeInt(tile.right); out.writeInt(tile.bottom)
            }
            val payload = RecognitionCodec.encode(PhotoRecognition(
                attempt.language, attempt.settings,
                OcrPage(attempt.tiles.maxOf { it.right }, attempt.tiles.maxOf { it.bottom }, emptyList(), OcrEngineType.LENS),
                failed = attempt.results.mapNotNull { result -> result.failure?.let { FailedRegion(result.tile.box, it) } },
                recognizedAt = attempt.startedAt,
                tiles = attempt.results.mapNotNull { result -> result.page?.let { PhotoTilePage(result.tile, it) } },
            ))
            out.writeInt(payload.size)
            out.write(payload)
        }
        val checksum = CRC32().apply { update(bytes.toByteArray()) }.value
        DataOutputStream(bytes).use { it.writeLong(checksum) }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): PhotoAttempt? {
        if (bytes.size < 16) return null
        val body = bytes.size - 8
        if (CRC32().apply { update(bytes, 0, body) }.value !=
            DataInputStream(ByteArrayInputStream(bytes, body, 8)).readLong()) return null
        return try {
            val input = DataInputStream(ByteArrayInputStream(bytes, 0, body))
            if (input.readInt() != 0x534C5041 || input.readInt() != 1) return null
            val id = input.readUTF()
            val basedOn = input.readInt()
            val started = input.readLong()
            val finished = input.readBoolean()
            val count = input.readInt()
            require(count in 1..input.available() / 16)
            val tiles = List(count) { PhotoTile(input.readInt(), input.readInt(), input.readInt(), input.readInt()) }
            val length = input.readInt()
            require(length in 0..input.available())
            val payload = ByteArray(length).also { input.readFully(it) }
            require(input.available() == 0)
            val recognition = requireNotNull(RecognitionCodec.decode(payload))
            val results = recognition.tiles.map { PhotoTileResult(it.tile, page = it.page) } + recognition.failed.map {
                PhotoTileResult(PhotoTile(it.box.left.toInt(), it.box.top.toInt(), it.box.right.toInt(), it.box.bottom.toInt()), failure = it.cause)
            }
            PhotoAttempt(id, basedOn, recognition.language, recognition.settings, tiles, results, started, finished)
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    }
}
