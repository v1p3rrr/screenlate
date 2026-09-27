package com.vpr.screenlate.dictionary.api.imports

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A dictionary listed in a collection export, for choosing what to import. Counts come from Yomitan's summary. */
data class CollectionDictionary(
    val title: String,
    val revision: String,
    val terms: Long,
    val termMeta: Long,
    val kanji: Long,
    val kanjiMeta: Long,
    val media: Long,
) {
    val entries: Long get() = terms + termMeta + kanji + kanjiMeta
}

/**
 * Converts Yomitan's "Export dictionary collection" file (a dexie-export-import JSON of its IndexedDB) back
 * into one Yomitan archive per dictionary, which the engine can then import as usual.
 *
 * The file can be gigabytes, so it is streamed: rows of every table are appended to the archive of their
 * dictionary as they are read, in banks of [BANK_SIZE] rows. Tables without their key in the row (dictionaries,
 * termMeta, kanji, kanjiMeta, tagMeta) store rows as `{"$": [key, row]}`.
 */
class YomitanBackup(private val outputDir: File) {
    private val archives = linkedMapOf<String, Archive>()
    private var selected: Set<String>? = null

    /**
     * Titles from the summary table. Exports can hold rows whose dictionary name was damaged when Yomitan wrote
     * the file (replacement characters in the middle); such rows belong to no listed dictionary and are skipped.
     */
    private val listed = HashSet<String>()

    /**
     * Reads [input] and returns the written archives, one per dictionary.
     *
     * @param titles dictionaries to convert; null converts all of them.
     * @param onProgress called with the number of bytes read so far, every few megabytes.
     */
    fun convert(input: InputStream, titles: Set<String>? = null, onProgress: (Long) -> Unit = {}): List<File> {
        selected = titles
        val counting = CountingInputStream(input, onProgress)
        val scanner = RawJsonScanner(counting.bufferedReader(Charsets.UTF_8))
        try {
            forEachTable(scanner) { table -> readRows(scanner, table) }
            return archives.values.map { it.close() }
        } catch (e: Exception) {
            archives.values.forEach { it.abort() }
            throw e
        }
    }

    private fun readRows(scanner: RawJsonScanner, table: String) {
        scanner.beginArray()
        val fields = HashMap<String, String>()
        while (scanner.hasNext()) {
            fields.clear()
            readRow(scanner, fields)
            handle(table, fields)
        }
        scanner.endArray()
    }

    private fun handle(table: String, fields: Map<String, String>) {
        if (table == "dictionaries") {
            val summary = runCatching { json.parseToJsonElement(rowObject(fields)).jsonObject }.getOrNull()
            val title = summary?.get("title")?.jsonPrimitive?.contentOrNull ?: return
            listed += title
            archive(title)?.summary = summary
            return
        }
        val dictionary = fields["dictionary"]?.let(RawJsonScanner::unquote) ?: return
        val archive = archive(dictionary) ?: return
        when (table) {
            "terms" -> archive.add(
                Bank.TERM,
                row(
                    fields["expression"],
                    fields["reading"],
                    fields["definitionTags"] ?: fields["tags"],
                    fields["rules"],
                    fields["score"] ?: "0",
                    fields["glossary"] ?: "[]",
                    fields["sequence"] ?: "0",
                    fields["termTags"],
                ),
            )
            "termMeta" -> archive.add(Bank.TERM_META, row(fields["expression"], fields["mode"], fields["data"]))
            "kanji" -> archive.add(
                Bank.KANJI,
                row(
                    fields["character"],
                    fields["onyomi"],
                    fields["kunyomi"],
                    fields["tags"],
                    fields["meanings"] ?: "[]",
                    fields["stats"] ?: "{}",
                ),
            )
            "kanjiMeta" -> archive.add(Bank.KANJI_META, row(fields["character"], fields["mode"], fields["data"]))
            "tagMeta" -> archive.add(
                Bank.TAG,
                row(fields["name"], fields["category"], fields["order"] ?: "0", fields["notes"], fields["score"] ?: "0"),
            )
            "media" -> {
                val path = fields["path"]?.let(RawJsonScanner::unquote) ?: return
                val content = fields["content"]?.let(::mediaBase64) ?: return
                archive.addMedia(path, Base64.getMimeDecoder().decode(content))
            }
        }
    }

    /** The archive of a selected dictionary; null when it is not being imported. */
    private fun archive(title: String): Archive? {
        if (selected?.contains(title) == false) return null
        if (listed.isNotEmpty() && title !in listed) return null
        return archives.getOrPut(title) { Archive(title, outputDir) }
    }

    /**
     * Media content is an ArrayBuffer, which dexie-export-import writes as a base64 string (with a `$types`
     * note on the row), or a Blob, written as an object with a base64 `data` field.
     */
    private fun mediaBase64(raw: String): String? {
        if (raw.startsWith("\"")) return RawJsonScanner.unquote(raw)
        if (!raw.startsWith("{")) return null
        return runCatching { json.parseToJsonElement(raw).jsonObject["data"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }

    /** Yomitan bank arrays; absent string values become empty strings. */
    private fun row(vararg values: String?): String = values.joinToString(",", "[", "]") { it ?: "\"\"" }

    private enum class Bank(val fileName: String) {
        TERM("term_bank"),
        TERM_META("term_meta_bank"),
        KANJI("kanji_bank"),
        KANJI_META("kanji_meta_bank"),
        TAG("tag_bank"),
    }

    /** One dictionary being written. Banks are flushed as separate zip entries every [BANK_SIZE] rows. */
    private class Archive(private val title: String, directory: File) {
        val file = File(directory, "${title.hashCode().toUInt()}-${System.nanoTime()}.zip")
        // The archive is read once and deleted; fast compression keeps it small enough without slowing the import.
        private val zip = ZipOutputStream(file.outputStream().buffered()).apply { setLevel(Deflater.BEST_SPEED) }
        private val pending = HashMap<Bank, StringBuilder>()
        private val counts = HashMap<Bank, Int>()
        private val numbers = HashMap<Bank, Int>()
        var summary: JsonObject? = null

        fun add(bank: Bank, row: String) {
            val rows = pending.getOrPut(bank) { StringBuilder("[") }
            if (rows.length > 1) rows.append(',')
            rows.append(row)
            val count = (counts[bank] ?: 0) + 1
            counts[bank] = count
            if (count >= BANK_SIZE) flush(bank)
        }

        fun addMedia(path: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(path.trimStart('/')))
            zip.write(bytes)
            zip.closeEntry()
        }

        private fun flush(bank: Bank) {
            val rows = pending[bank] ?: return
            if (rows.length <= 1) return
            val number = (numbers[bank] ?: 0) + 1
            numbers[bank] = number
            zip.putNextEntry(ZipEntry("${bank.fileName}_$number.json"))
            zip.write(rows.append(']').toString().toByteArray())
            zip.closeEntry()
            pending.remove(bank)
            counts[bank] = 0
        }

        fun close(): File {
            Bank.entries.forEach(::flush)
            zip.putNextEntry(ZipEntry("index.json"))
            zip.write(indexJson().toByteArray())
            zip.closeEntry()
            summary?.get("styles")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { css ->
                zip.putNextEntry(ZipEntry("styles.css"))
                zip.write(css.toByteArray())
                zip.closeEntry()
            }
            zip.close()
            return file
        }

        fun abort() {
            runCatching { zip.close() }
            file.delete()
        }

        /** index.json from the stored summary: its fields except the ones that describe Yomitan's import. */
        private fun indexJson(): String {
            val fields = summary.orEmpty()
            val kept = fields.filterKeys { it !in DROPPED_SUMMARY_FIELDS && !it.startsWith("$") }.toMutableMap()
            kept.putIfAbsent("title", JsonPrimitive(title))
            kept.putIfAbsent("revision", JsonPrimitive("yomitan-backup"))
            kept["format"] = fields["version"] ?: JsonPrimitive(3)
            return json.encodeToString(JsonObject.serializer(), JsonObject(kept))
        }
    }

    private class CountingInputStream(input: InputStream, private val onProgress: (Long) -> Unit) :
        FilterInputStream(input) {
        private var count = 0L
        private var reported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        private fun advance(bytes: Long) {
            count += bytes
            if (count - reported >= PROGRESS_STEP) {
                reported = count
                onProgress(count)
            }
        }
    }

    companion object {
        const val BANK_SIZE = 10_000
        private const val PROGRESS_STEP = 8L shl 20
        private val json = Json { ignoreUnknownKeys = true }
        private val DROPPED_SUMMARY_FIELDS = setOf(
            "id", "version", "importDate", "counts", "styles", "importSuccess", "yomitanVersion", "prefixWildcardsSupported",
        )

        /**
         * The dictionaries of an export, from its summary table. Dexie writes tables in name order, so the summary
         * comes first and only the start of the file is read.
         */
        fun scan(input: InputStream): List<CollectionDictionary> {
            val scanner = RawJsonScanner(input.bufferedReader(Charsets.UTF_8))
            val found = mutableListOf<CollectionDictionary>()
            forEachTableWhile(scanner) { table ->
                if (table != "dictionaries") {
                    scanner.skipValue()
                    return@forEachTableWhile true
                }
                scanner.beginArray()
                val fields = HashMap<String, String>()
                while (scanner.hasNext()) {
                    fields.clear()
                    readRow(scanner, fields)
                    runCatching { json.parseToJsonElement(rowObject(fields)).jsonObject }.getOrNull()
                        ?.toCollectionDictionary()
                        ?.let { found += it }
                }
                false
            }
            return found
        }

        private fun JsonObject.toCollectionDictionary(): CollectionDictionary? {
            val title = this["title"]?.jsonPrimitive?.contentOrNull ?: return null
            val counts = this["counts"] as? JsonObject
            fun count(table: String) =
                ((counts?.get(table) as? JsonObject)?.get("total") as? JsonPrimitive)?.longOrNull ?: 0L
            return CollectionDictionary(
                title = title,
                revision = this["revision"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                terms = count("terms"),
                termMeta = count("termMeta"),
                kanji = count("kanji"),
                kanjiMeta = count("kanjiMeta"),
                media = count("media"),
            )
        }

        /**
         * Calls [onTable] with the scanner positioned at each table's `rows` array; the callback consumes the array
         * and returns whether to go on.
         */
        private inline fun forEachTable(scanner: RawJsonScanner, onTable: (String) -> Unit) =
            forEachTableWhile(scanner) { table ->
                onTable(table)
                true
            }

        private inline fun forEachTableWhile(scanner: RawJsonScanner, onTable: (String) -> Boolean) {
            scanner.beginObject()
            while (scanner.hasNext()) {
                if (scanner.nextName() != "data") {
                    scanner.skipValue()
                    continue
                }
                scanner.beginObject()
                while (scanner.hasNext()) {
                    if (scanner.nextName() != "data") {
                        scanner.skipValue()
                        continue
                    }
                    scanner.beginArray()
                    while (scanner.hasNext()) {
                        var table = ""
                        scanner.beginObject()
                        while (scanner.hasNext()) {
                            when (scanner.nextName()) {
                                "tableName" -> table = scanner.nextString()
                                "rows" -> if (!onTable(table)) return
                                else -> scanner.skipValue()
                            }
                        }
                        scanner.endObject()
                    }
                    scanner.endArray()
                }
                scanner.endObject()
            }
            scanner.endObject()
        }

        /** One row into [fields] as raw JSON per field. A row without its key inline is `{"$": [key, row]}`. */
        private fun readRow(scanner: RawJsonScanner, fields: HashMap<String, String>) {
            scanner.beginObject()
            while (scanner.hasNext()) {
                val name = scanner.nextName()
                when {
                    name == "$" -> {
                        scanner.beginArray()
                        scanner.skipValue()
                        if (scanner.hasNext()) readFields(scanner, fields)
                        while (scanner.hasNext()) scanner.skipValue()
                        scanner.endArray()
                    }
                    name.startsWith("$") -> scanner.skipValue()
                    else -> fields[name] = scanner.rawValue()
                }
            }
            scanner.endObject()
        }

        private fun readFields(scanner: RawJsonScanner, fields: HashMap<String, String>) {
            scanner.beginObject()
            while (scanner.hasNext()) {
                val name = scanner.nextName()
                if (name.startsWith("$")) scanner.skipValue() else fields[name] = scanner.rawValue()
            }
            scanner.endObject()
        }

        /** The row as a JSON object again, for the few rows that are parsed (dictionary summaries). */
        private fun rowObject(fields: Map<String, String>): String =
            fields.entries.joinToString(",", "{", "}") { (key, value) -> "${JsonPrimitive(key)}:$value" }
    }
}
