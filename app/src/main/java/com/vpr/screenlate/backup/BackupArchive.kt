package com.vpr.screenlate.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** The zip archive of a backup (see [BackupLayout]); streams only, so it works on any document the user picks. */
object BackupArchive {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @property fonts font files and their list, stored under their own names.
     * @property dictionaryFiles directories of the dictionaries whose files go in, by index in [dictionaries].
     */
    class Contents(
        val manifest: BackupManifest,
        val settings: JsonObject,
        val dictionaries: BackupDictionaryList,
        val fonts: List<File> = emptyList(),
        val dictionaryFiles: Map<Int, File> = emptyMap(),
    )

    /** What is read before any file: enough to offer the sections. */
    class Head(val manifest: BackupManifest, val settings: JsonObject, val dictionaries: BackupDictionaryList)

    class NotBackupException : IOException("Not a backup")

    class NewerFormatException : IOException("Backup from a newer version")

    /** Bytes the dictionary files of [contents] take, for progress. */
    fun size(contents: Contents): Long =
        contents.dictionaryFiles.values.sumOf { directory -> directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    /** Writes [contents]; [onProgress] gets the bytes of dictionary files written so far. */
    fun write(output: OutputStream, contents: Contents, onProgress: (Long) -> Unit = {}) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putText(BackupLayout.MANIFEST, json.encodeToString(BackupManifest.serializer(), contents.manifest))
            zip.putText(BackupLayout.SETTINGS, contents.settings.toString())
            zip.putText(BackupLayout.DICTIONARIES, json.encodeToString(BackupDictionaryList.serializer(), contents.dictionaries))
            contents.fonts.forEach { zip.putFile(BackupLayout.FONTS + it.name, it) }
            // Dictionary files are large and partly compressed already; speed matters more than size here.
            zip.setLevel(Deflater.BEST_SPEED)
            var written = 0L
            for ((index, directory) in contents.dictionaryFiles.toSortedMap()) {
                directory.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                    val path = file.relativeTo(directory).invariantSeparatorsPath
                    zip.putFile("${BackupLayout.DICTIONARY_FILES}$index/$path", file)
                    written += file.length()
                    onProgress(written)
                }
            }
        }
    }

    /** Reads the manifest, settings and dictionary list, which come first. */
    fun readHead(input: InputStream): Head {
        val zip = ZipInputStream(input.buffered())
        var manifest: BackupManifest? = null
        var settings: JsonObject? = null
        var dictionaries: BackupDictionaryList? = null
        while (true) {
            val entry = zip.nextEntry ?: break
            when (entry.name) {
                BackupLayout.MANIFEST -> manifest = decodeManifest(zip.readText())
                BackupLayout.SETTINGS -> settings = json.parseToJsonElement(zip.readText()) as? JsonObject
                BackupLayout.DICTIONARIES -> dictionaries = json.decodeFromString(BackupDictionaryList.serializer(), zip.readText())
                else -> break
            }
            if (manifest == null) throw NotBackupException()
        }
        val found = manifest ?: throw NotBackupException()
        if (found.format > BackupLayout.FORMAT) throw NewerFormatException()
        return Head(found, settings ?: JsonObject(emptyMap()), dictionaries ?: BackupDictionaryList(emptyList()))
    }

    /** Receives the files of a backup while it is read. */
    interface Reader {
        suspend fun font(name: String, input: InputStream)

        /** Every file of dictionary [index] comes before the next dictionary's. */
        suspend fun dictionaryFile(index: Int, path: String, input: InputStream)

        /** Called once after the last file of dictionary [index]. */
        suspend fun dictionaryDone(index: Int)
    }

    /**
     * Streams the files to [reader]; [onHead] gets the [Head] before the first file. Without [dictionaryFiles] reading
     * stops where they start, which saves reading gigabytes when only settings are restored.
     */
    suspend fun read(
        input: InputStream,
        reader: Reader,
        dictionaryFiles: Boolean = true,
        onHead: suspend (Head) -> Unit = {},
    ): Head {
        val zip = ZipInputStream(input.buffered())
        var manifest: BackupManifest? = null
        var settings: JsonObject? = null
        var dictionaries: BackupDictionaryList? = null
        var current: Int? = null
        val done = mutableSetOf<Int>()
        var head: Head? = null
        suspend fun head(): Head = head ?: Head(
            manifest ?: throw NotBackupException(),
            settings ?: JsonObject(emptyMap()),
            dictionaries ?: BackupDictionaryList(emptyList()),
        ).also {
            head = it
            onHead(it)
        }
        suspend fun finish() {
            current?.let {
                reader.dictionaryDone(it)
                done += it
            }
            current = null
        }
        while (true) {
            val entry = zip.nextEntry ?: break
            if (manifest == null && entry.name != BackupLayout.MANIFEST) throw NotBackupException()
            when (entry.name) {
                BackupLayout.MANIFEST -> {
                    manifest = decodeManifest(zip.readText())
                    if (manifest.format > BackupLayout.FORMAT) throw NewerFormatException()
                }
                BackupLayout.SETTINGS -> settings = json.parseToJsonElement(zip.readText()) as? JsonObject
                BackupLayout.DICTIONARIES -> dictionaries = json.decodeFromString(BackupDictionaryList.serializer(), zip.readText())
                else -> {
                    head()
                    if (entry.isDirectory) continue
                    BackupLayout.fontFile(entry.name)?.let { name ->
                        reader.font(name, zip)
                        continue
                    }
                    val (index, path) = BackupLayout.dictionaryFile(entry.name) ?: continue
                    if (!dictionaryFiles) break
                    if (index in done) continue
                    if (index != current) {
                        finish()
                        current = index
                    }
                    reader.dictionaryFile(index, path, zip)
                }
            }
        }
        val found = head()
        finish()
        return found
    }

    /** Sections this version does not know (a newer version's) are left out rather than refusing the backup. */
    private fun decodeManifest(text: String): BackupManifest = runCatching {
        val stored = json.parseToJsonElement(text).jsonObject
        val known = BackupSection.entries.map { it.name }.toSet()
        val sections = stored["sections"]?.jsonArray?.filter { (it as? JsonPrimitive)?.content in known }
        json.decodeFromJsonElement(
            BackupManifest.serializer(),
            if (sections == null) stored else JsonObject(stored + ("sections" to JsonArray(sections))),
        )
    }.getOrElse { throw NotBackupException() }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipInputStream.readText(): String = readBytes().decodeToString()
}
