package com.vpr.screenlate.dictionary.api.imports

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
 *
 * Row values are copied as UTF-8 bytes without decoding; only dictionary titles, media paths and the summary
 * table are decoded. The archives are read once by the engine and deleted, so by default they are stored
 * uncompressed: deflating them doubles the conversion time.
 *
 * [measure] reads the same rows without writing anything and reports what they take, so an import can choose
 * between the two and check the free space first ([CollectionSpace]).
 */
class YomitanBackup private constructor(
    private val outputDir: File?,
    private val compress: Boolean,
    private val measuring: Boolean,
) {
    /**
     * @param compress deflate the archives (fast level) to use less temporary space.
     */
    constructor(outputDir: File, compress: Boolean = false) : this(outputDir, compress, measuring = false)

    private val targets = linkedMapOf<String, Target>()
    private var selected: Set<String>? = null

    /**
     * Titles from the summary table. Exports can hold rows whose dictionary name was damaged when Yomitan wrote
     * the file (replacement characters in the middle); such rows belong to no listed dictionary and are skipped.
     */
    private val listed = HashSet<String>()

    /** The current row's field values as raw JSON, located by [starts] and [ends] (-1 when absent). */
    private val row = ByteBuilder(1 shl 12)
    private val starts = IntArray(Field.entries.size)
    private val ends = IntArray(Field.entries.size)

    /** Sizes of the current row's values when measuring; the dictionary name is copied into [row] as usual. */
    private var rowText = 0L
    private var rowMedia = 0L

    /** Rows of one dictionary come in runs, so the last dictionary name and its target are remembered. */
    private var lastDictionary: ByteArray? = null
    private var lastTarget: Target? = null

    /**
     * Reads [input] and returns the written archives, one per dictionary.
     *
     * @param titles dictionaries to convert; null converts all of them.
     * @param onProgress called with the number of bytes read so far, every few megabytes.
     */
    fun convert(input: InputStream, titles: Set<String>? = null, onProgress: (Long) -> Unit = {}): List<File> {
        selected = titles
        val scanner = RawJsonScanner(CountingInputStream(input, onProgress))
        try {
            forEachTable(scanner) { table -> readRows(scanner, table) }
            return targets.values.mapNotNull { it.archive?.close() }
        } catch (e: Exception) {
            targets.values.forEach { it.archive?.abort() }
            throw e
        }
    }

    private fun measureRows(input: InputStream, titles: Set<String>?, onProgress: (Long) -> Unit): List<CollectionSize> {
        selected = titles
        val scanner = RawJsonScanner(CountingInputStream(input, onProgress))
        forEachTable(scanner) { table -> readRows(scanner, table) }
        return targets.values.map { CollectionSize(it.title, it.rows, it.textBytes, it.mediaBytes) }
    }

    private fun readRows(scanner: RawJsonScanner, tableName: String) {
        val table = Table.entries.firstOrNull { it.tableName == tableName }
        scanner.beginArray()
        while (scanner.hasNext()) {
            when (table) {
                null -> scanner.skipValue()
                Table.DICTIONARIES -> readSummary(scanner, row)?.let(::handleSummary)
                else -> {
                    readFields(scanner)
                    handle(table)
                }
            }
        }
        scanner.endArray()
    }

    private fun handleSummary(summary: JsonObject) {
        val title = summary["title"]?.jsonPrimitive?.contentOrNull ?: return
        listed += title
        lastDictionary = null
        target(title)?.archive?.summary = summary
    }

    /** One row into [row], [starts] and [ends]. A row without its key inline is `{"$": [key, row]}`. */
    private fun readFields(scanner: RawJsonScanner) {
        row.clear()
        starts.fill(-1)
        rowText = 0
        rowMedia = 0
        scanner.beginObject()
        while (scanner.hasNext()) {
            val name = scanner.nextRawName()
            if (name.size == 1 && name.array[0] == DOLLAR) {
                scanner.beginArray()
                scanner.skipValue()
                if (scanner.hasNext()) readMembers(scanner)
                while (scanner.hasNext()) scanner.skipValue()
                scanner.endArray()
            } else {
                readMember(scanner, name)
            }
        }
        scanner.endObject()
    }

    private fun readMembers(scanner: RawJsonScanner) {
        scanner.beginObject()
        while (scanner.hasNext()) readMember(scanner, scanner.nextRawName())
        scanner.endObject()
    }

    /** Keeps the value of a known field (the last one wins); names starting with `$` are never fields. */
    private fun readMember(scanner: RawJsonScanner, name: ByteBuilder) {
        val field = Field.match(name)
        if (field == null) {
            scanner.skipValue()
            return
        }
        if (measuring && field != Field.DICTIONARY) {
            val start = scanner.offset
            scanner.skipValue()
            if (field == Field.CONTENT) rowMedia += scanner.offset - start else rowText += scanner.offset - start
            return
        }
        starts[field.ordinal] = row.size
        scanner.copyValue(row)
        ends[field.ordinal] = row.size
    }

    private fun handle(table: Table) {
        val target = rowTarget() ?: return
        if (measuring) {
            if (table == Table.MEDIA) {
                // A quoted base64 string in the export, bytes in the archive.
                target.mediaBytes += (rowMedia - 2).coerceAtLeast(0) * 3 / 4
            } else {
                target.rows++
                target.textBytes += rowText + ROW_OVERHEAD
            }
            return
        }
        val archive = target.archive ?: return
        when (table) {
            Table.TERMS -> archive.add(Bank.TERM) { out ->
                value(out, Field.EXPRESSION)
                value(out, Field.READING)
                value(out, if (starts[Field.DEFINITION_TAGS.ordinal] >= 0) Field.DEFINITION_TAGS else Field.TAGS)
                value(out, Field.RULES)
                value(out, Field.SCORE, ZERO)
                value(out, Field.GLOSSARY, EMPTY_ARRAY)
                value(out, Field.SEQUENCE, ZERO)
                value(out, Field.TERM_TAGS)
            }
            Table.TERM_META -> archive.add(Bank.TERM_META) { out ->
                value(out, Field.EXPRESSION)
                value(out, Field.MODE)
                value(out, Field.DATA)
            }
            Table.KANJI -> archive.add(Bank.KANJI) { out ->
                value(out, Field.CHARACTER)
                value(out, Field.ONYOMI)
                value(out, Field.KUNYOMI)
                value(out, Field.TAGS)
                value(out, Field.MEANINGS, EMPTY_ARRAY)
                value(out, Field.STATS, EMPTY_OBJECT)
            }
            Table.KANJI_META -> archive.add(Bank.KANJI_META) { out ->
                value(out, Field.CHARACTER)
                value(out, Field.MODE)
                value(out, Field.DATA)
            }
            Table.TAG_META -> archive.add(Bank.TAG) { out ->
                value(out, Field.NAME)
                value(out, Field.CATEGORY)
                value(out, Field.ORDER, ZERO)
                value(out, Field.NOTES)
                value(out, Field.SCORE, ZERO)
            }
            Table.MEDIA -> {
                val path = string(Field.PATH) ?: return
                val content = mediaContent() ?: return
                archive.addMedia(path, content)
            }
            Table.DICTIONARIES -> Unit
        }
    }

    /** The target of the row's dictionary; null when the row has none or the dictionary is not imported. */
    private fun rowTarget(): Target? {
        val start = starts[Field.DICTIONARY.ordinal]
        if (start < 0) return null
        val end = ends[Field.DICTIONARY.ordinal]
        lastDictionary?.let { if (row.contentEquals(start, end, it)) return lastTarget }
        val title = RawJsonScanner.unquote(row.array, start, end) ?: return null
        lastDictionary = row.array.copyOfRange(start, end)
        lastTarget = target(title)
        return lastTarget
    }

    /** Appends the field as written, or [missing] when the row lacks it, after a comma unless it is the first value. */
    private fun value(out: ByteBuilder, field: Field, missing: ByteArray = EMPTY_STRING) {
        if (out.array[out.size - 1] != ARRAY_START) out.append(COMMA)
        val start = starts[field.ordinal]
        if (start < 0) out.append(missing) else out.append(row.array, start, ends[field.ordinal] - start)
    }

    private fun string(field: Field): String? {
        val start = starts[field.ordinal]
        return if (start < 0) null else RawJsonScanner.unquote(row.array, start, ends[field.ordinal])
    }

    /**
     * Media content is an ArrayBuffer, which dexie-export-import writes as a base64 string (with a `$types`
     * note on the row), or a Blob, written as an object with a base64 `data` field.
     */
    private fun mediaContent(): ByteArray? {
        val start = starts[Field.CONTENT.ordinal]
        if (start < 0) return null
        val end = ends[Field.CONTENT.ordinal]
        return when (row.array[start]) {
            QUOTE -> {
                var escaped = false
                for (i in start + 1 until end - 1) if (row.array[i] == BACKSLASH) escaped = true
                if (escaped) {
                    string(Field.CONTENT)?.let { Base64.getMimeDecoder().decode(it) }
                } else {
                    Base64.getMimeDecoder().decode(row.array.copyOfRange(start + 1, end - 1))
                }
            }
            OBJECT_START -> runCatching {
                json.parseToJsonElement(row.decode(start, end)).jsonObject["data"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.let { Base64.getMimeDecoder().decode(it) }
            else -> null
        }
    }

    /** A selected dictionary; null when it is not being imported. */
    private fun target(title: String): Target? {
        if (selected?.contains(title) == false) return null
        if (listed.isNotEmpty() && title !in listed) return null
        return targets.getOrPut(title) {
            Target(title, if (measuring) null else Archive(title, requireNotNull(outputDir), compress))
        }
    }

    /** A dictionary being converted into [archive], or measured. */
    private class Target(val title: String, val archive: Archive?) {
        var rows = 0L
        var textBytes = 0L
        var mediaBytes = 0L
    }

    private enum class Table(val tableName: String) {
        DICTIONARIES("dictionaries"),
        TERMS("terms"),
        TERM_META("termMeta"),
        KANJI("kanji"),
        KANJI_META("kanjiMeta"),
        TAG_META("tagMeta"),
        MEDIA("media"),
    }

    /** Row fields that go into banks or decide where a row goes. */
    private enum class Field(key: String) {
        DICTIONARY("dictionary"),
        EXPRESSION("expression"),
        READING("reading"),
        DEFINITION_TAGS("definitionTags"),
        TAGS("tags"),
        RULES("rules"),
        SCORE("score"),
        GLOSSARY("glossary"),
        SEQUENCE("sequence"),
        TERM_TAGS("termTags"),
        MODE("mode"),
        DATA("data"),
        CHARACTER("character"),
        ONYOMI("onyomi"),
        KUNYOMI("kunyomi"),
        MEANINGS("meanings"),
        STATS("stats"),
        NAME("name"),
        CATEGORY("category"),
        ORDER("order"),
        NOTES("notes"),
        PATH("path"),
        CONTENT("content"),
        ;

        val bytes = key.encodeToByteArray()

        companion object {
            private val byLength: Array<List<Field>> = Array(entries.maxOf { it.bytes.size } + 1) { length ->
                entries.filter { it.bytes.size == length }
            }

            fun match(name: ByteBuilder): Field? {
                if (name.size >= byLength.size) return null
                for (field in byLength[name.size]) if (name.contentEquals(0, name.size, field.bytes)) return field
                return null
            }
        }
    }

    private enum class Bank(val fileName: String) {
        TERM("term_bank"),
        TERM_META("term_meta_bank"),
        KANJI("kanji_bank"),
        KANJI_META("kanji_meta_bank"),
        TAG("tag_bank"),
    }

    /** One dictionary being written. Banks are flushed as separate zip entries every [BANK_SIZE] rows. */
    private class Archive(private val title: String, directory: File, private val compress: Boolean) {
        val file = File(directory, "${title.hashCode().toUInt()}-${System.nanoTime()}.zip")
        private val zip = ZipOutputStream(BufferedOutputStream(FileOutputStream(file), 1 shl 16)).apply {
            setLevel(Deflater.BEST_SPEED)
        }
        private val pending = arrayOfNulls<ByteBuilder>(Bank.entries.size)
        private val counts = IntArray(Bank.entries.size)
        private val numbers = IntArray(Bank.entries.size)

        /** The size of each bank's last full buffer, so the next one starts large enough instead of growing. */
        private val capacities = IntArray(Bank.entries.size) { 1 shl 16 }
        var summary: JsonObject? = null

        /** Adds a row that [write] appends to the bank's JSON array. */
        inline fun add(bank: Bank, write: (ByteBuilder) -> Unit) {
            val rows = pending[bank.ordinal] ?: ByteBuilder(capacities[bank.ordinal]).also {
                it.append(ARRAY_START)
                pending[bank.ordinal] = it
            }
            if (rows.size > 1) rows.append(COMMA)
            rows.append(ARRAY_START)
            write(rows)
            rows.append(ARRAY_END)
            if (++counts[bank.ordinal] >= BANK_SIZE) flush(bank)
        }

        fun addMedia(path: String, bytes: ByteArray) = putEntry(path.trimStart('/'), bytes, bytes.size)

        fun flush(bank: Bank) {
            val rows = pending[bank.ordinal] ?: return
            // A bank's buffer is dropped rather than reused: many banks of many dictionaries would hold memory.
            pending[bank.ordinal] = null
            counts[bank.ordinal] = 0
            if (rows.size <= 1) return
            rows.append(ARRAY_END)
            capacities[bank.ordinal] = maxOf(capacities[bank.ordinal], rows.size + rows.size / 8)
            numbers[bank.ordinal]++
            putEntry("${bank.fileName}_${numbers[bank.ordinal]}.json", rows.array, rows.size)
        }

        fun close(): File {
            Bank.entries.forEach(::flush)
            indexJson().encodeToByteArray().let { putEntry("index.json", it, it.size) }
            summary?.get("styles")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { css ->
                css.encodeToByteArray().let { putEntry("styles.css", it, it.size) }
            }
            zip.close()
            return file
        }

        fun abort() {
            runCatching { zip.close() }
            file.delete()
        }

        private fun putEntry(name: String, bytes: ByteArray, length: Int) {
            val entry = ZipEntry(name)
            if (!compress) {
                entry.method = ZipEntry.STORED
                entry.size = length.toLong()
                entry.compressedSize = length.toLong()
                entry.crc = CRC32().apply { update(bytes, 0, length) }.value
            }
            zip.putNextEntry(entry)
            zip.write(bytes, 0, length)
            zip.closeEntry()
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

        /** Brackets, commas and default values a bank row adds to its values. */
        private const val ROW_OVERHEAD = 16
        private const val DOLLAR = '$'.code.toByte()
        private const val QUOTE = '"'.code.toByte()
        private const val BACKSLASH = '\\'.code.toByte()
        private const val OBJECT_START = '{'.code.toByte()
        private const val ARRAY_START = '['.code.toByte()
        private const val ARRAY_END = ']'.code.toByte()
        private const val COMMA = ','.code.toByte()
        private val EMPTY_STRING = "\"\"".encodeToByteArray()
        private val EMPTY_ARRAY = "[]".encodeToByteArray()
        private val EMPTY_OBJECT = "{}".encodeToByteArray()
        private val ZERO = "0".encodeToByteArray()
        private val json = Json { ignoreUnknownKeys = true }
        private val DROPPED_SUMMARY_FIELDS = setOf(
            "id", "version", "importDate", "counts", "styles", "importSuccess", "yomitanVersion", "prefixWildcardsSupported",
        )

        /**
         * What the chosen dictionaries of an export take, in the order [convert] writes and the import installs
         * them. Reads the whole file but writes nothing.
         *
         * @param titles dictionaries to measure; null measures all of them.
         * @param onProgress called with the number of bytes read so far, every few megabytes.
         */
        fun measure(
            input: InputStream,
            titles: Set<String>? = null,
            onProgress: (Long) -> Unit = {},
        ): List<CollectionSize> =
            YomitanBackup(outputDir = null, compress = false, measuring = true).measureRows(input, titles, onProgress)

        /**
         * The dictionaries of an export, from its summary table. Dexie writes tables in name order, so the summary
         * comes first and only the start of the file is read.
         */
        fun scan(input: InputStream): List<CollectionDictionary> {
            val scanner = RawJsonScanner(input)
            val found = mutableListOf<CollectionDictionary>()
            val row = ByteBuilder(1 shl 12)
            forEachTableWhile(scanner) { table ->
                if (table != Table.DICTIONARIES.tableName) {
                    scanner.skipValue()
                    return@forEachTableWhile true
                }
                scanner.beginArray()
                while (scanner.hasNext()) readSummary(scanner, row)?.toCollectionDictionary()?.let { found += it }
                false
            }
            return found
        }

        /**
         * A summary row as one object: its own fields and those inside `{"$": [key, row]}`, without the `$` notes
         * dexie adds. Null when the row is not an object.
         */
        private fun readSummary(scanner: RawJsonScanner, row: ByteBuilder): JsonObject? {
            row.clear()
            scanner.copyValue(row)
            val parsed = runCatching { json.parseToJsonElement(row.decode(0, row.size)) as? JsonObject }.getOrNull()
                ?: return null
            val fields = LinkedHashMap<String, JsonElement>()
            fun collect(from: JsonObject) = from.forEach { (key, value) -> if (!key.startsWith("$")) fields[key] = value }
            parsed.forEach { (key, value) ->
                when {
                    key == "$" -> ((value as? JsonArray)?.getOrNull(1) as? JsonObject)?.let(::collect)
                    key.startsWith("$") -> Unit
                    else -> fields[key] = value
                }
            }
            return JsonObject(fields)
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
    }
}
