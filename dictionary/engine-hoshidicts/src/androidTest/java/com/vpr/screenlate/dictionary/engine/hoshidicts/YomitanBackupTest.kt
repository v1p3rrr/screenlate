package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.imports.YomitanBackup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A Yomitan database export split back into archives must import and look up like the original dictionaries. */
@RunWith(AndroidJUnit4::class)
class YomitanBackupTest {
    private lateinit var root: File
    private val engine = HoshidictsEngine()

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun convertsExportIntoImportableArchives() = runTest {
        val png = Base64.encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2), Base64.NO_WRAP)
        val export = """
            {"formatName":"dexie","formatVersion":1,"data":{"databaseName":"dict","databaseVersion":60,
             "tables":[{"name":"dictionaries","schema":"++id,title,version","rowCount":2}],
             "data":[
              {"tableName":"dictionaries","inbound":true,"rows":[
                {"title":"Backup Terms","revision":"r1","sequenced":true,"version":3,"importDate":1,
                 "prefixWildcardsSupported":false,"counts":{"terms":{"total":2}},"styles":".x{color:red}",
                 "sourceLanguage":"ja","id":1},
                {"title":"Backup Freq","revision":"f1","version":3,"frequencyMode":"rank-based","id":2}]},
              {"tableName":"terms","inbound":true,"rows":[
                {"expression":"食べる","reading":"たべる","expressionReverse":"るべ食","readingReverse":"るべた",
                 "definitionTags":"","rules":"v1","score":0,
                 "glossary":[{"type":"structured-content","content":{"tag":"span","content":"to eat"}}],
                 "sequence":1,"termTags":"","dictionary":"Backup Terms","id":1},
                {"expression":"点","reading":"てん","definitionTags":"n","rules":"","score":5,
                 "glossary":[{"type":"image","path":"img/dot.png"}],"termTags":"","dictionary":"Backup Terms","id":2}]},
              {"tableName":"termMeta","inbound":true,"rows":[
                {"expression":"食べる","mode":"freq","data":120,"dictionary":"Backup Freq","id":1}]},
              {"tableName":"tagMeta","inbound":true,"rows":[
                {"name":"n","category":"partOfSpeech","order":0,"notes":"noun","score":0,"dictionary":"Backup Terms","id":1}]},
              {"tableName":"media","inbound":true,"rows":[
                {"dictionary":"Backup Terms","path":"img/dot.png","mediaType":"image/png","width":1,"height":1,
                 "content":"$png","${'$'}types":{"content":"arraybuffer"},"id":1}]}
             ]}}
        """.trimIndent()

        val archives = YomitanBackup(File(root, "zips").apply { mkdirs() }).convert(export.byteInputStream())
        assertThat(archives).hasSize(2)

        val imported = archives.map { engine.import(it, File(root, "out")) }
        val terms = imported.single { it.metadata.title == "Backup Terms" }
        val freq = imported.single { it.metadata.title == "Backup Freq" }
        assertThat(terms.metadata.termCount).isEqualTo(2)
        assertThat(terms.metadata.mediaCount).isEqualTo(1)
        assertThat(terms.metadata.revision).isEqualTo("r1")
        assertThat(freq.metadata.frequencyCount).isEqualTo(1)

        engine.load(DictionarySet(terms = listOf(terms.directory), frequencies = listOf(freq.directory)))
        val result = engine.lookup("食べなかった", LookupOptions(frequencyDictionary = "Backup Freq")).first()
        assertThat(result.term.expression).isEqualTo("食べる")
        assertThat(result.term.glossaries.single().content.toString()).contains("to eat")
        assertThat(result.term.frequencies.single().values.single().value).isEqualTo(120)
        assertThat(engine.styles().single().css).contains("color:red")
        assertThat(engine.media("Backup Terms", "img/dot.png")).hasLength(6)
    }
}
