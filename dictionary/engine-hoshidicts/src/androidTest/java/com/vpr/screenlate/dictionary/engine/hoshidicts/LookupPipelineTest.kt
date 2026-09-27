package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryLookup
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

/** The whole lookup path on the real engine: spelling variants, romaji, single kanji entries and the result limit. */
@RunWith(AndroidJUnit4::class)
class LookupPipelineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var database: DictionaryDatabase
    private lateinit var storage: DictionaryStorage
    private lateinit var repository: DictionaryRepository
    private lateinit var settings: LookupSettingsRepository
    private lateinit var lookup: DictionaryLookup

    @Before
    fun setUp() = runTest {
        root = File(context.cacheDir, "pipeline-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, DictionaryDatabase::class.java).build()
        storage = DictionaryStorage(context)
        val engine = HoshidictsEngine()
        repository = DictionaryRepository(
            database.dictionaryDao(),
            engine,
            storage,
            PreferenceDataStoreFactory.create { File(root, "registry.preferences_pb") },
        )
        settings = LookupSettingsRepository(PreferenceDataStoreFactory.create { File(root, "lookup.preferences_pb") })
        lookup = DictionaryLookup(repository, engine, settings)
        repository.import(termDictionary())
    }

    @After
    fun tearDown() {
        runTest { repository.getAll().forEach { storage.directoryOf(it).deleteRecursively() } }
        database.close()
        root.deleteRecursively()
    }

    private suspend fun lookUp(text: String) = lookup.lookup(text, Language.JAPANESE)

    @Test
    fun digitsAreLookedUpAsKanjiNumerals() = runTest {
        val first = lookUp("1人で行く").first()
        assertThat(first.term.expression).isEqualTo("一人")
        // The match is reported in the screen's characters, so the highlight covers "1人".
        assertThat(first.matched).isEqualTo("1人")
        assertThat(lookUp("１人").first().term.expression).isEqualTo("一人")
    }

    @Test
    fun middleDotsAndSpacesAreSkipped() = runTest {
        val first = lookUp("ス・マホで").first()
        assertThat(first.term.expression).isEqualTo("スマホ")
        assertThat(first.matched).isEqualTo("ス・マホ")
    }

    @Test
    fun romajiNeedsTheSetting() = runTest {
        assertThat(lookUp("taberu")).isEmpty()
        settings.setRomaji(true)
        val first = lookUp("taberu").first()
        assertThat(first.term.expression).isEqualTo("食べる")
        assertThat(first.matched).isEqualTo("taberu")
    }

    @Test
    fun kanjiOfTheWordFollowTheResults() = runTest {
        // 面 is a shorter match of the text itself; 影 is only reachable as a kanji of the word.
        val results = lookUp("面影が")
        assertThat(results.map { it.term.expression }).containsExactly("面影", "面", "影").inOrder()
        settings.setSingleKanji(false)
        assertThat(lookUp("面影が").map { it.term.expression }).containsExactly("面影", "面").inOrder()
    }

    @Test
    fun theLimitAppliesBeforeTheKanjiEntries() = runTest {
        settings.setMaxResults(1)
        val results = lookUp("面影が")
        assertThat(results.map { it.term.expression }).containsExactly("面影", "影").inOrder()
        settings.setSingleKanji(false)
        assertThat(lookUp("食べる").map { it.term.expression }).hasSize(1)
    }

    @Test
    fun latinTextAloneIsNotLookedUp() = runTest {
        assertThat(lookUp("Tokyo")).isEmpty()
        assertThat(lookUp("  ")).isEmpty()
    }

    private fun termDictionary(): File {
        val file = File(root, "terms.zip")
        val entries = listOf(
            "index.json" to """{"title":"Pipeline Terms","revision":"1","format":3,"sourceLanguage":"ja"}""",
            "term_bank_1.json" to """
                [
                  ["一人","ひとり","","",0,["one person"],1,""],
                  ["人","ひと","","",0,["person"],2,""],
                  ["食べる","たべる","","v1",0,["to eat"],3,""],
                  ["食べ物","たべもの","","",0,["food"],4,""],
                  ["スマホ","","","",0,["smartphone"],5,""],
                  ["面影","おもかげ","","",0,["trace"],6,""],
                  ["影","かげ","","",0,["shadow"],7,""],
                  ["面","めん","","",0,["face"],8,""]
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
}
