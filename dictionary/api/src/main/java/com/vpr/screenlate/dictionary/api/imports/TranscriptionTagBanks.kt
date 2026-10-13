package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.dictionary.api.registry.TranscriptionTags
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * The tags of the phonetic transcriptions in an archive's `term_meta_bank_*.json` files: rows
 * `[term, "ipa", {"reading", "transcriptions": [{"ipa", "tags"}]}]`. Other rows are skipped without being parsed.
 */
object TranscriptionTagBanks {

    /** The tags found, or null when no transcription has any. */
    fun read(archive: File): TranscriptionTags? = ZipFile(archive).use { zip ->
        val builder = TranscriptionTags.Builder()
        zip.entries().asSequence()
            .filter { BANK.matches(it.name.substringAfterLast('/')) }
            .sortedBy { it.name }
            .forEach { entry -> zip.getInputStream(entry).use { parse(it, builder) } }
        builder.takeUnless { it.isEmpty }?.build()
    }

    internal fun parse(input: InputStream, into: TranscriptionTags.Builder) {
        val scanner = RawJsonScanner(input)
        scanner.beginArray()
        while (scanner.hasNext()) {
            if (scanner.peek() != ARRAY_START) {
                scanner.skipValue()
                continue
            }
            scanner.beginArray()
            val term = if (scanner.hasNext()) stringOrSkip(scanner) else null
            val mode = if (scanner.hasNext()) stringOrSkip(scanner) else null
            if (term != null && mode == IPA && scanner.hasNext()) readData(scanner, term, into)
            while (scanner.hasNext()) scanner.skipValue()
            scanner.endArray()
        }
        scanner.endArray()
    }

    private fun readData(scanner: RawJsonScanner, term: String, into: TranscriptionTags.Builder) {
        if (scanner.peek() != OBJECT_START) return scanner.skipValue()
        scanner.beginObject()
        while (scanner.hasNext()) {
            if (scanner.nextName() == "transcriptions" && scanner.peek() == ARRAY_START) {
                scanner.beginArray()
                while (scanner.hasNext()) readTranscription(scanner, term, into)
                scanner.endArray()
            } else {
                scanner.skipValue()
            }
        }
        scanner.endObject()
    }

    private fun readTranscription(scanner: RawJsonScanner, term: String, into: TranscriptionTags.Builder) {
        if (scanner.peek() != OBJECT_START) return scanner.skipValue()
        var ipa: String? = null
        val tags = mutableListOf<String>()
        scanner.beginObject()
        while (scanner.hasNext()) {
            when (scanner.nextName()) {
                "ipa" -> ipa = stringOrSkip(scanner)
                "tags" -> if (scanner.peek() == ARRAY_START) {
                    scanner.beginArray()
                    while (scanner.hasNext()) stringOrSkip(scanner)?.let(tags::add)
                    scanner.endArray()
                } else {
                    scanner.skipValue()
                }
                else -> scanner.skipValue()
            }
        }
        scanner.endObject()
        if (ipa != null) into.add(term, ipa, tags)
    }

    private fun stringOrSkip(scanner: RawJsonScanner): String? =
        if (scanner.peek() == QUOTE) scanner.nextString() else null.also { scanner.skipValue() }

    private const val IPA = "ipa"
    private const val QUOTE = '"'.code.toByte()
    private const val OBJECT_START = '{'.code.toByte()
    private const val ARRAY_START = '['.code.toByte()
    private val BANK = Regex("""term_meta_bank_\d+\.json""")
}
