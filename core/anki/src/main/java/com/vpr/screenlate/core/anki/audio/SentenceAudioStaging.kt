package com.vpr.screenlate.core.anki.audio

import android.content.Context
import com.vpr.screenlate.core.anki.AnkiDroid
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Owns process-lifetime admission to sentence export staging and removes orphans before its first new file. */
@Singleton
class SentenceAudioStaging internal constructor(
    private val directory: File,
    private val delete: (File) -> Boolean = { it.delete() },
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(AnkiDroid.mediaDirectory(context))

    private var cleaned = false

    @Synchronized
    fun create(): File {
        synchronized(cleanupLock) {
            runCatching { directory.canonicalPath }.getOrNull()?.let(activeDirectories::add)
            if (!cleaned) {
                sweep(directory, delete)
                cleaned = true
            }
        }
        return File.createTempFile(PREFIX, ".$EXTENSION", directory)
    }

    companion object {
        private const val PREFIX = "screenlate_sentence_"
        private const val EXTENSION = "opus"
        private val cleanupLock = Any()
        private val activeDirectories = ConcurrentHashMap.newKeySet<String>()

        fun cleanupOrphansOnStartup(context: Context) {
            cleanupOrphansOnStartup(AnkiDroid.mediaDirectory(context))
        }

        internal fun cleanupOrphansOnStartup(directory: File) {
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return
            if (!canonical.isDirectory) return
            synchronized(cleanupLock) {
                if (canonical.path in activeDirectories) return
                runCatching { sweep(canonical) { it.delete() } }
            }
        }

        private fun sweep(directory: File, delete: (File) -> Boolean) {
            val files = directory.listFiles() ?: throw IOException("Cannot read sentence audio staging")
            files.filter { it.isFile && it.name.startsWith(PREFIX) && it.extension == EXTENSION }
                .forEach { if (it.exists() && !delete(it)) throw IOException("Cannot clean sentence audio staging") }
        }
    }
}
