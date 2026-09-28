package com.vpr.screenlate.dictionary.api.imports

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Tag descriptions (Yomitan's `notes`, such as "noun (common) (futsuumeishi)" for `n`) from the `tag_bank_*.json`
 * files of an archive. The engine keeps only tag names, so the descriptions are saved next to the dictionary and the
 * popup shows them when a tag is tapped.
 */
object TagBanks {

    /** Tag name to description; tags without a description are left out. */
    fun read(archive: File): Map<String, String> = ZipFile(archive).use { zip ->
        val notes = mutableMapOf<String, String>()
        zip.entries().asSequence()
            .filter { isTagBank(it.name) }
            .sortedBy { it.name }
            .forEach { entry -> zip.getInputStream(entry).use { parse(it, notes) } }
        notes
    }

    /** As [read], for an archive that can only be streamed (an APK asset). */
    fun read(zip: ZipInputStream): Map<String, String> {
        val notes = mutableMapOf<String, String>()
        generateSequence { zip.nextEntry }.filter { isTagBank(it.name) }.forEach { parse(zip, notes) }
        return notes
    }

    internal fun parse(input: InputStream, into: MutableMap<String, String>) {
        val rows = runCatching { Json.parseToJsonElement(input.readBytes().decodeToString()) }.getOrNull() as? JsonArray
            ?: return
        for (row in rows) {
            val fields = row as? JsonArray ?: continue
            val name = (fields.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: continue
            val notes = (fields.getOrNull(3) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (name.isNotEmpty() && notes.isNotEmpty()) into[name] = notes
        }
    }

    private fun isTagBank(path: String): Boolean = BANK.matches(path.substringAfterLast('/'))

    private val BANK = Regex("""tag_bank_\d+\.json""")
}
