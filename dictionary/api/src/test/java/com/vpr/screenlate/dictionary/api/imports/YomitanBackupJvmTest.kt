package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
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

    /** An export with the given summary rows and table rows, in dexie's layout. */
    private fun export(summaries: List<String>, vararg tables: Pair<String, List<String>>): String {
        val all = listOf("dictionaries" to summaries) + tables
        return """{"formatName":"dexie","formatVersion":1,"data":{"databaseName":"dict","tables":[],"data":[""" +
            all.joinToString(",") { (name, rows) -> """{"tableName":"$name","inbound":true,"rows":[${rows.joinToString(",")}]}""" } +
            "]}}"
    }

    private fun summary(title: String) = """{"$":[1,{"title":"$title","revision":"1","version":3}]}"""

    private fun termBanks(zip: File): List<List<String>> {
        val entries = entries(zip)
        return generateSequence(1) { it + 1 }
            .map { entries["term_bank_$it.json"] }
            .takeWhile { it != null }
            .map { bank -> Json.parseToJsonElement(bank!!).jsonArray.map { it.jsonArray[0].jsonPrimitive.content } }
            .toList()
    }

    @Test
    fun `stores archives uncompressed unless asked to compress`() {
        val stored = YomitanBackup(File(directory, "stored").apply { mkdirs() }).convert(export.byteInputStream())
        val deflated = YomitanBackup(File(directory, "deflated").apply { mkdirs() }, compress = true)
            .convert(export.byteInputStream())
        assertThat(stored.map(::entries)).isEqualTo(deflated.map(::entries))
        ZipFile(stored[0]).use { zip -> assertThat(zip.entries().toList().map { it.method }.toSet()).containsExactly(ZipEntry.STORED) }
        ZipFile(deflated[0]).use { zip -> assertThat(zip.entries().toList().map { it.method }.toSet()).containsExactly(ZipEntry.DEFLATED) }
    }

    @Test
    fun `splits banks and keeps interleaved dictionaries apart`() {
        val rows = (0 until YomitanBackup.BANK_SIZE + 3).flatMap { i ->
            listOf(
                """{"expression":"a$i","reading":"","glossary":["x"],"dictionary":"A","id":${2 * i}}""",
                """{"expression":"b$i","reading":"","glossary":["y"],"dictionary":"B","id":${2 * i + 1}}""",
            )
        }
        val archives = YomitanBackup(directory).convert(export(listOf(summary("A"), summary("B")), "terms" to rows).byteInputStream())
        val (a, b) = archives.map(::termBanks)
        assertThat(a.map { it.size }).containsExactly(YomitanBackup.BANK_SIZE, 3).inOrder()
        assertThat(a.flatten().all { it.startsWith("a") }).isTrue()
        assertThat(b.flatten()).hasSize(YomitanBackup.BANK_SIZE + 3)
        assertThat(b.flatten().all { it.startsWith("b") }).isTrue()
        assertThat(b.flatten().last()).isEqualTo("b${YomitanBackup.BANK_SIZE + 2}")
    }

    @Test
    fun `fills in missing values and keeps the ones written`() {
        val rows = listOf(
            """{"expression":"食べる","glossary":["eat"],"tags":"v","termTags":null,"dictionary":"A","id":1}""",
            """{"id":2,"dictionary":"A","reading":"たべる","expression":"食\u3079る","rules":"v1","definitionTags":"d",""" +
                """"tags":"t","score":-1,"sequence":7,"glossary":[{"type":"text","text":"x"}],"termTags":"P"}""",
        )
        val kanji = listOf("""{"$":[1,{"character":"食","dictionary":"A"}]}""")
        val tags = listOf("""{"$":[1,{"name":"P","dictionary":"A"}]}""")
        val archive = YomitanBackup(directory)
            .convert(export(listOf(summary("A")), "kanji" to kanji, "tagMeta" to tags, "terms" to rows).byteInputStream())
            .single()
        val entries = entries(archive)
        assertThat(entries.getValue("term_bank_1.json")).isEqualTo(
            """[["食べる","","v","",0,["eat"],0,null],""" +
                """["食\u3079る","たべる","d","v1",-1,[{"type":"text","text":"x"}],7,"P"]]""",
        )
        assertThat(entries.getValue("kanji_bank_1.json")).isEqualTo("""[["食","","","",[],{}]]""")
        assertThat(entries.getValue("tag_bank_1.json")).isEqualTo("""[["P","",0,"",0]]""")
    }

    @Test
    fun `skips rows of dictionaries missing from the summary`() {
        val rows = listOf(
            """{"expression":"a","glossary":[],"dictionary":"A","id":1}""",
            """{"expression":"b","glossary":[],"dictionary":"\uFFFDA","id":2}""",
            """{"expression":"c","glossary":[],"dictionary":"A","id":3}""",
            """{"expression":"d","glossary":[],"id":4}""",
        )
        val archives = YomitanBackup(directory).convert(export(listOf(summary("A")), "terms" to rows).byteInputStream())
        assertThat(archives.map(::termBanks)).containsExactly(listOf(listOf("a", "c")))
    }

    @Test
    fun `reads media written as escaped base64 and as blobs`() {
        val bytes = byteArrayOf(-1, -1, -1, 1, 2, 3)
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val media = listOf(
            """{"dictionary":"A","path":"/img/a.png","content":"${base64.replace("/", "\\/")}","id":1}""",
            """{"dictionary":"A","path":"img/b.png","content":{"type":"image/png","data":"$base64"},"id":2}""",
            """{"dictionary":"A","path":"img/c.png","content":null,"id":3}""",
        )
        val archive = YomitanBackup(directory).convert(export(listOf(summary("A")), "media" to media).byteInputStream()).single()
        ZipFile(archive).use { zip ->
            assertThat(zip.getInputStream(zip.getEntry("img/a.png")).readBytes()).isEqualTo(bytes)
            assertThat(zip.getInputStream(zip.getEntry("img/b.png")).readBytes()).isEqualTo(bytes)
            assertThat(zip.getEntry("img/c.png")).isNull()
        }
    }

    @Test
    fun `reports progress while reading`() {
        val rows = (0 until 20_000).map { """{"expression":"${"x".repeat(500)}","glossary":[],"dictionary":"A","id":$it}""" }
        val text = export(listOf(summary("A")), "terms" to rows)
        val reported = mutableListOf<Long>()
        YomitanBackup(directory).convert(text.byteInputStream(), onProgress = { reported += it })
        assertThat(reported).isNotEmpty()
        assertThat(reported).isInOrder()
        assertThat(reported.last()).isAtMost(text.encodeToByteArray().size.toLong())
    }

    @Test
    fun `measures the chosen dictionaries in the order they are converted`() {
        val sizes = YomitanBackup.measure(export.byteInputStream())
        assertThat(sizes.map { it.title }).containsExactly("Terms", "Freq").inOrder()
        val (terms, freq) = sizes
        // Two terms, one kanji and one tag row; the media file is 3 bytes.
        assertThat(terms.rows).isEqualTo(4)
        assertThat(terms.mediaBytes).isEqualTo(3)
        assertThat(freq.rows).isEqualTo(1)
        assertThat(freq.mediaBytes).isEqualTo(0)

        val archives = YomitanBackup(directory).convert(export.byteInputStream())
        for ((size, archive) in sizes.zip(archives)) {
            val banks = ZipFile(archive).use { zip ->
                zip.entries().toList().filter { it.name.contains("_bank_") }.sumOf { it.size }
            }
            // Row values are counted exactly; brackets, commas and missing values are allowed for generously.
            assertThat(size.textBytes).isAtLeast(banks)
            assertThat(size.textBytes).isAtMost(banks + 24 * size.rows)
        }
        assertThat(YomitanBackup.measure(export.byteInputStream(), titles = setOf("Freq")).map { it.title }).containsExactly("Freq")
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
        val sizes = file.inputStream().use { YomitanBackup.measure(it) }
        println("Measure: ${(System.nanoTime() - started) / 1_000_000} ms")
        for (compressed in listOf(false, true)) {
            println("  peak with compressed=$compressed: ${CollectionSpace.peakBytes(sizes, compressed) shr 20} MB")
        }
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
