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
 * Chinese on the real engine: words in either script, Yomitan's radical normalization, the script setting and pinyin
 * typed into a search, which needs the reading index written at import.
 */
@RunWith(AndroidJUnit4::class)
class ChineseLookupTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var database: DictionaryDatabase
    private lateinit var storage: DictionaryStorage
    private lateinit var repository: DictionaryRepository
    private lateinit var settings: LookupSettingsRepository
    private lateinit var lookup: DictionaryLookup

    @Before
    fun setUp() = runTest {
        root = File(context.cacheDir, "chinese-${UUID.randomUUID()}").apply { mkdirs() }
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
        settings.setScript(Language.CHINESE, SIMPLIFIED)
    }

    @After
    fun tearDown() {
        runTest { repository.getAll().forEach { storage.directoryOf(it).deleteRecursively() } }
        database.close()
        root.deleteRecursively()
    }

    private suspend fun lookUp(text: String) = lookup.lookup(text, Language.CHINESE)

    private suspend fun search(query: String) = lookup.lookupQuery(query, Language.CHINESE)

    @Test
    fun theLongestWordIsFoundInTheScriptOfTheText() = runTest {
        val simplified = lookUp("图书馆看书").first()
        assertThat(simplified.term.expression).isEqualTo("图书馆")
        assertThat(simplified.matched).isEqualTo("图书馆")
        assertThat(simplified.lang).isEqualTo(SIMPLIFIED)

        val traditional = lookUp("圖書館看書").first()
        assertThat(traditional.term.expression).isEqualTo("圖書館")
        assertThat(traditional.matched).isEqualTo("圖書館")
        assertThat(traditional.lang).isEqualTo(TRADITIONAL)
    }

    @Test
    fun radicalCharactersAreReadAsTheirCharacters() = runTest {
        // KANGXI RADICAL SUN, which looks like 日 and comes out of copied text and some fonts.
        val text = String(Character.toChars(0x2F47)) + "本"
        val first = lookUp(text).first()
        assertThat(first.term.expression).isEqualTo("日本")
        assertThat(first.matched).isEqualTo(text)
    }

    @Test
    fun aFixedScriptShowsEveryWordInIt() = runTest {
        settings.setScript(Language.CHINESE, TRADITIONAL)
        settings.setFixedScript(Language.CHINESE, true)

        val first = lookUp("图书馆").first()
        assertThat(first.term.expression).isEqualTo("圖書館")
        assertThat(first.matched).isEqualTo("图书馆")
    }

    @Test
    fun typedPinyinFindsTheWordsOfItsReadingInTheChosenScript() = runTest {
        assertThat(search("tushuguan").map { it.term.expression }).containsExactly("图书馆")

        settings.setScript(Language.CHINESE, TRADITIONAL)
        assertThat(search("tu shu guan").map { it.term.expression }).containsExactly("圖書館")
    }

    @Test
    fun tonesTypedInTheQueryComeFirst() = runTest {
        assertThat(search("shu").map { it.term.expression }).containsAtLeast("书", "树")
        assertThat(search("shù").first().term.expression).isEqualTo("树")
        assertThat(search("shu1").first().term.expression).isEqualTo("书")
    }

    private fun termDictionary(): File {
        val file = File(root, "zh.zip")
        val entries = listOf(
            "index.json" to """{"title":"Chinese Terms","revision":"1","format":3,"sourceLanguage":"zh","targetLanguage":"en"}""",
            "term_bank_1.json" to """
                [
                  ["图书馆","tú shū guǎn","","",0,["library"],1,""],
                  ["圖書館","tú shū guǎn","","",0,["library"],2,""],
                  ["图书","tú shū","","",0,["books"],3,""],
                  ["圖書","tú shū","","",0,["books"],4,""],
                  ["看书","kàn shū","","",0,["to read"],5,""],
                  ["看書","kàn shū","","",0,["to read"],6,""],
                  ["书","shū","","",0,["book"],7,""],
                  ["書","shū","","",0,["book"],8,""],
                  ["树","shù","","",0,["tree"],9,""],
                  ["樹","shù","","",0,["tree"],10,""],
                  ["日本","Rì běn","","",0,["Japan"],11,""]
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
        /** The script tags of Chinese (`LanguageSupport.scriptTags`). */
        const val SIMPLIFIED = "zh-Hans"
        const val TRADITIONAL = "zh-Hant"
    }
}
