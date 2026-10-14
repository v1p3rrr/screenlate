package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
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

class WordfreqDictionaryTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** A minimal MessagePack writer for the test lists. */
    private class Pack {
        val out = ByteArrayOutputStream()

        fun array(size: Int) = apply {
            if (size < 16) out.write(0x90 or size) else {
                out.write(0xdc)
                out.write(size shr 8)
                out.write(size and 0xff)
            }
        }

        fun map(size: Int) = apply { out.write(0x80 or size) }

        fun string(text: String) = apply {
            val bytes = text.encodeToByteArray()
            if (bytes.size < 32) out.write(0xa0 or bytes.size) else {
                out.write(0xd9)
                out.write(bytes.size)
            }
            out.write(bytes)
        }

        fun int(value: Int) = apply { out.write(value) }
    }

    /** A gzipped cBpack list: the header and [buckets] of words. */
    private fun wordList(buckets: List<List<String>>, format: String = "cB"): ByteArray {
        val pack = Pack().array(buckets.size + 1).map(2).string("format").string(format).string("version").int(1)
        for (bucket in buckets) {
            pack.array(bucket.size)
            bucket.forEach(pack::string)
        }
        return ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write(pack.out.toByteArray()) } }.toByteArray()
    }

    private val buckets = listOf(emptyList(), listOf("the", "of"), emptyList(), listOf("1", "and", "istanbul"), listOf("ß-wort"))

    @Test
    fun `words come bucket by bucket, the most frequent first`() {
        assertThat(WordfreqDictionary.words(wordList(buckets).inputStream(), 100))
            .containsExactly("the", "of", "1", "and", "istanbul", "ß-wort").inOrder()
        assertThat(WordfreqDictionary.words(wordList(buckets).inputStream(), 3)).containsExactly("the", "of", "1").inOrder()
    }

    @Test(expected = IOException::class)
    fun `another file is refused`() {
        WordfreqDictionary.words(wordList(buckets, format = "other").inputStream(), 10)
    }

    @Test
    fun `ranks skip tokens without a letter and give capitalized forms the same rank`() {
        val rows = WordfreqDictionary.termMeta(listOf("the", "1", "the", "and", "Already", "über", "ß"), 4, Locale.ENGLISH)

        assertThat(rows).containsExactly(
            "the" to 1, "The" to 1, "and" to 2, "And" to 2, "Already" to 3, "über" to 4, "Über" to 4,
        ).inOrder()
    }

    @Test
    fun `other spellings take the word's rank unless the list has them as words`() {
        val spellings = mapOf("头发" to listOf("頭髮", "頭發"), "后" to listOf("後"))
        val rows = WordfreqDictionary.termMeta(listOf("头发", "后", "後"), 3, Locale.CHINESE) { spellings[it].orEmpty() }

        assertThat(rows).containsExactly("头发" to 1, "后" to 2, "後" to 3, "頭髮" to 1, "頭發" to 1).inOrder()
    }

    @Test
    fun `chinese words are spelled in traditional characters too`() {
        val chinese = Language.CHINESE.support
        assertThat(WordfreqDictionary.spellings("头发", chinese)).contains("頭髮")
        assertThat(WordfreqDictionary.spellings("学习", chinese)).containsExactly("學習")
        assertThat(WordfreqDictionary.spellings("中文", chinese)).isEmpty()
        assertThat(WordfreqDictionary.spellings("water", Language.ENGLISH.support)).isEmpty()
    }

    @Test
    fun `capitals follow the language`() {
        assertThat(WordfreqDictionary.capitalized("istanbul", Locale.forLanguageTag("tr"))).isEqualTo("İstanbul")
        assertThat(WordfreqDictionary.capitalized("istanbul", Locale.ENGLISH)).isEqualTo("Istanbul")
        assertThat(WordfreqDictionary.capitalized("ßa", Locale.GERMAN)).isEqualTo("SSa")
        assertThat(WordfreqDictionary.capitalized("𝔞b", Locale.ENGLISH)).isNull()
        assertThat(WordfreqDictionary.capitalized("", Locale.ENGLISH)).isNull()
    }

    @Test
    fun `the built archive is a rank-based frequency dictionary that credits wordfreq`() {
        val source = folder.newFile().apply { writeBytes(wordList(buckets)) }
        val output = folder.newFile()

        val ranked = WordfreqDictionary.build(source, "en", "3.2", output, size = 3)

        assertThat(ranked).isEqualTo(3)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("wordfreq en")
            assertThat(index["revision"]!!.jsonPrimitive.content).isEqualTo("wordfreq-3.2-3")
            assertThat(index["frequencyMode"]!!.jsonPrimitive.content).isEqualTo("rank-based")
            assertThat(index["sourceLanguage"]!!.jsonPrimitive.content).isEqualTo("en")
            assertThat(index["attribution"]!!.jsonPrimitive.content).contains("CC BY-SA 4.0")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { Triple(it[0].jsonPrimitive.content, it[1].jsonPrimitive.content, it[2].jsonPrimitive.int) } })
                .containsExactly(
                    Triple("the", "freq", 1), Triple("The", "freq", 1), Triple("of", "freq", 2), Triple("Of", "freq", 2),
                    Triple("and", "freq", 3), Triple("And", "freq", 3),
                ).inOrder()
            assertThat(zip.getEntry("term_meta_bank_2.json")).isNull()
        }
    }

    @Test
    fun `a chinese archive ranks traditional spellings and says so`() {
        val source = folder.newFile().apply { writeBytes(wordList(listOf(listOf("学习", "中文")))) }
        val output = folder.newFile()

        assertThat(WordfreqDictionary.build(source, "zh", "3.2", output, size = 2)).isEqualTo(2)
        ZipFile(output).use { zip ->
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString()).jsonObject
            assertThat(index["description"]!!.jsonPrimitive.content).contains("other script")
            val bank = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(bank.map { row -> row.jsonArray.let { it[0].jsonPrimitive.content to it[2].jsonPrimitive.int } })
                .containsExactly("学习" to 1, "中文" to 2, "學習" to 1).inOrder()
        }
    }

    @Test
    fun `the checksum is compared in any case`() {
        val file = folder.newFile().apply { writeText("wordfreq") }
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

        assertThat(WordfreqDictionary.hasChecksum(file, sha)).isTrue()
        assertThat(WordfreqDictionary.hasChecksum(file, sha.uppercase())).isTrue()
        assertThat(WordfreqDictionary.hasChecksum(file, "0".repeat(64))).isFalse()
    }

    /** wordfreq's own English list, when it was downloaded into testdata (`scripts/frequency/wordfreq_catalog.py`). */
    @Test
    fun `the English list builds the top 100 000 words`() {
        val source = File("../../testdata/wordfreq/large_en.msgpack.gz").takeIf { it.exists() } ?: return
        val output = folder.newFile()

        val ranked = WordfreqDictionary.build(source, "en", "3.2", output)

        assertThat(ranked).isEqualTo(WordfreqDictionary.DEFAULT_SIZE)
        ZipFile(output).use { zip ->
            val first = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("term_meta_bank_1.json")).readBytes().decodeToString()) as JsonArray
            assertThat(first.take(2).map { it.jsonArray[0].jsonPrimitive.content }).containsExactly("the", "The").inOrder()
        }
    }

    /** wordfreq's Chinese list, when it was downloaded into testdata. */
    @Test
    fun `the Chinese list ranks traditional spellings as their simplified words`() {
        val source = File("../../testdata/wordfreq/large_zh.msgpack.gz").takeIf { it.exists() } ?: return
        val output = folder.newFile()

        val ranked = WordfreqDictionary.build(source, "zh", "3.2", output)

        assertThat(ranked).isEqualTo(WordfreqDictionary.DEFAULT_SIZE)
        val ranks = HashMap<String, Int>()
        ZipFile(output).use { zip ->
            zip.entries().asSequence().filter { it.name.startsWith("term_meta_bank_") }.forEach { entry ->
                val bank = Json.parseToJsonElement(zip.getInputStream(entry).readBytes().decodeToString()) as JsonArray
                bank.forEach { row -> ranks[row.jsonArray[0].jsonPrimitive.content] = row.jsonArray[2].jsonPrimitive.int }
            }
        }
        assertThat(ranks["頭髮"]).isEqualTo(ranks["头发"])
        assertThat(ranks["學習"]).isEqualTo(ranks["学习"])
        assertThat(ranks["後"]).isNotNull()
    }
}
