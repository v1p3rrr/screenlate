package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.KazakhSupport
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.GZIPOutputStream
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

    /** A gzipped tar archive of [files] (path to text) in the ustar format, as the Leipzig archives are. */
    private fun tarGz(vararg files: Pair<String, String>): ByteArray {
        val tar = ByteArrayOutputStream()
        for ((path, text) in files) {
            val data = text.toByteArray()
            val header = ByteArray(512)
            path.toByteArray().copyInto(header)
            "%011o".format(data.size).toByteArray().copyInto(header, 124)
            header[156] = '0'.code.toByte()
            "ustar".toByteArray().copyInto(header, 257)
            tar.write(header)
            tar.write(data)
            tar.write(ByteArray((512 - data.size % 512) % 512))
        }
        tar.write(ByteArray(1024))
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(tar.toByteArray()) } }.toByteArray()
    }

    private val leipzig = listOf(
        "1\t!\t50", "2\t\"\t40", "100\tкiтап\t30", "101\tжәне\t28", "102\tКітап\t25", "103\t2007\t20",
        "104\tКабар/\t19", "105\tсондай-ақ\t10",
    ).joinToString("\n")

    @Test
    fun `leipzig words are lowercased, kazakh latin i read as і, counts merged, the list found anywhere in the archive`() {
        // A long file before the list: its data spans blocks.
        val archive = tarGz("kaz/kaz-sources.txt" to "x".repeat(1300), "kaz/kaz-words.txt" to leipzig, "kaz/kaz-co_n.txt" to "y")
        val words = CorpusCountsDictionary.leipzigWords(archive.inputStream(), 100, KazakhSupport, Locale.forLanguageTag("kk"))
        assertThat(words).containsExactly("кітап", "және", "сондай-ақ").inOrder()
        assertThat(CorpusCountsDictionary.leipzigWords(archive.inputStream(), 1, KazakhSupport, Locale.ROOT)).containsExactly("кітап")
    }

    @Test(expected = IOException::class)
    fun `an archive without a word list is refused`() {
        CorpusCountsDictionary.leipzigWords(tarGz("kaz/kaz-sentences.txt" to "1\tБұл.").inputStream(), 10, null, Locale.ROOT)
    }

    @Test(expected = IOException::class)
    fun `a word list without counts is refused`() {
        CorpusCountsDictionary.leipzigWords(tarGz("kaz/kaz-words.txt" to "1\tкітап").inputStream(), 10, null, Locale.ROOT)
    }

    @Test
    fun `the kazakh dictionary is built from the leipzig archive and credits the collection`() {
        val source = folder.newFile().apply { writeBytes(tarGz("kaz/kaz-words.txt" to leipzig)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "kk", "2016", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (kk)")
            assertThat(index["author"]!!.jsonPrimitive.content).isEqualTo("Leipzig University")
            assertThat(index["attribution"]!!.jsonPrimitive.content).contains("kaz_newscrawl_2016_100K")
            assertThat(index["attribution"]!!.jsonPrimitive.content).contains("CC BY")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("кітап" to 1, "Кітап" to 1, "және" to 2, "Және" to 2, "сондай-ақ" to 3, "Сондай-ақ" to 3).inOrder()
        }
    }

    @Test
    fun `the latin dictionary credits the wikipedia texts and merges words written with vowel lengths`() {
        val list = listOf("1\tvita\t5", "2\tvīta\t2", "3\tVita\t1", "4\test\t7").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("lat/lat-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "la", "2021", output)).isEqualTo(2)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (la)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Latin Wikipedia of 2021 (lat_wikipedia_2021_100K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("vita" to 1, "Vita" to 1, "est" to 2, "Est" to 2).inOrder()
        }
    }

    @Test
    fun `the esperanto dictionary credits the web texts and keeps the hats`() {
        val list = listOf("1\tŝi\t5", "2\tsi\t2", "3\tĈu\t1").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("epo/epo-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "eo", "2012", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (eo)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Esperanto web texts of 2012 (epo_web_2012_100K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("ŝi" to 1, "Ŝi" to 1, "si" to 2, "Si" to 2, "ĉu" to 3, "Ĉu" to 3).inOrder()
        }
    }

    @Test
    fun `the albanian dictionary credits the news texts and keeps the letters' marks`() {
        val list = listOf("1\ttë\t9", "2\tte\t3", "3\tËshtë\t2").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("sqi/sqi-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "sq", "2020", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (sq)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Albanian news of 2020 (sqi_news_2020_100K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("të" to 1, "Të" to 1, "te" to 2, "Te" to 2, "është" to 3, "Është" to 3).inOrder()
        }
    }

    @Test
    fun `the swahili dictionary credits the wikipedia texts and keeps the apostrophe of ng'`() {
        val list = listOf("1\tna\t9", "2\tya\t5", "3\tNg'ombe\t2").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("swa/swa-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "sw", "2020", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (sw)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Swahili news of 2020 (swa_news_2020_30K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("na" to 1, "Na" to 1, "ya" to 2, "Ya" to 2, "ng'ombe" to 3, "Ng'ombe" to 3).inOrder()
        }
    }

    @Test
    fun `the estonian dictionary credits the news texts and keeps the letters' marks`() {
        val list = listOf("1\tja\t9", "2\tõu\t3", "3\tÖÖ\t2").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("est/est-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "et", "2020", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (et)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Estonian news of 2020 (est_news_2020_100K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("ja" to 1, "Ja" to 1, "õu" to 2, "Õu" to 2, "öö" to 3, "Öö" to 3).inOrder()
        }
    }

    @Test
    fun `the azerbaijani dictionary credits the news texts and capitalizes by the turkish rules`() {
        val list = listOf("1\tvə\t9", "2\tişıq\t3", "3\tİlham\t2").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("aze/aze-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "az", "2020", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (az)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Azerbaijani news of 2020 (aze_news_2020_30K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("və" to 1, "Və" to 1, "işıq" to 2, "İşıq" to 2, "ilham" to 3, "İlham" to 3).inOrder()
        }
    }

    @Test
    fun `the uzbek dictionary credits the news texts and keeps the turned comma of its letters`() {
        val list = listOf("1\tva\t9", "2\tsoʻz\t3", "3\tOʻzbekiston\t2").joinToString("\n")
        val source = folder.newFile().apply { writeBytes(tarGz("uzb/uzb-words.txt" to list)) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "uz", "2020", output)).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (uz)")
            assertThat(index["attribution"]!!.jsonPrimitive.content)
                .startsWith("Word counts of the Uzbek news of 2020 (uzb_news_2020_30K)")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("va" to 1, "Va" to 1, "soʻz" to 2, "Soʻz" to 2, "oʻzbekiston" to 3, "Oʻzbekiston" to 3).inOrder()
        }
    }

    /** The Leipzig archive itself, when it was copied into testdata (`scripts/frequency/corpus_catalog.py`). */
    @Test
    fun `the kazakh archive builds the top 100 000 words`() {
        val source = File("../../testdata/corpus/kaz_newscrawl_2016_100K.tar.gz").takeIf { it.exists() } ?: return
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "kk", "test", output)).isEqualTo(100_000)
        ZipFile(output).use { zip ->
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            val ranks = bank.associate { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } }
            assertThat(ranks["және"]).isLessThan(10)
            assertThat(ranks.keys.none { word -> word.any { it == 'i' } && word.any { it in '\u0400'..'\u04FF' } }).isTrue()
        }
    }

    @Test
    fun `the kyrgyz dictionary credits the kyrgyz corpus`() {
        val source = folder.newFile().apply { writeBytes(tarGz("kir/kir-words.txt" to "1\tжана\t9\n2\tкитеп\t3")) }
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "ky", "2016", output)).isEqualTo(2)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Leipzig Corpora Collection (ky)")
            assertThat(index["attribution"]!!.jsonPrimitive.content).contains("Kyrgyz news crawl of 2016 (kir_newscrawl_2016_100K)")
        }
    }

    /** The Kyrgyz archive itself, when it was copied into testdata (`scripts/frequency/corpus_catalog.py`). */
    @Test
    fun `the kyrgyz archive builds the top 100 000 words`() {
        val source = File("../../testdata/corpus/kir_newscrawl_2016_100K.tar.gz").takeIf { it.exists() } ?: return
        val output = folder.newFile()

        assertThat(CorpusCountsDictionary.build(source, "ky", "test", output)).isEqualTo(100_000)
        ZipFile(output).use { zip ->
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            val ranks = bank.associate { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } }
            assertThat(ranks["жана"]).isLessThan(10)
        }
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
