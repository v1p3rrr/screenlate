package com.vpr.screenlate.dictionary.api.registry

import android.content.Context
import android.os.Process
import android.os.SystemClock
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
     * Removes directories that no registry entry points to, and what imports cut short by the process dying left
     * behind; see [cleanUpLeftovers].
     */
    fun cleanUp(known: Collection<String>, archivesInUse: Set<String>?) {
        val now = System.currentTimeMillis()
        val processStart = now - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
        cleanUpLeftovers(root, staging, downloads, known, archivesInUse, processStart, now)
    }

    private companion object {
        /** Next to the engine's files, which it does not look at. */
        const val TAG_NOTES = "tag_notes.json"
        val NOTES = MapSerializer(String.serializer(), String.serializer())
    }
}

/** Leftovers younger than this may belong to an import still running; used when the archives in use are unknown. */
private const val STALE_MS = 6 * 60 * 60 * 1000L

/**
 * Deletes dictionary directories in [root] that are not [known], staging directories made before [processStart]
 * (only a running import writes there, and none from an earlier process runs any more), and archives in [downloads]
 * made before it that no queued import reads ([archivesInUse]; when null, only archives older than [STALE_MS] go).
 */
internal fun cleanUpLeftovers(
    root: File,
    staging: File,
    downloads: File,
    known: Collection<String>,
    archivesInUse: Set<String>?,
    processStart: Long,
    now: Long,
) {
    root.listFiles()
        ?.filter { it.isDirectory && it != staging && it.name !in known }
        ?.forEach { it.deleteRecursively() }
    staging.listFiles().orEmpty()
        .filter { it.lastModified() < processStart }
        .forEach { it.deleteRecursively() }
    val archivesBefore = if (archivesInUse == null) minOf(processStart, now - STALE_MS) else processStart
    downloads.listFiles().orEmpty()
        .filter { it.lastModified() < archivesBefore && it.name !in archivesInUse.orEmpty() }
        .forEach { it.deleteRecursively() }
}
