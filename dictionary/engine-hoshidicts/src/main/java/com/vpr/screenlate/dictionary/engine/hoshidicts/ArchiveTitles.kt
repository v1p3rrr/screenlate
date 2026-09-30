package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.vpr.screenlate.dictionary.api.DictionaryImportException
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The native importer writes a dictionary into `outputDir/<title>` and deletes that directory when the import fails,
 * so a title that leads out of `outputDir` would write or delete other files of the app. Every `index.json` of an
 * archive is checked before the import; an archive that cannot be read here is refused too, since the importer might
 * read it differently.
 */
internal object ArchiveTitles {

    fun check(archive: File) {
        val titles = try {
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filter { it.name == INDEX }.map { entry ->
                    val text = zip.getInputStream(entry).use { it.readBytes() }.decodeToString().removePrefix(BOM)
                    ((Json.parseToJsonElement(text) as? JsonObject)?.get("title") as? JsonPrimitive)?.contentOrNull
                }.toList()
            }
        } catch (e: IOException) {
            throw DictionaryImportException("failed to open zip", e)
        } catch (e: SerializationException) {
            throw DictionaryImportException("failed to parse index.json", e)
        }
        if (titles.any { it != null && !isSafe(it) }) throw DictionaryImportException("invalid dictionary title")
    }

    /** False for a title with a `..` segment or an absolute one: the native path join replaces its left side then. */
    fun isSafe(title: String): Boolean = !title.startsWith('/') && title.split('/').none { it == ".." }

    private const val INDEX = "index.json"
    private const val BOM = "\uFEFF"
}
