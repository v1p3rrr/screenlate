package com.vpr.screenlate.core.media

import java.io.InputStream

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
    private var currentSourceFrame: Long = edit.selection.start
    private val single = ByteArray(1)

    override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0

        while (true) {
            val range = currentRange ?: return -1

            if (currentSourceFrame < range.start) {
                var bytesToSkip = (range.start - currentSourceFrame) * 2
                while (bytesToSkip > 0L) {
                    val skipped = rawStream.skip(bytesToSkip)
                    if (skipped <= 0L) {
                        val temp = ByteArray(minOf(bytesToSkip, 4096L).toInt())
                        val bytesRead = rawStream.read(temp)
                        if (bytesRead < 0) return -1
                        bytesToSkip -= bytesRead
                    } else {
                        bytesToSkip -= skipped
                    }
                }
                currentSourceFrame = range.start
            }

            val framesRemaining = range.endExclusive - currentSourceFrame
            if (framesRemaining <= 0L) {
                currentRange = if (ranges.hasNext()) ranges.next() else null
                continue
            }

            val maxBytes = minOf(length.toLong(), framesRemaining * 2).toInt()
            val evenLength = if (maxBytes > 1 && maxBytes % 2 != 0) maxBytes - 1 else maxBytes
            val bytesRead = rawStream.read(buffer, offset, evenLength)
            if (bytesRead <= 0) return -1

            currentSourceFrame += bytesRead / 2
            if (currentSourceFrame >= range.endExclusive) {
                currentRange = if (ranges.hasNext()) ranges.next() else null
            }
            return bytesRead
        }
    }

    override fun close() {
        rawStream.close()
    }
}
