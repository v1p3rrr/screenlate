package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.common.Language

/** A short-lived status for one language; closing the view also invalidates pending answers. Main thread only. */
internal class AnkiStatusCache(
    private val ttlMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val language: Language, val status: AnkiStatus, val at: Long)

    private var entry: Entry? = null
    private var revision = 0L

    suspend fun get(language: Language, read: suspend (Language) -> AnkiStatus): AnkiStatus {
        entry?.takeIf { it.language == language && clock() - it.at < ttlMs }?.let { return it.status }
        val request = ++revision
        return read(language).also { status ->
            if (revision == request) entry = Entry(language, status, clock())
        }
    }

    fun clear() {
        entry = null
        revision++
    }
}
