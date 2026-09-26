package com.vpr.screenlate.dictionary.api.registry

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionaryMetadata
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.FrequencyOrder
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: DictionaryDatabase
    private lateinit var engine: FakeEngine
    private lateinit var storage: DictionaryStorage
    private lateinit var repository: DictionaryRepository
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(context.cacheDir, "repository-test-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, DictionaryDatabase::class.java).build()
        engine = FakeEngine()
        storage = DictionaryStorage(context)
        val preferences = PreferenceDataStoreFactory.create { File(root, "prefs.preferences_pb") }
        repository = DictionaryRepository(database.dictionaryDao(), engine, storage, preferences)
    }

    @After
    fun tearDown() {
        runTest { repository.getAll().forEach { storage.directoryOf(it).deleteRecursively() } }
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun importsInPriorityOrderAndLoadsEnabledDictionaries() = runTest {
        val terms = repository.import(archive("Terms [1]", terms = 10))
        val frequency = repository.import(archive("Freq", frequencies = 5, frequencyMode = "occurrence-based"))
        assertThat(terms.priority).isLessThan(frequency.priority)

        val prepared = repository.prepareLookup(Language.JAPANESE)
        assertThat(engine.loaded.terms.map { it.name }).containsExactly(terms.directory)
        assertThat(engine.loaded.frequencies.map { it.name }).containsExactly(frequency.directory)
        assertThat(prepared.options.frequencyDictionary).isEqualTo("Freq")
        assertThat(prepared.options.frequencyOrder).isEqualTo(FrequencyOrder.DESCENDING)
        assertThat(prepared.termDictionaries).containsExactly("Terms [1]")

        repository.setEnabled(terms.id, false)
        assertThat(engine.loaded.terms).isEmpty()
    }

    @Test
    fun updateReplacesTheDictionaryItWasStartedFor() = runTest {
        repository.import(archive("Other", terms = 1))
        val old = repository.import(archive("Dict [2026-01-01]", terms = 1))
        repository.setEnabled(old.id, false)
        val oldDirectory = storage.directoryOf(old)

        val updated = repository.import(archive("Dict [2026-02-01]", terms = 2), replaces = old.id)

        assertThat(updated.id).isEqualTo(old.id)
        assertThat(updated.priority).isEqualTo(old.priority)
        assertThat(updated.enabled).isFalse()
        assertThat(updated.title).isEqualTo("Dict [2026-02-01]")
        assertThat(repository.getAll().map { it.title }).containsExactly("Other", "Dict [2026-02-01]").inOrder()
        assertThat(oldDirectory.exists()).isFalse()
        assertThat(storage.directoryOf(updated).exists()).isTrue()
    }

    @Test
    fun sameTitleReplacesAndDeleteRemovesFiles() = runTest {
        val first = repository.import(archive("Same", terms = 1))
        val second = repository.import(archive("Same", terms = 3))
        assertThat(second.id).isEqualTo(first.id)
        assertThat(repository.getAll()).hasSize(1)

        repository.delete(second.id)
        assertThat(repository.getAll()).isEmpty()
        assertThat(storage.directoryOf(second).exists()).isFalse()
    }

    @Test
    fun chosenSortDictionaryWinsOverOrder() = runTest {
        repository.import(archive("First freq", frequencies = 1))
        val second = repository.import(archive("Second freq", frequencies = 1))
        repository.setSortDictionary(second.id)
        assertThat(repository.prepareLookup(Language.JAPANESE).options.frequencyDictionary).isEqualTo("Second freq")
    }

    @Test
    fun reorderChangesPriorities() = runTest {
        val a = repository.import(archive("A", terms = 1))
        val b = repository.import(archive("B", terms = 1))
        repository.prepareLookup(Language.JAPANESE)
        repository.reorder(listOf(b.id, a.id))
        assertThat(repository.getAll().map { it.title }).containsExactly("B", "A").inOrder()
        assertThat(engine.loaded.terms.map { it.name })
            .containsExactly(repository.getAll()[0].directory, repository.getAll()[1].directory)
            .inOrder()
    }

    private fun archive(
        title: String,
        terms: Long = 0,
        frequencies: Long = 0,
        frequencyMode: String? = null,
    ): File = File(root, "${UUID.randomUUID()}.zip").apply {
        writeText(listOf(title, terms, frequencies, frequencyMode.orEmpty()).joinToString("\n"))
    }

    /** Reads the "archive" written by [archive] and creates a directory named after the title. */
    private class FakeEngine : DictionaryEngine {
        var loaded = DictionarySet()

        override suspend fun import(archive: File, outputDir: File): ImportedDictionary {
            val (title, terms, frequencies, mode) = archive.readText().split("\n")
            val directory = File(outputDir, title).apply { mkdirs() }
            File(directory, "data").writeText(title)
            return ImportedDictionary(
                directory,
                DictionaryMetadata(
                    title = title,
                    revision = "1",
                    termCount = terms.toLong(),
                    frequencyCount = frequencies.toLong(),
                    frequencyMode = mode.ifEmpty { null },
                ),
            )
        }

        override suspend fun load(dictionaries: DictionarySet) {
            loaded = dictionaries
        }

        override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> = emptyList()

        override suspend fun styles(): List<DictionaryStyle> = emptyList()

        override suspend fun media(dictionary: String, path: String): ByteArray? = null

        override suspend fun kanji(character: String): KanjiResult = KanjiResult(character)
    }
}
