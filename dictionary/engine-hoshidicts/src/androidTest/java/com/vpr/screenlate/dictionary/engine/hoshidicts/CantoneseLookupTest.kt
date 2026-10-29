package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.languages.DictionaryLanguageDetector
import com.vpr.screenlate.dictionary.api.registry.DictionaryDatabase
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Cantonese on the real engine: the variant characters of Hong Kong, Taiwan and OpenCC found under one another, words in
 * Traditional characters by default and Jyutping typed into a search, as words.hk writes its headwords and readings.
 */
@RunWith(AndroidJUnit4::class)
class CantoneseLookupTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var database: DictionaryDatabase
    private lateinit var storage: DictionaryStorage
    private lateinit var repository: DictionaryRepository
    private lateinit var settings: LookupSettingsRepository
    private lateinit var lookup: DictionaryLookup

    @Before
    fun setUp() = runTest {
        root = File(context.cacheDir, "cantonese-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, DictionaryDatabase::class.java).build()
        storage = DictionaryStorage(context)
        val engine = HoshidictsEngine(context)
        repository = DictionaryRepository(
            database.dictionaryDao(),
            engine,
            storage,
            PreferenceDataStoreFactory.create { File(root, "registry.preferences_pb") },
            DictionaryLanguageDetector(context),
        )
        settings = LookupSettingsRepository(PreferenceDataStoreFactory.create { File(root, "lookup.preferences_pb") })
        lookup = DictionaryLookup(repository, engine, settings) {
            DictionaryImports(context, storage, PreferenceDataStoreFactory.create { File(root, "imports.preferences_pb") })
        }
        repository.import(termDictionary())
    }

    @After
    fun tearDown() {
        runTest { repository.getAll().forEach { storage.directoryOf(it).deleteRecursively() } }
        database.close()
        root.deleteRecursively()
    }

    private suspend fun lookUp(text: String) = lookup.lookup(text, Language.CANTONESE)

    private suspend fun search(query: String) = lookup.lookupQuery(query, Language.CANTONESE)

    @Test
    fun variantCharactersOfTheTextFindTheDictionarysSpelling() = runTest {
        val health = lookUp("衛生好重要").first()
        assertThat(health.term.expression).isEqualTo("衞生")
        assertThat(health.matched).isEqualTo("衛生")
        assertThat(health.lang).isEqualTo(TRADITIONAL)

        assertThat(lookUp("屋裡").first().term.expression).isEqualTo("屋裏")
        assertThat(lookUp("爲咗").first().term.expression).isEqualTo("為咗")
        // The dictionary's own spelling is found as written.
        assertThat(lookUp("衞生").first().matched).isEqualTo("衞生")
    }

    @Test
    fun wordsWrittenTheSameInBothScriptsAreTaggedTraditional() = runTest {
        val we = lookUp("我哋食飯").first()
        assertThat(we.term.expression).isEqualTo("我哋")
        assertThat(we.lang).isEqualTo(TRADITIONAL)
    }

    @Test
    fun aFixedTraditionalScriptFindsSimplifiedTextInTheDictionarysForms() = runTest {
        settings.setFixedScript(Language.CANTONESE, true)

        val health = lookUp("卫生").first()
        assertThat(health.term.expression).isEqualTo("衞生")
        assertThat(health.matched).isEqualTo("卫生")
    }

    @Test
    fun typedJyutpingFindsTheWordsOfItsReading() = runTest {
        assertThat(search("ngodei").map { it.term.expression }).containsExactly("我哋")
        assertThat(search("ngo5 dei6").map { it.term.expression }).containsExactly("我哋")
        assertThat(search("wai6sang1").map { it.term.expression }).containsExactly("衞生")
    }

    @Test
    fun tonesTypedInTheQueryComeFirst() = runTest {
        assertThat(search("si").map { it.term.expression }).containsAtLeast("詩", "史", "試", "時")
        assertThat(search("si3").first().term.expression).isEqualTo("試")
        assertThat(search("si4").first().term.expression).isEqualTo("時")
    }

    private fun termDictionary(): File {
        val file = File(root, "yue.zip")
        val entries = listOf(
            "index.json" to """{"title":"Cantonese Terms","revision":"1","format":3,"sourceLanguage":"yue","targetLanguage":"en"}""",
            "term_bank_1.json" to """
                [
                  ["衞生","wai6 sang1","","",0,["hygiene"],1,""],
                  ["屋裏","uk1 leoi5","","",0,["at home"],2,""],
                  ["為咗","wai6 zo2","","",0,["for the sake of"],3,""],
                  ["我哋","ngo5 dei6","","",0,["we"],4,""],
                  ["食飯","sik6 faan6","","",0,["to eat"],5,""],
                  ["詩","si1","","",0,["poem"],6,""],
                  ["史","si2","","",0,["history"],7,""],
                  ["試","si3","","",0,["to try"],8,""],
                  ["時","si4","","",0,["time"],9,""]
                ]
            """.trimIndent(),
        )
        ZipOutputStream(file.outputStream()).use { out ->
            for ((path, text) in entries) {
                out.putNextEntry(ZipEntry(path))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private companion object {
        /** Cantonese's Traditional tag (`CantoneseSupport.TRADITIONAL`). */
        const val TRADITIONAL = "yue-HK"
    }
}
