package com.vpr.screenlate.core.anki.audio

import android.content.Context
import com.vpr.screenlate.core.anki.AnkiDroid
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
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
        if (!cleaned) {
            val files = directory.listFiles() ?: throw IOException("Cannot read sentence audio staging")
            files.filter { it.isFile && it.name.startsWith(PREFIX) && it.extension == EXTENSION }
                .forEach { if (it.exists() && !delete(it)) throw IOException("Cannot clean sentence audio staging") }
            cleaned = true
        }
        return File.createTempFile(PREFIX, ".$EXTENSION", directory)
    }

    private companion object {
        const val PREFIX = "screenlate_sentence_"
        const val EXTENSION = "opus"
    }
}
