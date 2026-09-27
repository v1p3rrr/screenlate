package com.vpr.screenlate.core.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnkiNotesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeAnki : AnkiBackend {
        var availability = AnkiAvailability.READY
        val decks = mutableListOf(AnkiDeck(DECK, "Mining"))
        val models = mutableMapOf(MODEL to listOf("Word", "Meaning", "Sentence", "Picture"))
        val existing = mutableListOf<ExistingNote>()
        val added = mutableListOf<Pair<List<String>, Set<String>>>()
        val updated = mutableListOf<Pair<Long, List<String>>>()
        val media = mutableListOf<Pair<String, AnkiDroid.MediaKind>>()
        var fieldQueries = 0
        var duplicateQuery: Triple<String, List<Long>, DuplicateScope>? = null
        var refuseNotes = false

        override fun availability() = availability

        override suspend fun decks() = decks.toList()

        override suspend fun models() = models.keys.map { AnkiModel(it, "Model $it") }

        override suspend fun fields(modelId: Long): List<String> {
            fieldQueries++
            return models[modelId].orEmpty()
        }

        override suspend fun addNote(modelId: Long, deckId: Long, fields: List<String>, tags: Set<String>): Long? {
            if (refuseNotes) return null
            added += fields to tags
            return 100L + added.size
        }

        override suspend fun updateNote(noteId: Long, fields: List<String>, tags: Set<String>): Boolean {
            updated += noteId to fields
            return true
        }

        override suspend fun findDuplicates(
            firstField: String,
            modelIds: List<Long>,
            scope: DuplicateScope,
            deckId: Long,
        ): List<ExistingNote> {
            duplicateQuery = Triple(firstField, modelIds, scope)
            return existing.filter { it.fields.firstOrNull() == firstField && it.modelId in modelIds }
        }

        override suspend fun addMedia(file: File, preferredName: String, kind: AnkiDroid.MediaKind): String {
            media += preferredName to kind
            return if (kind == AnkiDroid.MediaKind.IMAGE) "<img src=\"${file.name}\">" else "[sound:${file.name}]"
        }
    }

    private val anki = FakeAnki()
    private lateinit var settings: AnkiSettingsRepository
    private lateinit var notes: AnkiNotes

    @Before
    fun setUp() {
        settings = AnkiSettingsRepository(MemoryDataStore())
        notes = AnkiNotes(anki, settings)
    }

    private fun configure(transform: (AnkiSettings) -> AnkiSettings = { it }) = runBlocking {
        settings.update {
            transform(
                it.copy(
                    deckId = DECK,
                    modelId = MODEL,
                    fields = mapOf(
                        "Word" to "{expression}",
                        "Meaning" to "{glossary}",
                        "Sentence" to "{sentence}",
                        "Picture" to "{screenshot}",
                    ),
                    tags = "screenlate, mined",
                ),
            )
        }
    }

    private val request = NoteRequest(mapOf("expression" to "猫", "glossary" to "cat"))

    @Test
    fun `status reports what broke`() = runBlocking<Unit> {
        assertThat(notes.status()).isEqualTo(AnkiStatus.NotConfigured)
        configure()
        assertThat(notes.status()).isEqualTo(AnkiStatus.Ready)

        anki.availability = AnkiAvailability.NO_PERMISSION
        assertThat(notes.status()).isEqualTo(AnkiStatus.Broken(AnkiProblem.NO_PERMISSION))
        anki.availability = AnkiAvailability.NOT_INSTALLED
        assertThat(notes.status()).isEqualTo(AnkiStatus.Broken(AnkiProblem.NOT_INSTALLED))
        anki.availability = AnkiAvailability.READY

        anki.decks.clear()
        assertThat(notes.status()).isEqualTo(AnkiStatus.Ready)
        anki.decks += AnkiDeck(99, "Other")
        assertThat(notes.status()).isEqualTo(AnkiStatus.Broken(AnkiProblem.DECK_MISSING))
        anki.decks += AnkiDeck(DECK, "Mining")

        anki.models[MODEL] = listOf("Word", "Meaning")
        assertThat(notes.status()).isEqualTo(AnkiStatus.Broken(AnkiProblem.FIELDS_CHANGED))
        anki.models.remove(MODEL)
        assertThat(notes.status()).isEqualTo(AnkiStatus.Broken(AnkiProblem.MODEL_MISSING))
        assertThat(notes.ready()).isFalse()
    }

    @Test
    fun `adds a note with rendered fields and tags`() = runBlocking<Unit> {
        assertThat(notes.add(request)).isEqualTo(AddResult.NotConfigured)
        configure()
        assertThat(notes.add(request)).isEqualTo(AddResult.Added(101))
        val (fields, tags) = anki.added.single()
        // Known markers without a value stay empty; the screenshot is only copied when there is one.
        assertThat(fields).containsExactly("猫", "cat", "", "").inOrder()
        assertThat(tags).containsExactly("screenlate", "mined")
        assertThat(anki.media).isEmpty()
    }

    @Test
    fun `media is copied only for used markers`() = runBlocking<Unit> {
        configure()
        val screenshot = folder.newFile("shot.png")
        val audio = AudioClip(folder.newFile("clip.mp3"), "https://example.org/a.mp3", "mp3")
        notes.add(request.copy(screenshot = screenshot, audio = audio))
        assertThat(anki.added.single().first[3]).isEqualTo("<img src=\"shot.png\">")
        assertThat(anki.media.map { it.second }).containsExactly(AnkiDroid.MediaKind.IMAGE)
    }

    @Test
    fun `unavailable anki and refused notes are reported`() = runBlocking<Unit> {
        configure()
        anki.availability = AnkiAvailability.NO_PERMISSION
        assertThat(notes.add(request)).isEqualTo(AddResult.Unavailable(AnkiAvailability.NO_PERMISSION))
        anki.availability = AnkiAvailability.READY
        anki.refuseNotes = true
        assertThat(notes.add(request)).isInstanceOf(AddResult.Failed::class.java)
    }

    @Test
    fun `duplicates are prevented unless forced`() = runBlocking<Unit> {
        configure()
        anki.existing += ExistingNote(7, MODEL, listOf("猫", "old", "", ""))
        assertThat(notes.duplicateIds(request.values)).containsExactly(7L)
        assertThat(notes.add(request)).isEqualTo(AddResult.Duplicate(listOf(7L)))
        assertThat(anki.added).isEmpty()
        assertThat(notes.add(request, force = true)).isEqualTo(AddResult.Added(101))
    }

    @Test
    fun `new behavior adds another note`() = runBlocking<Unit> {
        configure { it.copy(duplicateBehavior = DuplicateBehavior.NEW) }
        anki.existing += ExistingNote(7, MODEL, listOf("猫", "old", "", ""))
        assertThat(notes.add(request)).isEqualTo(AddResult.Added(101))
    }

    @Test
    fun `overwrite merges fields by their modes`() = runBlocking<Unit> {
        configure {
            it.copy(
                duplicateBehavior = DuplicateBehavior.OVERWRITE,
                overwriteModes = mapOf("Meaning" to OverwriteMode.APPEND, "Sentence" to OverwriteMode.OVERWRITE),
            )
        }
        anki.existing += ExistingNote(7, MODEL, listOf("猫", "old ", "a sentence", "<img src=\"x.png\">"))
        assertThat(notes.add(request)).isEqualTo(AddResult.Updated(7))
        // Word and Picture use the default COALESCE: the existing values stay.
        assertThat(anki.updated.single().second).containsExactly("猫", "old cat", "", "<img src=\"x.png\">").inOrder()
    }

    @Test
    fun `duplicate search follows the settings`() = runBlocking<Unit> {
        configure { it.copy(duplicateAllModels = true, duplicateScope = DuplicateScope.DECK_ROOT) }
        anki.models[2L] = listOf("Front")
        notes.duplicateIds(request.values)
        assertThat(anki.duplicateQuery).isEqualTo(Triple("猫", listOf(MODEL, 2L), DuplicateScope.DECK_ROOT))

        anki.duplicateQuery = null
        notes.duplicateIds(mapOf("glossary" to "cat"))
        assertThat(anki.duplicateQuery).isNull()

        configure { it.copy(duplicateCheck = false) }
        notes.duplicateIds(request.values)
        assertThat(anki.duplicateQuery).isNull()
    }

    @Test
    fun `note type fields are cached until invalidated`() = runBlocking<Unit> {
        configure()
        notes.add(request)
        notes.add(request)
        val queries = anki.fieldQueries
        notes.duplicateCheckMarkers()
        assertThat(anki.fieldQueries).isEqualTo(queries)
        notes.invalidate()
        assertThat(notes.duplicateCheckMarkers()).containsExactly("expression")
        assertThat(anki.fieldQueries).isEqualTo(queries + 1)
        assertThat(notes.usedMarkers()).containsExactly("expression", "glossary", "sentence", "screenshot")
    }

    private companion object {
        const val DECK = 1L
        const val MODEL = 1L
    }
}
