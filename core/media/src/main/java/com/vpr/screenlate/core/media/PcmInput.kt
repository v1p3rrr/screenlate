package com.vpr.screenlate.core.media

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Fill complete PCM16 frames, carrying odd short reads rather than dropping a half sample. */
internal fun InputStream.readPcm16(buffer: ByteArray, byteCount: Int, checkActive: () -> Unit = {}) {
    require(byteCount in 0..buffer.size && byteCount % 2 == 0)
    var offset = 0
    while (offset < byteCount) {
        checkActive()
        val read = read(buffer, offset, byteCount - offset)
        if (read < 0) throw EOFException("PCM source ended before the expected frame count")
        if (read == 0 || read > byteCount - offset) throw IOException("Invalid PCM read progress")
        offset += read
    }
    checkActive()
}
