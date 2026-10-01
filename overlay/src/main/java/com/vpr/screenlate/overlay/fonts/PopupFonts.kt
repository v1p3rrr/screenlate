package com.vpr.screenlate.overlay.fonts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface FontDownload {
    /** @param fraction of all files of the font. */
    data class Running(val fraction: Float) : FontDownload

    data object Failed : FontDownload
}

sealed interface FontImport {
    data class Added(val font: InstalledFont) : FontImport

    data object NotAFont : FontImport

    /** A font compressed for websites (WOFF, WOFF2), which Android cannot preview; TTF and OTF files are needed. */
    data object WebFont : FontImport

    data object TooLarge : FontImport

    /** The file could not be read, or the font could not be stored. */
    data object Failed : FontImport
}

/**
 * Fonts for the lookup page: the catalog of freely licensed fonts, downloads, the user's own font files, and the
 * list of installed fonts (kept in `fonts/fonts.json` next to the files).
 */
@Singleton
class PopupFonts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val directory = File(context.filesDir, PageFonts.DIRECTORY)
    private val index = File(directory, INDEX)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val mutableInstalled = MutableStateFlow(readIndex())
    val installed: StateFlow<List<InstalledFont>> = mutableInstalled

    private val mutableDownloads = MutableStateFlow<Map<String, FontDownload>>(emptyMap())

    /** Downloads by catalog id; a finished download disappears from the map. */
    val downloads: StateFlow<Map<String, FontDownload>> = mutableDownloads

    val catalog: List<CatalogFont> by lazy {
        runCatching {
            context.assets.open(CATALOG).use { json.decodeFromString<FontCatalog>(it.readBytes().decodeToString()).fonts }
        }.onFailure { Log.w(TAG, "Font catalog unreadable", it.redacted()) }.getOrDefault(emptyList())
    }

    fun download(font: CatalogFont) {
        if (mutableDownloads.value[font.id] is FontDownload.Running) return
        mutableDownloads.update { it + (font.id to FontDownload.Running(0f)) }
        scope.launch {
            val result = runCatching { fetch(font) }
            result.onFailure { Log.w(TAG, "Font download failed", it.redacted()) }
            mutableDownloads.update { if (result.isSuccess) it - font.id else it + (font.id to FontDownload.Failed) }
        }
    }

    /**
     * Adds a font file chosen by the user as a font of its own, named by its family and style ("Noto Serif JP Bold");
     * a font added from a file of the same name is replaced. The file is copied first and read in place, so a large
     * font is never held in memory.
     */
    suspend fun import(uri: Uri): FontImport = withContext(Dispatchers.IO) {
        val partial = File(directory, "import-${newId()}.part")
        try {
            directory.mkdirs()
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                partial.outputStream().use { output -> copyAtMost(input, output, MAX_FILE_BYTES) }
            } ?: return@withContext FontImport.Failed
            if (!copied) return@withContext FontImport.TooLarge
            val (format, description) = RandomAccessFile(partial, "r").use { file ->
                val buffer = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                val head = ByteArray(minOf(HEAD.toLong(), file.length()).toInt()) { buffer.get(it) }
                FontFiles.format(head) to FontFiles.describe(buffer)
            }
            if (format == null) return@withContext FontImport.NotAFont
            if (format.web) return@withContext FontImport.WebFont
            val family = description?.displayName
                ?: displayName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: DEFAULT_NAME
            lock.withLock {
                val existing = mutableInstalled.value.firstOrNull { it.catalogId == null && it.family == family }
                val id = existing?.id ?: "file-${newId()}"
                // A new file name every time: a page that loaded the replaced file must not keep it from its cache.
                val name = "$id-${newId()}.${format.extension}"
                val target = File(directory, name)
                if (!partial.renameTo(target)) throw IOException("Rename failed")
                val font = InstalledFont(id, family, listOf(FontFile(name, description?.weight ?: DEFAULT_WEIGHT)))
                try {
                    save(mutableInstalled.value.filter { it.id != id } + font)
                } catch (e: IOException) {
                    target.delete()
                    throw e
                }
                // Only once the new font is in the list: a failure above keeps the replaced one working.
                existing?.files?.forEach { File(directory, it.name).delete() }
                FontImport.Added(font)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Font file not readable", e.redacted())
            FontImport.Failed
        } catch (e: Exception) {
            // A malformed file can fail anywhere in reading its tables.
            Log.w(TAG, "Font import failed", e.redacted())
            FontImport.Failed
        } finally {
            partial.delete()
        }
    }

    /** Removes an installed font; its files go once the list without it is stored. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val font = mutableInstalled.value.firstOrNull { it.id == id } ?: return@withLock
            try {
                save(mutableInstalled.value - font)
            } catch (e: IOException) {
                Log.w(TAG, "Font list not stored", e.redacted())
                return@withLock
            }
            font.files.forEach { File(directory, it.name).delete() }
        }
    }

    fun file(file: FontFile): File = File(directory, file.name)

    /** The font files and their list, for a backup; empty when no font is installed. */
    fun backupFiles(): List<File> {
        val fonts = mutableInstalled.value
        if (fonts.isEmpty()) return emptyList()
        return listOf(index) + fonts.flatMap { font -> font.files.map { File(directory, it.name) } }.filter { it.exists() }
    }

    /**
     * Replaces the installed fonts with the files in [source] (as [backupFiles] lists them).
     *
     * @return the number of fonts installed now.
     */
    suspend fun restore(source: File): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            directory.listFiles()?.forEach { it.deleteRecursively() }
            directory.mkdirs()
            try {
                source.listFiles()?.forEach { it.copyTo(File(directory, it.name), overwrite = true) }
            } finally {
                // The old files are gone already: a failed copy must not leave them listed.
                mutableInstalled.value = readIndex()
            }
            mutableInstalled.value.size
        }
    }

    /**
     * Downloads all files of [font]. A font downloaded before keeps its files until the new ones are listed, so a
     * failure leaves it whole; on a failure, the files of this attempt go.
     */
    private suspend fun fetch(font: CatalogFont) {
        directory.mkdirs()
        val written = mutableListOf<File>()
        try {
            val files = font.files.mapIndexed { index, file ->
                val extension = file.url.substringAfterLast('.').lowercase().takeIf { it == "otf" } ?: "ttf"
                // A new file name every time: a page that loaded the replaced file must not keep it from its cache.
                val name = "${font.id}-$index-${newId()}.$extension"
                val partial = File(directory, "$name.part")
                val target = File(directory, name)
                written += partial
                written += target
                download(file.url, partial) { copied, length ->
                    val fraction = (index + copied.toFloat() / length) / font.files.size
                    mutableDownloads.update { it + (font.id to FontDownload.Running(fraction)) }
                }
                val head = partial.inputStream().use { it.readNBytesCompat(HEAD) }
                if (FontFiles.format(head) == null) throw IOException("Not a font")
                if (!partial.renameTo(target)) throw IOException("Rename failed")
                FontFile(name, file.weight)
            }
            lock.withLock {
                val replaced = mutableInstalled.value.filter { it.id == font.id }
                save(mutableInstalled.value - replaced.toSet() + InstalledFont(font.id, font.family, files, font.id))
                replaced.flatMap { it.files }.forEach { File(directory, it.name).delete() }
            }
        } catch (e: Throwable) {
            lock.withLock {
                val listed = mutableInstalled.value.flatMap { it.files }.map { it.name }.toSet()
                written.filter { it.name !in listed }.forEach { it.delete() }
            }
            throw e
        }
    }

    /** Writes [url] into [target]; [progress] gets the bytes so far and the total when the server names it. */
    private fun download(url: String, target: File, progress: (copied: Long, length: Long) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val length = response.body.contentLength()
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (length > 0) progress(copied, length)
                    }
                }
            }
        }
    }

    /**
     * The installed fonts. A restored list is not trusted: a font with a missing file, a file name that is not plain,
     * or no family name is left out, and a weight that is not a CSS weight becomes the normal one.
     */
    private fun readIndex(): List<InstalledFont> = runCatching {
        if (!index.exists()) return emptyList()
        json.decodeFromString<List<InstalledFont>>(index.readText())
            .filter { font -> font.family.isNotBlank() && font.files.isNotEmpty() }
            .filter { font -> font.files.all { FontFiles.isFileName(it.name) && File(directory, it.name).exists() } }
            .map { font ->
                font.copy(files = font.files.map { if (PageFonts.isWeight(it.weight)) it else it.copy(weight = DEFAULT_WEIGHT) })
            }
    }.onFailure { Log.w(TAG, "Font list unreadable", it.redacted()) }.getOrDefault(emptyList())

    private fun save(fonts: List<InstalledFont>) {
        directory.mkdirs()
        val temporary = File(directory, "$INDEX.tmp")
        temporary.writeText(json.encodeToString(fonts))
        // The rename replaces the old list in one step; deleting it first only where the rename cannot replace.
        if (!temporary.renameTo(index)) {
            index.delete()
            temporary.renameTo(index)
        }
        mutableInstalled.value = fonts
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** Copies [input] into [output]; false as soon as more than [limit] bytes come. */
    private fun copyAtMost(input: InputStream, output: OutputStream, limit: Int): Boolean {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > limit) return false
            output.write(buffer, 0, read)
        }
    }

    private fun newId(): String = UUID.randomUUID().toString().take(ID_LENGTH)

    private fun InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var total = 0
        while (total < count) {
            val read = read(buffer, total, count - total)
            if (read < 0) break
            total += read
        }
        return buffer.copyOf(total)
    }

    private companion object {
        const val TAG = "PopupFonts"
        const val INDEX = "fonts.json"
        const val CATALOG = "fonts/catalog.json"
        const val BUFFER = 64 * 1024
        const val HEAD = 12
        const val ID_LENGTH = 8
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
        const val DEFAULT_NAME = "Font"
        const val DEFAULT_WEIGHT = "400"
    }
}
