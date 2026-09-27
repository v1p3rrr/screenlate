package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test

class YomitanBackupJvmTest {
    private val directory: File = Files.createTempDirectory("backup").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** The layout of a real export: summary rows and several tables keep their key outside the row. */
    private val export = """
        {"formatName":"dexie","formatVersion":1,"data":{"databaseName":"dict","databaseVersion":6,
         "tables":[{"name":"dictionaries","schema":"++,title,version","rowCount":2}],
         "data":[
          {"tableName":"dictionaries","inbound":false,"rows":[
            {"$":[1,{"title":"Terms","revision":"r1","sequenced":true,"version":3,"importDate":1,
              "counts":{"terms":{"total":2},"termMeta":{"total":0},"media":{"total":1}},"styles":".x{color:red}",
              "sourceLanguage":"ja","indexUrl":"https://example.com/index.json"}],"${'$'}types":{"$":{"":"arrayNonindexKeys"}}},
            {"$":[2,{"title":"Freq","revision":"f1","version":3,"frequencyMode":"rank-based",
              "counts":{"termMeta":{"total":1}}}]}]},
          {"tableName":"kanji","inbound":false,"rows":[
            {"$":[1,{"character":"亜","onyomi":"ア","kunyomi":"つ.ぐ","tags":"jouyou","meanings":["Asia"],"stats":{"strokes":"7"},"dictionary":"Terms"}]}]},
          {"tableName":"kanjiMeta","inbound":false,"rows":[]},
          {"tableName":"media","inbound":true,"rows":[
            {"dictionary":"Terms","path":"img/dot.png","mediaType":"image/png","content":"${Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))}","id":1}]},
          {"tableName":"tagMeta","inbound":false,"rows":[
            {"$":[1,{"name":"n","category":"partOfSpeech","order":0,"notes":"noun","score":0,"dictionary":"Terms"}]}]},
          {"tableName":"termMeta","inbound":false,"rows":[
            {"$":[1,{"expression":"食べる","mode":"freq","data":{"value":120,"displayValue":"120"},"dictionary":"Freq"}]}]},
          {"tableName":"terms","inbound":true,"rows":[
            {"expression":"食べる","reading":"たべる","definitionTags":"","rules":"v1","score":0,
             "glossary":[{"type":"structured-content","content":{"tag":"span","content":"to \"eat\""}}],
             "sequence":1,"termTags":"","dictionary":"Terms","id":1,"${'$'}types":{"glossary":"arrayNonindexKeys"}},
            {"expression":"点","reading":"てん","definitionTags":"n","rules":"","score":5,
             "glossary":[{"type":"image","path":"img/dot.png"}],"termTags":"","dictionary":"Terms","id":2}]}
         ]}}
    """.trimIndent()

    private fun entries(zip: File): Map<String, String> = ZipFile(zip).use { file ->
        file.entries().toList().associate { it.name to file.getInputStream(it).readBytes().decodeToString() }
    }

    @Test
    fun `lists the dictionaries from the start of the file`() {
        val dictionaries = YomitanBackup.scan(export.byteInputStream())
        assertThat(dictionaries.map { it.title }).containsExactly("Terms", "Freq").inOrder()
        assertThat(dictionaries[0].terms).isEqualTo(2)
        assertThat(dictionaries[0].media).isEqualTo(1)
        assertThat(dictionaries[1].termMeta).isEqualTo(1)
    }

    @Test
    fun `writes every table of a dictionary`() {
        val archives = YomitanBackup(directory).convert(export.byteInputStream())
        assertThat(archives).hasSize(2)
        val terms = entries(archives[0])
        val index = Json.parseToJsonElement(terms.getValue("index.json")).jsonObject
        assertThat(index["title"]!!.jsonPrimitive.content).isEqualTo("Terms")
        assertThat(index["revision"]!!.jsonPrimitive.content).isEqualTo("r1")
        assertThat(index["format"]!!.jsonPrimitive.content).isEqualTo("3")
        assertThat(index["indexUrl"]!!.jsonPrimitive.content).isEqualTo("https://example.com/index.json")
        assertThat(index.keys).containsNoneOf("counts", "importDate", "styles")
        assertThat(terms.getValue("styles.css")).isEqualTo(".x{color:red}")
        val termBank = Json.parseToJsonElement(terms.getValue("term_bank_1.json")).jsonArray
        assertThat(termBank).hasSize(2)
        assertThat(termBank[0].jsonArray[5].toString()).contains("to \\\"eat\\\"")
        assertThat(Json.parseToJsonElement(terms.getValue("kanji_bank_1.json")).jsonArray).hasSize(1)
        assertThat(Json.parseToJsonElement(terms.getValue("tag_bank_1.json")).jsonArray).hasSize(1)
        assertThat(terms.keys).contains("img/dot.png")

        val freq = entries(archives[1])
        val meta = Json.parseToJsonElement(freq.getValue("term_meta_bank_1.json")).jsonArray.single().jsonArray
        assertThat(meta[0].jsonPrimitive.content).isEqualTo("食べる")
        assertThat(meta[2].jsonObject["value"]!!.jsonPrimitive.content).isEqualTo("120")
        assertThat(Json.parseToJsonElement(freq.getValue("index.json")).jsonObject["frequencyMode"]!!.jsonPrimitive.content)
            .isEqualTo("rank-based")
    }

    @Test
    fun `converts only the chosen dictionaries`() {
        val archives = YomitanBackup(directory).convert(export.byteInputStream(), titles = setOf("Freq"))
        assertThat(archives).hasSize(1)
        assertThat(entries(archives.single()).keys).contains("term_meta_bank_1.json")
    }

    /** Speed on the owner's multi-gigabyte export in testdata/, run with `BENCHMARK_COLLECTION=1`. */
    @Test
    fun `benchmark on a real export`() {
        assumeTrue(System.getenv("BENCHMARK_COLLECTION") == "1")
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "testdata/dictionaries/yomitan-dictionaries-2026-01-22-22-46-44.json") }
            .firstOrNull { it.exists() }
        assumeTrue(file != null)
        var started = System.nanoTime()
        val listed = file!!.inputStream().use { YomitanBackup.scan(it) }
        println("Scan: ${listed.size} dictionaries in ${(System.nanoTime() - started) / 1_000_000} ms")
        listed.forEach { println("  ${it.title}: ${it.terms} terms, ${it.termMeta} meta, ${it.kanji} kanji, ${it.media} media") }
        started = System.nanoTime()
        val archives = file.inputStream().use { YomitanBackup(directory).convert(it) }
        val seconds = (System.nanoTime() - started) / 1e9
        println("Convert: ${archives.size} archives, ${archives.sumOf { it.length() } shr 20} MB in ${"%.1f".format(seconds)} s")
        archives.forEach { archive ->
            val index = ZipFile(archive).use { zip -> zip.getInputStream(zip.getEntry("index.json")).readBytes().decodeToString() }
            println("  ${Json.parseToJsonElement(index).jsonObject["title"]} ${archive.length() shr 20} MB")
        }
        assertThat(archives.size).isEqualTo(listed.size)
    }
}
