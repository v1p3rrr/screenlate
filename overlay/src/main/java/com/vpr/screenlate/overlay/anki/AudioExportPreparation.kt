package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.media.AudioEdit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Serial editor staging: retain one encoded edit through pre-note retries, then transfer or delete it. */
internal class AudioExportPreparation(
    private val encode: suspend (AudioEdit) -> File?,
    private val prepare: suspend (File) -> Boolean,
) {
    private var encoded: Pair<AudioEdit, File>? = null

    suspend fun export(edit: AudioEdit): File? {
        if (encoded?.first != edit) {
            close()
            encode(edit)?.let { encoded = edit to it }
        }
        val file = encoded?.second ?: return null
        currentCoroutineContext().ensureActive()
        val ready = prepare(file)
        currentCoroutineContext().ensureActive()
        return file.takeIf { ready }
    }

    /** Call after editor work has retired. [keep] transfers ownership to the pending note. */
    suspend fun close(keep: File? = null) {
        val file = encoded?.second
        encoded = null
        if (file != null && file != keep) withContext(NonCancellable + Dispatchers.IO) { file.delete() }
    }
}
