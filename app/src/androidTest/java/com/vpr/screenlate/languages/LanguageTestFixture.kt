package com.vpr.screenlate.languages

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiBackend
import com.vpr.screenlate.core.anki.AnkiDeck
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiModel
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.ExistingNote
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.core.ocr.model.DeviceOcr
import com.vpr.screenlate.core.ocr.model.OcrModelChoices
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import com.vpr.screenlate.overlay.install.LanguageInstallation
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.core.translate.SentenceTranslator
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.DictionaryMetadata
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.languages.DictionaryLanguageDetector
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.Glossary
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.registry.DictionaryDatabase
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.overlay.fonts.PageAppearance
import com.vpr.screenlate.overlay.fonts.PopupFonts
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import com.vpr.screenlate.overlay.translate.SentenceTranslation
import com.vpr.screenlate.search.SearchViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient

/** Isolated settings and registry; every network request and Anki collection write fails the test. */
internal class LanguageTestFixture : AutoCloseable {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    // The system can evict cache during a low-storage run, including live DataStore/registry test files.
    val root = File(target.noBackupFilesDir, "language-regression-${UUID.randomUUID()}").apply { mkdirs() }
    val context = object : ContextWrapper(target) {
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = root
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val preferences = PreferenceDataStoreFactory.create(scope = scope) { File(root, "prefs.preferences_pb") }
    val database = Room.inMemoryDatabaseBuilder(target, DictionaryDatabase::class.java).build()
    private val engine = TestEngine()
    val lookupCalls: Int get() = engine.calls
    val storage = DictionaryStorage(context)
    val repository = DictionaryRepository(database.dictionaryDao(), engine, storage, preferences, DictionaryLanguageDetector(context))
    private val client = OkHttpClient.Builder().addInterceptor { error("No network request expected") }.build()
    val catalog = DictionaryCatalog(context, client)
    val imports = DictionaryImports(target, storage, preferences)
    val profiles = LanguageProfiles(preferences)
    private val bundled = BundledDictionaries(context, preferences)
    val models = OcrModelStore(context)
    val files = LanguageFiles(repository, bundled, models, catalog, profiles, imports)
    val switch = LanguageSwitch(profiles, files, repository, bundled, imports, catalog)
    val ankiSettings = AnkiSettingsRepository(preferences)
    private val viewModels = ViewModelStore()

    suspend fun dictionary(title: String, language: String? = "en", indexUrl: String? = null) =
        repository.import(File(root, "${UUID.randomUUID()}.zip").apply { writeText("test archive") }.also {
            engine.next = DictionaryMetadata(title = title, revision = "1", termCount = 1, sourceLanguage = language, targetLanguage = "en", indexUrl = indexUrl)
        })

    fun search(): SearchViewModel {
        val audioSettings = AudioSettingsRepository(preferences)
        val translationSettings = TranslationSettingsRepository(preferences)
        val lookup = DictionaryLookup(repository, engine, LookupSettingsRepository(preferences)) { imports }
        return SearchViewModel(
            lookup, AnkiDroid(context), AnkiNotes(ReadOnlyAnki(), ankiSettings), AudioFinder(context, client, audioSettings),
            audioSettings, AudioPlayer(context, audioSettings),
            SentenceTranslation(SentenceTranslator(context, client, translationSettings), translationSettings),
            AppSettingsRepository(preferences), PageAppearance(PopupAppearanceRepository(preferences), PopupFonts(context, client)), profiles,
            LanguageInstallation(repository, imports, catalog, bundled, models, DeviceOcr(MlKitOcrEngine(), emptySet(), models, CatalogLanguageModels(catalog), OcrModelChoices(preferences)), OverlaySettingsRepository(preferences)),
        ).also { viewModels.put("search", it) }
    }

    override fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
        database.close()
        scope.cancel()
        root.deleteRecursively()
    }

    private class TestEngine : DictionaryEngine {
        var calls = 0
        lateinit var next: DictionaryMetadata
        override suspend fun import(archive: File, outputDir: File): ImportedDictionary =
            ImportedDictionary(File(outputDir, "test").apply { mkdirs(); File(this, "data").writeText("test") }, next)
        override suspend fun load(dictionaries: DictionarySet) = Unit
        override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> {
            calls++
            return listOf(
            LookupResult("walk", "walk", term = TermEntry("walk", "", glossaries = listOf(Glossary("Test", "[\"walk\"]")))),
            )
        }
        override suspend fun styles(): List<DictionaryStyle> = emptyList()
        override suspend fun media(dictionary: String, path: String): ByteArray? = null
        override suspend fun kanji(character: String): KanjiResult = KanjiResult(character)
    }

    private class ReadOnlyAnki : AnkiBackend {
        override fun availability() = AnkiAvailability.READY
        override suspend fun decks() = listOf(AnkiDeck(1, "Test"))
        override suspend fun models() = listOf(AnkiModel(1, "Test"))
        override suspend fun fields(modelId: Long) = listOf("Front")
        override suspend fun findDuplicates(firstField: String, modelIds: List<Long>, scope: DuplicateScope, deckId: Long) = emptyList<ExistingNote>()
        override suspend fun addNote(modelId: Long, deckId: Long, fields: List<String>, tags: Set<String>): Long? = error("No note write expected")
        override suspend fun updateNote(noteId: Long, fields: List<String>, tags: Set<String>): Boolean = error("No note write expected")
        override suspend fun addMedia(file: File, preferredName: String, kind: AnkiDroid.MediaKind): String? = error("No media write expected")
    }
}
