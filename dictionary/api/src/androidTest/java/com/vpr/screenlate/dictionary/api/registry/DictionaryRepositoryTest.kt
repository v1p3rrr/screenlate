package com.vpr.screenlate.dictionary.api.registry

import com.vpr.screenlate.dictionary.api.languages.DictionaryLanguageDetector
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.DictionaryMetadata
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.FrequencyOrder
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DictionaryRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: DictionaryDatabase
    private lateinit var engine: FakeEngine
    private lateinit var storage: DictionaryStorage
    private lateinit var repository: DictionaryRepository
    private lateinit var lookup: DictionaryLookup
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(context.cacheDir, "repository-test-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, DictionaryDatabase::class.java).build()
        engine = FakeEngine()
        storage = DictionaryStorage(context)
        val preferences = PreferenceDataStoreFactory.create { File(root, "prefs.preferences_pb") }
        repository = DictionaryRepository(database.dictionaryDao(), engine, storage, preferences, DictionaryLanguageDetector(context))
        lookup = DictionaryLookup(repository, engine, LookupSettingsRepository(preferences)) { DictionaryImports(context, storage, preferences) }
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

        val prepared = repository.withLookup(Language.JAPANESE) { it }
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
    fun updateTakingTheTitleOfAnotherDictionaryReplacesItToo() = runTest {
        val old = repository.import(archive("Old name", terms = 1))
        val other = repository.import(archive("New name", terms = 1))
        val otherDirectory = storage.directoryOf(other)

        val updated = repository.import(archive("New name", terms = 2), replaces = old.id)

        assertThat(updated.id).isEqualTo(old.id)
        assertThat(repository.getAll().map { it.id to it.title }).containsExactly(old.id to "New name")
        assertThat(otherDirectory.exists()).isFalse()
    }

    @Test
    fun updateOfADeletedDictionaryReplacesTheOneWithItsTitle() = runTest {
        val old = repository.import(archive("Old name", terms = 1))
        val other = repository.import(archive("New name", terms = 1))
        repository.delete(old.id)

        val updated = repository.import(archive("New name", terms = 2), replaces = old.id)

        assertThat(updated.id).isEqualTo(other.id)
        assertThat(repository.getAll().map { it.id to it.termCount }).containsExactly(other.id to 2L)
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
        repository.setSortDictionary(Language.JAPANESE, second.id)
        assertThat(repository.withLookup(Language.JAPANESE) { it.options.frequencyDictionary }).isEqualTo("Second freq")
    }

    @Test
    fun reorderChangesPriorities() = runTest {
        val a = repository.import(archive("A", terms = 1))
        val b = repository.import(archive("B", terms = 1))
        repository.withLookup(Language.JAPANESE) { }
        repository.reorder(listOf(b.id, a.id))
        assertThat(repository.getAll().map { it.title }).containsExactly("B", "A").inOrder()
        assertThat(engine.loaded.terms.map { it.name })
            .containsExactly(repository.getAll()[0].directory, repository.getAll()[1].directory)
            .inOrder()
    }

    @Test
    fun reorderSetsSwitchesInTheSameStep() = runTest {
        val a = repository.import(archive("A", terms = 1))
        val b = repository.import(archive("B", terms = 1))
        repository.withLookup(Language.JAPANESE) { }
        repository.reorder(listOf(b.id, a.id), enabled = mapOf(a.id to false))
        assertThat(repository.getAll().map { it.title to it.enabled }).containsExactly("B" to true, "A" to false).inOrder()
        assertThat(engine.loaded.terms.map { it.name }).containsExactly(repository.getAll()[0].directory)
    }

    @Test
    fun languagesMissingFromTheIndexComeFromTheCatalog() = runTest {
        val catalog = listOf(
            CatalogEntry(
                id = "jmdict-english",
                title = "JMdict",
                installedTitle = "JMdict [",
                kind = DictionaryKind.TERM,
                sourceLanguage = "ja",
                targetLanguage = "en",
                downloadUrl = "https://example.com/JMdict_english.zip",
            ),
        )
        val listed = repository.import(archive("JMdict [2026-09-27]", terms = 1), catalog = catalog)
        val unlisted = repository.import(archive("Other", terms = 1), catalog = catalog)

        assertThat(listed.sourceLanguage).isEqualTo("ja")
        assertThat(listed.targetLanguage).isEqualTo("en")
        assertThat(unlisted.sourceLanguage).isNull()
        assertThat(unlisted.targetLanguage).isNull()
    }

    @Test
    fun anotherRevisionOfTheSameDictionaryReplacesIt() = runTest {
        val old = repository.import(archive("Dict [2026-01-04]", terms = 1))
        repository.import(archive("Freq [2026-01-04]", frequencies = 1))

        val newer = repository.import(archive("Dict [2026-08-11]", terms = 2))

        assertThat(newer.id).isEqualTo(old.id)
        assertThat(repository.getAll().map { it.title }).containsExactly("Dict [2026-08-11]", "Freq [2026-01-04]").inOrder()
        // A frequency dictionary of the same name stays: only the same kind is another revision.
        val frequency = repository.import(archive("Dict [2026-09-01]", frequencies = 1))
        assertThat(frequency.id).isNotEqualTo(newer.id)
        assertThat(repository.getAll()).hasSize(3)
    }

    @Test
    fun languagesAreFilledInOrSetByHand() = runTest {
        val terms = repository.import(archive("Terms", terms = 1))
        val frequency = repository.import(archive("Freq", frequencies = 1))

        repository.fillLanguages(terms.id, source = "ja", target = "en")
        repository.fillLanguages(terms.id, source = "ko", target = "ru")
        repository.fillLanguages(frequency.id, source = "ja", target = "en")
        assertThat(repository.getAll().first { it.id == terms.id }.let { it.sourceLanguage to it.targetLanguage })
            .isEqualTo("ja" to "en")
        // Frequency data explains nothing: only the language of its words.
        assertThat(repository.getAll().first { it.id == frequency.id }.let { it.sourceLanguage to it.targetLanguage })
            .isEqualTo("ja" to null)

        repository.setLanguages(terms.id, source = "ja", target = null)
        assertThat(repository.getAll().first { it.id == terms.id }.targetLanguage).isNull()
    }

    @Test
    fun termDictionariesCountOnlyWhenLookupsInTheLanguageUseThem() = runTest {
        val terms = repository.import(archive("Terms", terms = 1))
        repository.import(archive("Freq", frequencies = 1))
        assertThat(lookup.hasTermDictionaries(Language.JAPANESE)).isTrue()

        repository.setLanguages(terms.id, source = "ko", target = null)
        assertThat(lookup.hasTermDictionaries(Language.JAPANESE)).isFalse()
        assertThat(repository.withLookup(Language.JAPANESE) { it.termDictionaries }).isEmpty()

        repository.setLanguages(terms.id, source = "ja", target = null)
        repository.setEnabled(terms.id, false)
        assertThat(lookup.hasTermDictionaries(Language.JAPANESE)).isFalse()

        repository.setEnabled(terms.id, true)
        storage.directoryOf(repository.getAll().first { it.id == terms.id }).deleteRecursively()
        assertThat(lookup.hasTermDictionaries(Language.JAPANESE)).isFalse()
    }

    @Test
    fun valuesDerivedFromTheDictionariesAreKeptUntilTheyChange() = runTest {
        val terms = repository.import(archive("Terms", terms = 1))
        repository.import(archive("Freq", frequencies = 1, frequencyMode = "rank-based"))

        assertThat(lookup.styles(Language.JAPANESE)).isSameInstanceAs(lookup.styles(Language.JAPANESE))
        assertThat(engine.stylesRead).isEqualTo(1)
        assertThat(lookup.frequencyModes()).containsExactly("Freq", "rank-based")

        repository.setEnabled(terms.id, false)
        lookup.styles(Language.JAPANESE)
        assertThat(engine.stylesRead).isEqualTo(2)

        repository.import(archive("Freq", frequencies = 1, frequencyMode = "occurrence-based"))
        assertThat(lookup.frequencyModes()).containsExactly("Freq", "occurrence-based")
    }

    @Test
    fun anotherLanguagesStylesCannotReplaceDictionariesDuringALookup() = runTest {
        val japanese = repository.import(archive("Japanese", terms = 1))
        repository.setLanguages(japanese.id, "ja", "en")
        val english = repository.import(archive("English", terms = 1))
        repository.setLanguages(english.id, "en", "ru")
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        engine.onLookup = {
            started.complete(Unit)
            resume.await()
            assertThat(engine.loaded.terms.map { it.name }).containsExactly(english.directory)
        }

        val query = async { lookup.lookup("walk", Language.ENGLISH) }
        started.await()
        val styles = async { lookup.styles(Language.JAPANESE) }
        runCurrent()
        assertThat(styles.isCompleted).isFalse()
        assertThat(engine.loaded.terms.map { it.name }).containsExactly(english.directory)
        resume.complete(Unit)
        query.await()
        styles.await()
        assertThat(engine.loaded.terms.map { it.name }).containsExactly(japanese.directory)
    }

    @Test
    fun registryChangesWaitForQueriesAndCancellationReleasesTheLock() = runTest {
        val dictionary = repository.import(archive("Terms", terms = 1))
        val started = CompletableDeferred<Unit>()
        val query = async {
            repository.withLookup(Language.JAPANESE) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        val change = async { repository.setEnabled(dictionary.id, false) }
        runCurrent()
        assertThat(change.isCompleted).isFalse()
        assertThat(engine.loaded.terms).hasSize(1)
        query.cancelAndJoin()
        change.await()
        assertThat(engine.loaded.terms).isEmpty()
        assertThat(lookup.styles(Language.JAPANESE)).isNotNull()
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
        var stylesRead = 0
        var onLookup: suspend () -> Unit = { }

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

        override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> {
            onLookup()
            return emptyList()
        }

        override suspend fun styles(): List<DictionaryStyle> {
            stylesRead++
            return listOf(DictionaryStyle("Terms", ".a { color: red }"))
        }

        override suspend fun media(dictionary: String, path: String): ByteArray? = null

        override suspend fun kanji(character: String): KanjiResult = KanjiResult(character)
    }
}
