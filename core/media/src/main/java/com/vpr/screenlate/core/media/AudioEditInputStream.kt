package com.vpr.screenlate.core.media

import java.io.InputStream
import java.io.EOFException
import java.io.IOException

/**
 * An [InputStream] that streams only the kept frames of an [AudioEdit] from an underlying stream
 * covering [AudioEdit.selection]. Excluded ranges are skipped in the raw stream.
 */
internal class AudioEditInputStream(
    private val rawStream: InputStream,
    private val edit: AudioEdit,
) : InputStream() {

    private val ranges = edit.kept.iterator()
    private var currentRange: AudioRange? = if (ranges.hasNext()) ranges.next() else null
    private var sourceByte = 0L
    private val single = ByteArray(1)
    private val skipBuffer = ByteArray(4096)

    override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0

        while (true) {
            val range = currentRange ?: return -1
            val start = Math.multiplyExact(range.start - edit.selection.start, 2L)
            val end = Math.multiplyExact(range.endExclusive - edit.selection.start, 2L)

            if (sourceByte < start) {
                var bytesToSkip = start - sourceByte
                while (bytesToSkip > 0L) {
                    val skipped = rawStream.skip(bytesToSkip)
                    if (skipped > bytesToSkip || skipped < 0) throw IOException("Invalid PCM skip count")
                    if (skipped <= 0L) {
                        val bytesRead = rawStream.read(skipBuffer, 0, minOf(bytesToSkip, skipBuffer.size.toLong()).toInt())
                        if (bytesRead < 0) throw EOFException("PCM source ended inside an exclusion")
                        if (bytesRead == 0) throw IOException("PCM source made no progress")
                        bytesToSkip -= bytesRead
                    } else {
                        bytesToSkip -= skipped
                    }
                }
                sourceByte = start
            }

            val bytesRemaining = end - sourceByte
            if (bytesRemaining <= 0L) {
                currentRange = if (ranges.hasNext()) ranges.next() else null
                continue
            }

            val maxBytes = minOf(length.toLong(), bytesRemaining).toInt()
            val bytesRead = rawStream.read(buffer, offset, maxBytes)
            if (bytesRead < 0) throw EOFException("PCM source ended inside a kept range")
            if (bytesRead == 0) throw IOException("PCM source made no progress")

            sourceByte += bytesRead
            if (sourceByte >= end) {
                currentRange = if (ranges.hasNext()) ranges.next() else null
            }
            return bytesRead
        }
    }

    override fun close() {
        rawStream.close()
    }
}
