package com.vpr.screenlate.dictionary.engine.hoshidicts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.model.Glossary
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class HoshidictsEngineTest {
    private lateinit var root: File
    private val engine = HoshidictsEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "engine-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun importsAndLooksUpDeinflectedTerms() = runTest {
        val terms = engine.import(termDictionary(), File(root, "out"))
        val frequencies = engine.import(frequencyDictionary(), File(root, "out"))
        assertThat(terms.metadata.title).isEqualTo("Test Terms")
        assertThat(terms.metadata.termCount).isEqualTo(3)
        assertThat(terms.metadata.mediaCount).isEqualTo(1)
        assertThat(frequencies.metadata.frequencyCount).isEqualTo(2)
        assertThat(frequencies.metadata.frequencyMode).isEqualTo("rank-based")

        engine.load(DictionarySet(terms = listOf(terms.directory), frequencies = listOf(frequencies.directory)))

        val results = engine.lookup("食べさせられなかった。", LookupOptions(frequencyDictionary = "Test Frequency"))
        val first = results.first()
        assertThat(first.term.expression).isEqualTo("食べる")
        assertThat(first.matched).isEqualTo("食べさせられなかった")
        assertThat(first.trace).isNotEmpty()
        assertThat(Json.parseToJsonElement(first.term.glossaries.single().content).jsonArray.first().jsonPrimitive.content)
            .isEqualTo("to eat")
        assertThat(first.term.frequencies.single().values.single().value).isEqualTo(100)

        // Characters outside the BMP must survive the JNI boundary.
        val scolded = engine.lookup("𠮟られた")
        assertThat(scolded.first().term.expression).isEqualTo("𠮟る")
        assertThat(scolded.first().matched).isEqualTo("𠮟られた")

        assertThat(engine.styles().single().css).contains(".gloss")
        assertThat(engine.media("Test Terms", "img/dot.png")).isEqualTo(PNG_BYTES)
        assertThat(engine.media("Test Terms", "img/missing.png")).isNull()
    }

    @Test
    fun followsFormOfEntriesToTheirLemmas() = runTest {
        val forms = engine.import(formOfDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(forms.directory)))

        // Rows holding only forms give way to the words they name; a word also found by its reading keeps no chain.
        val reading = engine.lookup("じどう")
        assertThat(reading.map { it.term.expression }).containsExactly("自動", "児童")
        assertThat(reading.flatMap { it.trace }).isEmpty()
        assertThat(reading.map { it.term.glossaries.single().texts() }).containsExactly(listOf("automatic"), listOf("child"))
        // The form rows do not take the places of the words.
        assertThat(engine.lookup("じどう", LookupOptions(maxResults = 1)).single().term.expression).isAnyOf("自動", "児童")

        // A word reached only through a form: the form's text matched, the item's tag as the chain.
        val old = engine.lookup("社會").single()
        assertThat(old.term.expression).isEqualTo("社会")
        assertThat(old.matched).isEqualTo("社會")
        assertThat(old.deinflected).isEqualTo("社会")
        assertThat(old.trace.map { it.name }).containsExactly("kyūjitai")
        assertThat(old.term.glossaries.single().texts()).containsExactly("society")

        // A lemma without definitions of its own leaves nothing.
        assertThat(engine.lookup("睦")).isEmpty()

        // The deinflection's chain stays when the form leads to the same word with the same match.
        val negative = engine.lookup("混ぜない").first()
        assertThat(negative.term.expression).isEqualTo("混ぜる")
        assertThat(negative.trace).isNotEmpty()
        assertThat(negative.trace.map { it.name }).doesNotContain("imperfective negative")

        // A row with definitions keeps them without the form item, and the lemma joins the results.
        val mixed = engine.lookup("書")
        assertThat(mixed.single { it.term.expression == "書" }.term.glossaries.single().texts()).containsExactly("writing")
        val lemma = mixed.single { it.term.expression == "書く" }
        assertThat(lemma.matched).isEqualTo("書")
        assertThat(lemma.trace.map { it.name }).containsExactly("stem")
    }

    @Test(expected = DictionaryImportException::class)
    fun rejectsArchiveWithoutIndex() = runTest {
        val archive = zip("broken.zip", "term_bank_1.json" to "[]".toByteArray())
        engine.import(archive, File(root, "out"))
    }

    @Test
    fun decodesIndexTextsButKeepsTheTitleRaw() = runTest {
        val index = """{"title":"Esc\u0061ped","revision":"2026\/09\/30","description":"one\ntwo","format":3}"""
        val archive = zip("escaped.zip", "index.json" to index.toByteArray(), "term_bank_1.json" to TERM_BANK)

        val metadata = engine.import(archive, File(root, "out")).metadata

        assertThat(metadata.title).isEqualTo("Esc\\u0061ped")
        assertThat(metadata.revision).isEqualTo("2026/09/30")
        assertThat(metadata.description).isEqualTo("one\ntwo")
    }

    @Test
    fun refusesATitleThatLeavesTheOutputDirectory() = runTest {
        // The importer would write into out/../kept and delete it when the empty dictionary fails.
        val kept = File(root, "kept").apply { mkdirs() }
        File(kept, "file").writeText("data")
        val archive = zip("escape.zip", "index.json" to """{"title":"../kept","revision":"1","format":3}""".toByteArray())

        val error = runCatching { engine.import(archive, File(root, "out")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DictionaryImportException::class.java)
        assertThat(File(kept, "file").readText()).isEqualTo("data")
    }

    private fun termDictionary(): File = zip(
        "terms.zip",
        "index.json" to """{"title":"Test Terms","revision":"1","format":3,"sourceLanguage":"ja"}""".toByteArray(),
        "styles.css" to ".gloss { color: red; }".toByteArray(),
        "term_bank_1.json" to """
            [
              ["食べる","たべる","","v1",0,["to eat"],1,""],
              ["𠮟る","しかる","","v5",0,["to scold"],2,""],
              ["点","てん","","",0,[{"type":"image","path":"img/dot.png"}],3,""]
            ]
        """.trimIndent().toByteArray(),
        "img/dot.png" to PNG_BYTES,
    )

    // Rows in the shape of Wiktionary's "non-lemma" rows.
    private fun formOfDictionary(): File = zip(
        "forms.zip",
        "index.json" to """{"title":"Test Forms","revision":"1","format":3,"sourceLanguage":"ja"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["自動","じどう","","n",0,["automatic"],1,""],
              ["児童","じどう","","n",0,["child"],2,""],
              ["じどう","","non-lemma","",0,[["自動",["transliteration"]]],3,""],
              ["じどう","","non-lemma","",0,[["児童",["transliteration"]]],4,""],
              ["社会","しゃかい","","n",0,["society"],5,""],
              ["社會","","non-lemma","",0,[["社会",["kyūjitai"]]],6,""],
              ["睦","","non-lemma","",0,[["睦月",["transliteration"]]],7,""],
              ["混ぜる","まぜる","","v1",0,["to mix"],8,""],
              ["混ぜない","","non-lemma","v",0,[["混ぜる",["imperfective negative"]]],9,""],
              ["書","しょ","","n",0,["writing",["書く",["stem"]]],10,""],
              ["書く","かく","","v5",0,["to write"],11,""]
            ]
        """.trimIndent().toByteArray(),
    )

    private fun frequencyDictionary(): File = zip(
        "freq.zip",
        "index.json" to """{"title":"Test Frequency","revision":"1","format":3,"frequencyMode":"rank-based"}"""
            .toByteArray(),
        "term_meta_bank_1.json" to """[["食べる","freq",100],["点","freq",200]]""".toByteArray(),
    )

    private fun Glossary.texts(): List<String> = Json.parseToJsonElement(content).jsonArray.map { it.jsonPrimitive.content }

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((path, bytes) in entries) {
                out.putNextEntry(ZipEntry(path))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private companion object {
        val TERM_BANK = """[["食べる","たべる","","v1",0,["to eat"],1,""]]""".toByteArray()
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    }
}
