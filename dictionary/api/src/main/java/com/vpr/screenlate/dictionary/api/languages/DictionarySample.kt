package com.vpr.screenlate.dictionary.api.languages

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Text taken from a dictionary to tell its languages: the headwords and the definitions. */
data class DictionarySample(val headwords: String, val definitions: String) {

    companion object {
        /**
         * Samples the first rows of an archive's first term bank (headwords and glossaries), else of its kanji bank
         * (characters with their readings, and meanings), else of its term meta bank (headwords only).
         */
        fun of(archive: File): DictionarySample = ZipFile(archive).use { zip ->
            fun firstBank(prefix: String) = zip.entries().asSequence()
                .filter { BANK.matchEntire(it.name.substringAfterLast('/'))?.groupValues?.get(1) == prefix }
                .minByOrNull { it.name.substringAfterLast('_').substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
            val headwords = StringBuilder()
            val definitions = StringBuilder()
            val term = firstBank("term")
            val kanji = firstBank("kanji")
            val meta = firstBank("term_meta")
            when {
                term != null -> zip.getInputStream(term).use { firstRows(it) }.forEach { row ->
                    row.string(0)?.let { headwords.append(it).append(' ') }
                    row.string(1)?.let { headwords.append(it).append(' ') }
                    row.getOrNull(5)?.let { glossaryText(it, definitions) }
                }
                kanji != null -> zip.getInputStream(kanji).use { firstRows(it) }.forEach { row ->
                    (0..2).forEach { index -> row.string(index)?.let { headwords.append(it).append(' ') } }
                    row.getOrNull(4)?.let { glossaryText(it, definitions) }
                }
                meta != null -> zip.getInputStream(meta).use { firstRows(it) }.forEach { row ->
                    row.string(0)?.let { headwords.append(it).append(' ') }
                }
            }
            DictionarySample(headwords.toString(), definitions.toString())
        }

        /**
         * The first [maxRows] rows of a bank (a JSON array of arrays), read without loading the whole file: large
         * banks take tens of megabytes. Stops early at [maxBytes], dropping a row that does not end by then.
         */
        internal fun firstRows(input: InputStream, maxRows: Int = MAX_ROWS, maxBytes: Int = MAX_BYTES): List<JsonArray> {
            val bytes = ByteArrayOutputStream()
            val stream = input.buffered()
            var depth = 0
            var inString = false
            var escaped = false
            var rows = 0
            var lastRowEnd = -1
            while (bytes.size() < maxBytes && rows < maxRows) {
                val byte = stream.read()
                if (byte < 0) break
                bytes.write(byte)
                if (inString) {
                    when {
                        escaped -> escaped = false
                        byte == '\\'.code -> escaped = true
                        byte == '"'.code -> inString = false
                    }
                    continue
                }
                when (byte) {
                    '"'.code -> inString = true
                    '['.code, '{'.code -> depth++
                    ']'.code, '}'.code -> {
                        depth--
                        if (depth == 1) {
                            rows++
                            lastRowEnd = bytes.size()
                        }
                        if (depth == 0) break
                    }
                }
            }
            if (lastRowEnd < 0) return emptyList()
            val text = bytes.toByteArray().copyOf(lastRowEnd).decodeToString() + "]"
            val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
            return parsed.filterIsInstance<JsonArray>()
        }

        /**
         * Appends the readable text of a Yomitan glossary: strings, `text` items and structured content, without
         * furigana (`rt`), images or attributes.
         */
        internal fun glossaryText(element: JsonElement, out: StringBuilder) {
            when (element) {
                is JsonPrimitive -> if (element.isString) out.append(element.content).append(' ')
                is JsonArray -> element.forEach { glossaryText(it, out) }
                is JsonObject -> {
                    val tag = (element["tag"] as? JsonPrimitive)?.contentOrNull
                    val type = (element["type"] as? JsonPrimitive)?.contentOrNull
                    if (tag in SKIPPED_TAGS || type == "image") return
                    element["text"]?.let { glossaryText(it, out) }
                    element["content"]?.let { glossaryText(it, out) }
                }
            }
        }

        private fun JsonArray.string(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.contentOrNull

        private val BANK = Regex("""(term|term_meta|kanji)_bank_\d+\.json""")
        private val SKIPPED_TAGS = setOf("rt", "rp", "img")
        private const val MAX_ROWS = 300
        private const val MAX_BYTES = 2 shl 20
    }
}
