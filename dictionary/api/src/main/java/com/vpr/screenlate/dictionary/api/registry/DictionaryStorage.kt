package com.vpr.screenlate.dictionary.api.registry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
    }
}
