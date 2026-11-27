package com.vpr.screenlate.core.anki.audio

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Admission and result checks for the single private synthesis file owned by [AudioPlayer]. */
class TtsWorkspace internal constructor(
    private val directory: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val minFreeSpaceBytes: Long = DEFAULT_MIN_FREE_SPACE_BYTES,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) {
    init {
        require(maxFileBytes > 0 && minFreeSpaceBytes >= 0)
    }

    internal fun createFile(): File {
        val required = Math.addExact(maxFileBytes, minFreeSpaceBytes)
        if (usableSpace(directory) < required) throw IOException("Pronunciation workspace is not available")
        return File.createTempFile("tts_", ".wav", directory)
    }

    internal fun isValid(file: File): Boolean {
        val length = file.length()
        return file.isFile && length in 1..maxFileBytes && usableSpace(directory) >= minFreeSpaceBytes
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MIN_FREE_SPACE_BYTES = 52_428_800L
        private val cleanupLock = Any()
        private val activeDirectories = ConcurrentHashMap.newKeySet<String>()

        fun cleanupOrphansOnStartup(context: Context) {
            cleanupOrphansOnStartup(File(context.cacheDir, "tts_playback"))
        }

        internal fun cleanupOrphansOnStartup(directory: File) {
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return
            if (!canonical.isDirectory) return
            synchronized(cleanupLock) {
                if (canonical.path in activeDirectories) return
                runCatching { sweep(canonical) }
            }
        }

        internal fun prepareOwnedDirectory(directory: File) {
            synchronized(cleanupLock) {
                if (!directory.isDirectory && !directory.mkdirs()) error("Cannot create pronunciation directory")
                runCatching { directory.canonicalPath }.getOrNull()?.let(activeDirectories::add)
                sweep(directory)
            }
        }

        private fun sweep(directory: File) {
            val files = checkNotNull(directory.listFiles()) { "Cannot read pronunciation directory" }
            files.filter { it.isFile && it.name.startsWith("tts_") && it.extension == "wav" }
                .forEach { check(!it.exists() || it.delete()) { "Cannot clean abandoned pronunciation" } }
        }
    }
}
