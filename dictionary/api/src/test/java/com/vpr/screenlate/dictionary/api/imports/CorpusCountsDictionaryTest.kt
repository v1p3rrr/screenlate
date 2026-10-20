package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CorpusCountsDictionaryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val counts = "ที่\t818364\nการ\t592036\n เป็น\t540030\nห - ม - า\t1200\n1\t900\nๆ\t800\nใน\t537563\n"

    @Test
    fun `words come in the list's order without spelled-out tokens or leading spaces`() {
        assertThat(CorpusCountsDictionary.words(counts.byteInputStream(), 100))
            .containsExactly("ที่", "การ", "เป็น", "1", "ๆ", "ใน").inOrder()
        assertThat(CorpusCountsDictionary.words(counts.byteInputStream(), 2)).containsExactly("ที่", "การ").inOrder()
    }

    @Test(expected = IOException::class)
    fun `a line without a count is refused`() {
        CorpusCountsDictionary.words("ที่\t818364\nการ\n".byteInputStream(), 10)
    }

    @Test(expected = IOException::class)
    fun `a language without a corpus is refused`() {
        CorpusCountsDictionary.build(folder.newFile().apply { writeText(counts) }, "lo", "1", folder.newFile())
    }

    @Test
    fun `the built archive ranks the corpus' words and credits it`() {
        val source = folder.newFile().apply { writeText(counts) }
        val output = folder.newFile()

        val ranked = CorpusCountsDictionary.build(source, "th", "8b2b9a6", output, size = 4)

        assertThat(ranked).isEqualTo(4)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Thai National Corpus (th)")
            assertThat(index["revision"]!!.jsonPrimitive.content).isEqualTo("corpus-8b2b9a6-4")
            assertThat(index["frequencyMode"]!!.jsonPrimitive.content).isEqualTo("rank-based")
            assertThat(index["sourceLanguage"]!!.jsonPrimitive.content).isEqualTo("th")
            assertThat(index["author"]!!.jsonPrimitive.content).isEqualTo("PyThaiNLP")
            assertThat(index["attribution"]!!.jsonPrimitive.content).contains("CC0 1.0")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            // "1" has no letter; ๆ is a letter in Unicode, so it keeps its rank as the corpus counts it.
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("ที่" to 1, "การ" to 2, "เป็น" to 3, "ๆ" to 4).inOrder()
        }
    }

    @Test
    fun `the title names the corpus`() {
        assertThat(CorpusCountsDictionary.title("th")).isEqualTo("Thai National Corpus (th)")
    }

    /** PyThaiNLP's own counts, when they were copied into testdata (`scripts/frequency/corpus_catalog.py`). */
    @Test
    fun `the Thai counts build the top 100 000 words`() {
        val source = File("../../testdata/corpus/tnc_freq.txt").takeIf { it.exists() } ?: return
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "th", "test", output)).isEqualTo(100_000)
        ZipFile(output).use { zip ->
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            val ranks = bank.associate { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } }
            assertThat(ranks["ที่"]).isEqualTo(1)
            assertThat(ranks.keys.none { word -> word.any(Char::isWhitespace) }).isTrue()
        }
    }
}
