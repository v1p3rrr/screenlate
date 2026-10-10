package com.vpr.screenlate.languages

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker
import com.vpr.screenlate.dictionary.api.languages.DictionaryLanguageDetector
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.registry.DictionaryDatabase
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {
    @Test
    fun turningOffBeforeTheFirstImportCancelsItsQueueWithoutAffectingOtherLanguages() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "language-switch-${UUID.randomUUID()}").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = root
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = PreferenceDataStoreFactory.create(scope = scope) { File(root, "prefs.preferences_pb") }
        val database = Room.inMemoryDatabaseBuilder(context, DictionaryDatabase::class.java).build()
        val storage = DictionaryStorage(isolatedContext)
        val repository = DictionaryRepository(database.dictionaryDao(), UnusedEngine(), storage, preferences,
            DictionaryLanguageDetector(context))
        val catalog = DictionaryCatalog(isolatedContext, OkHttpClient())
        val imports = DictionaryImports(context, storage, preferences)
        val profiles = LanguageProfiles(preferences)
        val bundled = BundledDictionaries(context, preferences)
        val files = LanguageFiles(repository, bundled, OcrModelStore(isolatedContext), catalog, profiles, imports)
        val switch = LanguageSwitch(profiles, files, repository, bundled, imports, catalog)
        val manager = WorkManager.getInstance(context)
        val entries = catalog.localCatalog().entries
        fun request(code: String) = OneTimeWorkRequestBuilder<DictionaryImportWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .addTag("dictionary-import")
            .addTag("dictionary-import-name:" + entries.first { it.sourceLanguage == code }.title)
            .build()
        val english = request("en")
        val japanese = request("ja")
        try {
            profiles.turnOn(Language.ENGLISH)
            assertThat(switch.filesSize(Language.ENGLISH)).isNull()
            manager.enqueue(listOf(english, japanese)).await()
            imports.tasks.first { tasks -> tasks.any { it.id == english.id } && tasks.any { it.id == japanese.id } }
            assertThat(repository.getAll()).isEmpty()
            assertThat(switch.filesSize(Language.ENGLISH)).isEqualTo(0L)

            assertThat(switch.turnOff(Language.ENGLISH, deleteFiles = true)).isTrue()
            assertThat(profiles.current().turnedOn).containsExactly(Language.JAPANESE)
            val tasks = imports.tasks.first()
            assertThat(tasks.map { it.id }).doesNotContain(english.id)
            assertThat(tasks.map { it.id }).contains(japanese.id)
            assertThat(switch.filesSize(Language.ENGLISH)).isNull()
        } finally {
            manager.cancelWorkById(english.id).await()
            manager.cancelWorkById(japanese.id).await()
            database.close()
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private class UnusedEngine : DictionaryEngine {
        override suspend fun import(archive: File, outputDir: File): ImportedDictionary = error("No import expected")
        override suspend fun load(dictionaries: DictionarySet) = Unit
        override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> = error("No lookup expected")
        override suspend fun styles(): List<DictionaryStyle> = emptyList()
        override suspend fun media(dictionary: String, path: String): ByteArray? = null
        override suspend fun kanji(character: String): KanjiResult = KanjiResult(character)
    }
}
