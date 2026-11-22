package com.vpr.screenlate.core.anki

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface NotePreparationResult {
    data class Ready(val note: PreparedNote) : NotePreparationResult
    data class Stopped(val result: AddResult) : NotePreparationResult
    data class MediaFailed(val role: NoteMediaRole) : NotePreparationResult
}

enum class NoteMediaRole { SCREENSHOT, PRONUNCIATION, SENTENCE }

/**
 * One pending note's immutable inputs and known successful media copies. Keep input files unchanged
 * until preparation ends. At most one reference per media role is cached; Anki-owned media is never deleted.
 */
class NotePreparation internal constructor(
    internal val owner: AnkiNotes,
    internal val target: NoteTarget,
    internal val request: NoteRequest,
    internal val force: Boolean,
) {
    private val work = Mutex()
    private var submitted = false
    internal var revision = 0L
        private set
    internal val copied = mutableMapOf<NoteMediaRole, Pair<File, String>>()

    suspend fun prepare(sentenceAudio: SentenceAudioAttachment = request.sentenceAudio): NotePreparationResult =
        work.withLock {
            if (submitted) return@withLock NotePreparationResult.Stopped(AddResult.Rejected)
            revision = Math.incrementExact(revision)
            owner.prepare(this, sentenceAudio)
        }

    internal suspend fun claim(note: PreparedNote): Boolean = work.withLock {
        if (submitted || note.revision != revision) return@withLock false
        submitted = true
        true
    }
}

/** Already rendered fields and transferred media; submission is allowed once, even after an uncertain result. */
class PreparedNote internal constructor(
    internal val preparation: NotePreparation,
    internal val names: List<String>,
    internal val fields: List<String>,
    internal val tags: Set<String>,
    internal val updateId: Long?,
) {
    internal val revision = preparation.revision
    internal val owner get() = preparation.owner
    internal val target get() = preparation.target
    internal suspend fun claim() = preparation.claim(this)
}
