package com.vpr.screenlate.core.anki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.api.AddContentApi
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class AnkiAvailability { NOT_INSTALLED, NO_PERMISSION, READY }

data class AnkiDeck(val id: Long, val name: String)

data class AnkiModel(val id: Long, val name: String)

/** A note found by the duplicate check. */
data class ExistingNote(val id: Long, val modelId: Long, val fields: List<String>)

/**
 * Access to AnkiDroid through its content provider API. All calls block on IPC and run on [Dispatchers.IO].
 *
 * Needs the `com.ichi2.anki.permission.READ_WRITE_DATABASE` runtime permission, which only an activity can request.
 */
@Singleton
class AnkiDroid @Inject constructor(@ApplicationContext private val context: Context) {
    private val api by lazy { AddContentApi(context) }

    fun availability(): AnkiAvailability = when {
        AddContentApi.getAnkiDroidPackageName(context) == null -> AnkiAvailability.NOT_INSTALLED
        ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED ->
            AnkiAvailability.NO_PERMISSION
        else -> AnkiAvailability.READY
    }

    suspend fun decks(): List<AnkiDeck> = io {
        api.deckList.orEmpty().map { (id, name) -> AnkiDeck(id, name) }.sortedBy { it.name.lowercase() }
    }

    suspend fun models(): List<AnkiModel> = io {
        api.modelList.orEmpty().map { (id, name) -> AnkiModel(id, name) }.sortedBy { it.name.lowercase() }
    }

    suspend fun fields(modelId: Long): List<String> = io { api.getFieldList(modelId)?.toList().orEmpty() }

    /** Adds a note; returns its id, or null if AnkiDroid refused it. */
    suspend fun addNote(modelId: Long, deckId: Long, fields: List<String>, tags: Set<String>): Long? =
        io { api.addNote(modelId, deckId, fields.toTypedArray(), tags) }

    suspend fun updateNote(noteId: Long, fields: List<String>, tags: Set<String>): Boolean = io {
        api.updateNoteFields(noteId, fields.toTypedArray()) && api.updateNoteTags(noteId, tags)
    }

    /**
     * Notes whose first field equals [firstField] (AnkiDroid compares the field checksum, so HTML must match
     * exactly), limited to [modelIds] and to the decks of [scope] relative to [deckId].
     */
    suspend fun findDuplicates(
        firstField: String,
        modelIds: List<Long>,
        scope: DuplicateScope,
        deckId: Long,
    ): List<ExistingNote> = io {
        val candidates = modelIds.flatMap { modelId ->
            api.findDuplicateNotes(modelId, firstField).orEmpty().map { ExistingNote(it.id, modelId, it.fields.toList()) }
        }
        if (candidates.isEmpty() || scope == DuplicateScope.COLLECTION) return@io candidates
        val decks = scopeDecks(scope, deckId)
        candidates.filter { note -> noteDecks(note.id).any { it in decks } }
    }

    /**
     * Copies [file] into AnkiDroid's media folder. Returns the markup AnkiDroid produced for it
     * (`<img src="…">` or `[sound:…]`), or null on failure.
     */
    suspend fun addMedia(file: File, preferredName: String, kind: MediaKind): String? = io {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        val ankiPackage = AddContentApi.getAnkiDroidPackageName(context) ?: return@io null
        context.grantUriPermission(ankiPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            api.addMediaFromUri(uri, preferredName, kind.mimeType)
        } finally {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Directory for files handed to [addMedia]; shared with AnkiDroid through a FileProvider. */
    fun mediaDirectory(): File = File(context.cacheDir, MEDIA_DIR).apply { mkdirs() }

    private fun scopeDecks(scope: DuplicateScope, deckId: Long): Set<Long> {
        if (scope == DuplicateScope.DECK) return setOf(deckId)
        val decks = api.deckList.orEmpty()
        val root = decks[deckId]?.substringBefore(DECK_SEPARATOR) ?: return setOf(deckId)
        return decks.filterValues { it == root || it.startsWith(root + DECK_SEPARATOR) }.keys
    }

    private fun noteDecks(noteId: Long): Set<Long> {
        val uri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "$noteId/cards")
        return context.contentResolver.query(uri, arrayOf(FlashCardsContract.Card.DECK_ID), null, null, null)
            ?.use { cursor ->
                buildSet {
                    val column = cursor.getColumnIndex(FlashCardsContract.Card.DECK_ID)
                    while (cursor.moveToNext()) add(cursor.getLong(column))
                }
            }
            .orEmpty()
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    enum class MediaKind(val mimeType: String) { IMAGE("image"), AUDIO("audio") }

    companion object {
        const val PERMISSION = AddContentApi.READ_WRITE_PERMISSION
        const val AUTHORITY_SUFFIX = ".anki.media"
        private const val MEDIA_DIR = "anki-media"
        private const val DECK_SEPARATOR = "::"
    }
}
