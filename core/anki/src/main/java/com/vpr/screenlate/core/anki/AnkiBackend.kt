package com.vpr.screenlate.core.anki

import com.vpr.screenlate.core.anki.settings.DuplicateScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File

/** The part of AnkiDroid that notes are built against; [AnkiDroid] in the app, a fake in tests. */
interface AnkiBackend {
    fun availability(): AnkiAvailability

    suspend fun decks(): List<AnkiDeck>

    suspend fun models(): List<AnkiModel>

    /** Field names of a note type; empty when it does not exist. */
    suspend fun fields(modelId: Long): List<String>

    /** Adds a note; returns its id, or null if AnkiDroid refused it. */
    suspend fun addNote(modelId: Long, deckId: Long, fields: List<String>, tags: Set<String>): Long?

    suspend fun updateNote(noteId: Long, fields: List<String>, tags: Set<String>): Boolean

    /** Notes whose first field equals [firstField], limited to [modelIds] and to [scope] relative to [deckId]. */
    suspend fun findDuplicates(
        firstField: String,
        modelIds: List<Long>,
        scope: DuplicateScope,
        deckId: Long,
    ): List<ExistingNote>

    /** Copies [file] into AnkiDroid's media; returns the markup for it, or null on failure. */
    suspend fun addMedia(file: File, preferredName: String, kind: AnkiDroid.MediaKind): String?
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnkiModule {
    @Binds
    abstract fun backend(ankiDroid: AnkiDroid): AnkiBackend
}
