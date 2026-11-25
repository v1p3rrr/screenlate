package com.vpr.screenlate.core.anki.audio

import java.io.File
import java.io.IOException

/** Admission and result checks for the single private synthesis file owned by [AudioPlayer]. */
internal class TtsWorkspace(
    private val directory: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val minFreeSpaceBytes: Long = DEFAULT_MIN_FREE_SPACE_BYTES,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) {
    init {
        require(maxFileBytes > 0 && minFreeSpaceBytes >= 0)
    }

    fun createFile(): File {
        val required = Math.addExact(maxFileBytes, minFreeSpaceBytes)
        if (usableSpace(directory) < required) throw IOException("Pronunciation workspace is not available")
        return File.createTempFile("tts_", ".wav", directory)
    }

    fun isValid(file: File): Boolean {
        val length = file.length()
        return file.isFile && length in 1..maxFileBytes && usableSpace(directory) >= minFreeSpaceBytes
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MIN_FREE_SPACE_BYTES = 52_428_800L
    }
}
