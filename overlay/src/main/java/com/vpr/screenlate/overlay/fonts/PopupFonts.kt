package com.vpr.screenlate.overlay.fonts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
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

    data object TooLarge : FontImport
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

    /** Adds a font file chosen by the user; a font with the same family added before is replaced. */
    suspend fun import(uri: Uri): FontImport = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_FILE_BYTES) return@withContext FontImport.TooLarge
            }
            out.toByteArray()
        } ?: return@withContext FontImport.NotAFont
        val format = FontFiles.format(bytes) ?: return@withContext FontImport.NotAFont
        val family = FontFiles.family(bytes) ?: displayName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NAME
        lock.withLock {
            val existing = mutableInstalled.value.firstOrNull { it.catalogId == null && it.family == family }
            val id = existing?.id ?: "file-${UUID.randomUUID().toString().take(ID_LENGTH)}"
            existing?.files?.forEach { File(directory, it.name).delete() }
            directory.mkdirs()
            val name = "$id.${format.extension}"
            File(directory, name).writeBytes(bytes)
            val font = InstalledFont(id, family, listOf(FontFile(name)))
            save(mutableInstalled.value.filter { it.id != id } + font)
            FontImport.Added(font)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val font = mutableInstalled.value.firstOrNull { it.id == id } ?: return@withLock
            font.files.forEach { File(directory, it.name).delete() }
            save(mutableInstalled.value - font)
        }
    }

    fun file(font: InstalledFont): File? = font.files.firstOrNull()?.let { File(directory, it.name) }

    /** The font files and their list, for a backup; empty when no font is installed. */
    fun backupFiles(): List<File> {
        val fonts = mutableInstalled.value
        if (fonts.isEmpty()) return emptyList()
        return listOf(index) + fonts.flatMap { font -> font.files.map { File(directory, it.name) } }.filter { it.exists() }
    }

    /** Replaces the installed fonts with the files in [source] (as [backupFiles] lists them). */
    suspend fun restore(source: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            directory.listFiles()?.forEach { it.deleteRecursively() }
            directory.mkdirs()
            source.listFiles()?.forEach { it.copyTo(File(directory, it.name), overwrite = true) }
            mutableInstalled.value = readIndex()
        }
    }

    private suspend fun fetch(font: CatalogFont) {
        directory.mkdirs()
        val files = font.files.mapIndexed { index, file ->
            val extension = file.url.substringAfterLast('.').lowercase().takeIf { it == "otf" } ?: "ttf"
            val name = "${font.id}-$index.$extension"
            val partial = File(directory, "$name.part")
            client.newCall(Request.Builder().url(file.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val length = response.body.contentLength()
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (length > 0) {
                                val fraction = (index + copied.toFloat() / length) / font.files.size
                                mutableDownloads.update { it + (font.id to FontDownload.Running(fraction)) }
                            }
                        }
                    }
                }
            }
            val head = partial.inputStream().use { it.readNBytesCompat(HEAD) }
            if (FontFiles.format(head) == null) {
                partial.delete()
                throw IOException("Not a font")
            }
            val target = File(directory, name)
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Rename failed")
            FontFile(name, file.weight)
        }
        lock.withLock {
            save(mutableInstalled.value.filter { it.id != font.id } + InstalledFont(font.id, font.family, files, font.id))
        }
    }

    private fun readIndex(): List<InstalledFont> = runCatching {
        if (!index.exists()) return emptyList()
        json.decodeFromString<List<InstalledFont>>(index.readText())
            .filter { font -> font.files.all { File(directory, it.name).exists() } }
    }.onFailure { Log.w(TAG, "Font list unreadable", it.redacted()) }.getOrDefault(emptyList())

    private fun save(fonts: List<InstalledFont>) {
        directory.mkdirs()
        val temporary = File(directory, "$INDEX.tmp")
        temporary.writeText(json.encodeToString(fonts))
        index.delete()
        temporary.renameTo(index)
        mutableInstalled.value = fonts
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(count: Int): ByteArray {
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
    }
}
