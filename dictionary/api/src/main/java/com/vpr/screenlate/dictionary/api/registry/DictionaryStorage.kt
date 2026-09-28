package com.vpr.screenlate.dictionary.api.registry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * File layout of imported dictionaries: `filesDir/dictionaries/<uuid>/` per dictionary, and a staging area on
 * the same file system so a finished import is moved into place with a rename.
 */
@Singleton
class DictionaryStorage @Inject constructor(@ApplicationContext context: Context) {
    val root: File = File(context.filesDir, "dictionaries")
    private val staging: File = File(root, ".staging")
    private val downloads: File = File(context.cacheDir, "dictionary-downloads")

    fun directoryOf(dictionary: DictionaryEntity): File = File(root, dictionary.directory)

    fun hasFiles(dictionary: DictionaryEntity): Boolean = directoryOf(dictionary).list()?.isNotEmpty() == true

    /** Whether the tag descriptions of [dictionary] were saved, possibly as none (see `TagBanks`). */
    fun hasTagNotes(dictionary: DictionaryEntity): Boolean = File(directoryOf(dictionary), TAG_NOTES).exists()

    /** Tag name to description; empty when the dictionary has none or was imported before they were kept. */
    fun tagNotes(dictionary: DictionaryEntity): Map<String, String> {
        val file = File(directoryOf(dictionary), TAG_NOTES)
        if (!file.exists()) return emptyMap()
        return runCatching { Json.decodeFromString(NOTES, file.readText()) }.getOrDefault(emptyMap())
    }

    /** Saves tag descriptions into a dictionary [directory], also when there are none. */
    fun writeTagNotes(directory: File, notes: Map<String, String>) {
        File(directory, TAG_NOTES).writeText(Json.encodeToString(NOTES, notes))
    }

    /** A fresh empty directory for one import; delete it when done. */
    fun newStagingDirectory(): File = File(staging, UUID.randomUUID().toString()).apply { mkdirs() }

    /** A fresh file for a downloaded or copied archive; delete it when done. */
    fun newArchiveFile(): File = File(downloads.apply { mkdirs() }, "${UUID.randomUUID()}.zip")

    /** Moves an imported dictionary directory into the storage root and returns the new directory name. */
    fun adopt(imported: File): String {
        val name = UUID.randomUUID().toString()
        val target = File(root, name)
        root.mkdirs()
        if (!imported.renameTo(target)) {
            imported.copyRecursively(target, overwrite = true)
            imported.deleteRecursively()
        }
        return name
    }

    /**
     * Removes directories that no registry entry points to, and staging or download leftovers older than
     * [STALE_MS] (younger ones may belong to an import that is still running).
     */
    fun cleanUp(known: Collection<String>) {
        val staleBefore = System.currentTimeMillis() - STALE_MS
        root.listFiles()
            ?.filter { it.isDirectory && it != staging && it.name !in known }
            ?.forEach { it.deleteRecursively() }
        listOf(staging, downloads).flatMap { it.listFiles().orEmpty().asList() }
            .filter { it.lastModified() < staleBefore }
            .forEach { it.deleteRecursively() }
    }

    private companion object {
        const val STALE_MS = 6 * 60 * 60 * 1000L

        /** Next to the engine's files, which it does not look at. */
        const val TAG_NOTES = "tag_notes.json"
        val NOTES = MapSerializer(String.serializer(), String.serializer())
    }
}
