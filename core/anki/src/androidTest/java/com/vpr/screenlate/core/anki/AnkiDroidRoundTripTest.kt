package com.vpr.screenlate.core.anki

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.ichi2.anki.api.AddContentApi
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adds notes to a real AnkiDroid through its API. Runs only where AnkiDroid is installed, and only against a local
 * test collection: it creates its own note type and deck ("Screenlate Test") and adds a note with a unique word
 * per run. Never run it on a device whose AnkiDroid syncs with a real account.
 */
@RunWith(AndroidJUnit4::class)
class AnkiDroidRoundTripTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var settings: AnkiSettingsRepository
    private lateinit var notes: AnkiNotes
    private val word = "テスト${UUID.randomUUID().toString().take(8)}"

    @Before
    fun setUp() {
        assumeTrue("AnkiDroid is not installed", AddContentApi.getAnkiDroidPackageName(context) != null)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, AnkiDroid.PERMISSION)
        root = File(context.cacheDir, "anki-${UUID.randomUUID()}").apply { mkdirs() }
        settings = AnkiSettingsRepository(PreferenceDataStoreFactory.create { File(root, "anki.preferences_pb") })
        notes = AnkiNotes(AnkiDroid(context), settings)

        // A freshly installed AnkiDroid (as on CI) may not have opened its collection yet.
        val api = AddContentApi(context)
        val modelId = runCatching {
            api.modelList.orEmpty().entries.firstOrNull { it.value == NAME }?.key
                ?: api.addNewCustomModel(NAME, arrayOf("Word", "Meaning"), arrayOf("Card 1"), arrayOf("{{Word}}"), arrayOf("{{Meaning}}"), null, null, null)
        }.getOrNull()
        val deckId = runCatching { api.deckList.orEmpty().entries.firstOrNull { it.value == NAME }?.key ?: api.addNewDeck(NAME) }
            .getOrNull()
        assumeTrue("AnkiDroid has no collection yet", modelId != null && deckId != null)
        runBlocking {
            settings.update {
                it.copy(
                    deckId = deckId!!,
                    deckName = NAME,
                    modelId = modelId!!,
                    modelName = NAME,
                    fields = mapOf("Word" to "{expression}", "Meaning" to "{glossary}"),
                    tags = "screenlate-test",
                    duplicateScope = DuplicateScope.DECK,
                    duplicateBehavior = DuplicateBehavior.PREVENT,
                )
            }
        }
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun addsFindsAndUpdatesNotes() = runBlocking<Unit> {
        assertThat(notes.status()).isEqualTo(AnkiStatus.Ready)
        val values = mapOf("expression" to word, "glossary" to "first")

        val added = notes.add(NoteRequest(values)) as AddResult.Added
        assertThat(notes.duplicateIds(values)).containsExactly(added.noteId)
        assertThat(notes.add(NoteRequest(values))).isEqualTo(AddResult.Duplicate(listOf(added.noteId)))

        settings.update { it.copy(duplicateBehavior = DuplicateBehavior.OVERWRITE) }
        val updated = notes.add(NoteRequest(values + ("glossary" to "second")))
        assertThat(updated).isEqualTo(AddResult.Updated(added.noteId))

        val forced = notes.add(NoteRequest(values), force = true) as AddResult.Added
        assertThat(forced.noteId).isNotEqualTo(added.noteId)
        assertThat(notes.duplicateIds(values)).containsExactly(added.noteId, forced.noteId)
    }

    @Test
    fun reportsAMissingNoteType() = runBlocking<Unit> {
        settings.update { it.copy(modelId = Long.MAX_VALUE - 1) }
        assertThat(notes.status()).isEqualTo(AnkiStatus.Broken(AnkiProblem.MODEL_MISSING))
    }

    private companion object {
        const val NAME = "Screenlate Test"
    }
}
