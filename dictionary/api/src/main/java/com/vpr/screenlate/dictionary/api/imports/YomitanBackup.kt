package com.vpr.screenlate.dictionary.api.imports

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.InputStream
import java.io.StringWriter
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Converts Yomitan's "Export dictionary collection" file (a dexie-export-import JSON of its IndexedDB) back
 * into one Yomitan archive per dictionary, which the engine can then import as usual.
 *
 * The file can be hundreds of megabytes, so it is streamed: rows of every table are appended to the archive of
 * their dictionary as they are read, in banks of [BANK_SIZE] rows.
 */
class YomitanBackup(private val outputDir: File) {
    private val archives = linkedMapOf<String, Archive>()

    /** Reads [input] and returns the written archives, one per dictionary. */
    fun convert(input: InputStream): List<File> {
        JsonReader(input.bufferedReader()).use { reader ->
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "data" -> readDatabase(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return archives.values.map { it.close() }
    }

    private fun readDatabase(reader: JsonReader) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> {
                    reader.beginArray()
                    while (reader.hasNext()) readTable(reader)
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readTable(reader: JsonReader) {
        var table = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tableName" -> table = reader.nextString()
                "rows" -> {
                    reader.beginArray()
                    while (reader.hasNext()) readRow(reader, table)
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    /** Reads one row as raw JSON per field and hands it to the table's writer. */
    private fun readRow(reader: JsonReader, table: String) {
        val fields = mutableMapOf<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            fields[name] = copyValue(reader)
        }
        reader.endObject()
        val dictionary = fields["dictionary"]?.let(::unquote)
        when (table) {
            "dictionaries" -> {
                val title = fields["title"]?.let(::unquote) ?: return
                archive(title).setSummary(fields)
            }
            "terms" -> archive(dictionary ?: return).add(
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
            "termMeta" -> archive(dictionary ?: return).add(
                Bank.TERM_META,
                row(fields["expression"], fields["mode"], fields["data"]),
            )
            "kanji" -> archive(dictionary ?: return).add(
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
            "kanjiMeta" -> archive(dictionary ?: return).add(
                Bank.KANJI_META,
                row(fields["character"], fields["mode"], fields["data"]),
            )
            "tagMeta" -> archive(dictionary ?: return).add(
                Bank.TAG,
                row(fields["name"], fields["category"], fields["order"] ?: "0", fields["notes"], fields["score"] ?: "0"),
            )
            "media" -> {
                val path = fields["path"]?.let(::unquote) ?: return
                val content = fields["content"]?.let(::mediaBase64) ?: return
                archive(dictionary ?: return).addMedia(path, Base64.decode(content, Base64.DEFAULT))
            }
        }
    }

    private fun archive(title: String): Archive = archives.getOrPut(title) { Archive(title, outputDir) }

    /**
     * Media content is an ArrayBuffer, which dexie-export-import writes as a base64 string (with a `$types`
     * note on the row), or a Blob, written as an object with a base64 `data` field.
     */
    private fun mediaBase64(json: String): String? {
        if (json.startsWith("\"")) return unquote(json)
        if (!json.startsWith("{")) return null
        JsonReader(json.reader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "data" && reader.peek() == JsonToken.STRING) return reader.nextString()
                reader.skipValue()
            }
        }
        return null
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
        private val zip = ZipOutputStream(file.outputStream().buffered())
        private val pending = mutableMapOf<Bank, MutableList<String>>()
        private val counters = mutableMapOf<Bank, Int>()
        private var summary: Map<String, String>? = null

        fun setSummary(fields: Map<String, String>) {
            summary = fields
        }

        fun add(bank: Bank, row: String) {
            val rows = pending.getOrPut(bank) { mutableListOf() }
            rows += row
            if (rows.size >= BANK_SIZE) flush(bank)
        }

        fun addMedia(path: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(path.trimStart('/')))
            zip.write(bytes)
            zip.closeEntry()
        }

        private fun flush(bank: Bank) {
            val rows = pending[bank].orEmpty()
            if (rows.isEmpty()) return
            val number = (counters[bank] ?: 0) + 1
            counters[bank] = number
            zip.putNextEntry(ZipEntry("${bank.fileName}_$number.json"))
            zip.write(rows.joinToString(",", "[", "]").toByteArray())
            zip.closeEntry()
            pending[bank] = mutableListOf()
        }

        fun close(): File {
            Bank.entries.forEach(::flush)
            val fields = summary.orEmpty()
            zip.putNextEntry(ZipEntry("index.json"))
            zip.write(indexJson(fields).toByteArray())
            zip.closeEntry()
            fields["styles"]?.let(::unquote)?.takeIf { it.isNotBlank() }?.let { css ->
                zip.putNextEntry(ZipEntry("styles.css"))
                zip.write(css.toByteArray())
                zip.closeEntry()
            }
            zip.close()
            return file
        }

        /** index.json from the stored summary: its fields except the ones that describe Yomitan's import. */
        private fun indexJson(fields: Map<String, String>): String {
            val kept = fields.filterKeys { it !in DROPPED_SUMMARY_FIELDS && !it.startsWith("$") }.toMutableMap()
            kept.putIfAbsent("title", quote(title))
            kept.putIfAbsent("revision", quote("yomitan-backup"))
            kept["format"] = fields["version"] ?: "3"
            return kept.entries.joinToString(",", "{", "}") { (key, value) -> "${quote(key)}:$value" }
        }
    }

    companion object {
        const val BANK_SIZE = 10_000
        private val DROPPED_SUMMARY_FIELDS = setOf(
            "id", "version", "importDate", "counts", "styles", "importSuccess", "yomitanVersion", "prefixWildcardsSupported",
        )

        /** Copies the next JSON value of [reader] and returns it as JSON text. */
        fun copyValue(reader: JsonReader): String {
            val out = StringWriter()
            JsonWriter(out).use { writer ->
                writer.isLenient = true
                copy(reader, writer)
            }
            return out.toString()
        }

        private fun copy(reader: JsonReader, writer: JsonWriter) {
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    writer.beginArray()
                    while (reader.hasNext()) copy(reader, writer)
                    reader.endArray()
                    writer.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    writer.beginObject()
                    while (reader.hasNext()) {
                        writer.name(reader.nextName())
                        copy(reader, writer)
                    }
                    reader.endObject()
                    writer.endObject()
                }
                JsonToken.STRING -> writer.value(reader.nextString())
                JsonToken.NUMBER -> writer.value(BigDecimal(reader.nextString()))
                JsonToken.BOOLEAN -> writer.value(reader.nextBoolean())
                JsonToken.NULL -> {
                    reader.nextNull()
                    writer.nullValue()
                }
                else -> reader.skipValue()
            }
        }

        private fun unquote(json: String): String? = if (json.startsWith("\"")) {
            // A top-level string is only accepted in lenient mode.
            JsonReader(json.reader()).use { reader ->
                reader.isLenient = true
                reader.nextString()
            }
        } else {
            null
        }

        private fun quote(text: String): String {
            val out = StringWriter()
            JsonWriter(out).use { writer ->
                writer.isLenient = true
                writer.value(text)
            }
            return out.toString()
        }
    }
}
