package com.vpr.screenlate.core.ocr.model

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A model to install: [id] names it in the store (several catalog entries may share one model, such as a text detector
 * used by many languages), [engine] is the [ModelOcrEngine] that reads it, [sha256] the archive's pinned checksum.
 */
data class OcrModelSpec(val id: String, val version: String, val engine: String, val sha256: String)

/** An installed model: its files lie in [directory], named as in the downloaded archive. */
data class InstalledOcrModel(val id: String, val version: String, val engine: String, val directory: File) {
    fun file(name: String): File = File(directory, name)

    /** Bytes the model takes on disk. */
    val bytes: Long get() = directory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

/** The downloaded archive is not the file the catalog pinned. */
class ModelChecksumException : IOException("The downloaded model does not match its checksum")

/**
 * Downloaded OCR models, one directory per model under [root]. A download is installed only when its SHA-256 matches
 * the pinned one; a zip archive is unpacked, any other file is kept as it is. A new version replaces the old one.
 */
@Singleton
class OcrModelStore internal constructor(private val root: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, DIRECTORY))

    private val mutex = Mutex()
    private val installed = MutableStateFlow(read())

    /** The installed models. */
    val models: StateFlow<List<InstalledOcrModel>> = installed.asStateFlow()

    fun model(id: String): InstalledOcrModel? = installed.value.firstOrNull { it.id == id }

    /** Whether [spec]'s version of the model is installed. */
    fun isInstalled(id: String, version: String): Boolean = model(id)?.version == version

    /** Checks [archive] against [spec] and installs it in place of an installed version; [archive] stays. */
    suspend fun install(spec: OcrModelSpec, archive: File): InstalledOcrModel = withContext(Dispatchers.IO) {
        require(spec.id.isSafeName()) { "Bad model id" }
        if (!sha256(archive).equals(spec.sha256, ignoreCase = true)) throw ModelChecksumException()
        mutex.withLock {
            root.mkdirs()
            val staging = File(root, ".${spec.id}.new").apply { deleteRecursively() }
            try {
                staging.mkdirs()
                if (isZip(archive)) unzip(archive, staging) else archive.copyTo(File(staging, MODEL_FILE))
                Properties().apply {
                    setProperty(KEY_ID, spec.id)
                    setProperty(KEY_VERSION, spec.version)
                    setProperty(KEY_ENGINE, spec.engine)
                }.let { manifest -> File(staging, MANIFEST).outputStream().use { manifest.store(it, null) } }
                val target = File(root, spec.id)
                val old = File(root, ".${spec.id}.old").apply { deleteRecursively() }
                if (target.exists() && !target.renameTo(old)) throw IOException("Cannot replace the model")
                if (!staging.renameTo(target)) {
                    old.renameTo(target)
                    throw IOException("Cannot install the model")
                }
                old.deleteRecursively()
                Log.i(TAG, "Model ${spec.id} ${spec.version} installed for ${spec.engine}")
                installed.value = read()
                requireNotNull(model(spec.id))
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /** Deletes the models [ids]; unknown ids are skipped. */
    suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ids.filter { it.isSafeName() }.forEach { id ->
                if (File(root, id).deleteRecursively()) Log.i(TAG, "Model $id deleted")
            }
            installed.value = read()
        }
    }

    private fun read(): List<InstalledOcrModel> =
        root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith('.') }.mapNotNull { directory ->
            val manifest = File(directory, MANIFEST).takeIf { it.isFile } ?: return@mapNotNull null
            val properties = runCatching { Properties().apply { manifest.inputStream().use(::load) } }.getOrNull()
                ?: return@mapNotNull null
            InstalledOcrModel(
                id = properties.getProperty(KEY_ID) ?: return@mapNotNull null,
                version = properties.getProperty(KEY_VERSION).orEmpty(),
                engine = properties.getProperty(KEY_ENGINE).orEmpty(),
                directory = directory,
            )
        }.sortedBy { it.id }

    /** Entries go only below [target]: names with `..` or absolute paths are refused. */
    private fun unzip(archive: File, target: File) {
        val base = target.canonicalFile
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = File(target, entry.name).canonicalFile
                if (!file.path.startsWith(base.path + File.separator)) throw IOException("Bad entry in the model archive")
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private fun isZip(file: File): Boolean = file.inputStream().use { input ->
        val head = ByteArray(ZIP_MAGIC.size)
        input.read(head) == head.size && head.contentEquals(ZIP_MAGIC)
    }

    private fun String.isSafeName() = isNotEmpty() && all { it.isLetterOrDigit() || it in "-_." } && !startsWith('.')

    companion object {
        private const val TAG = "OcrModelStore"
        private const val DIRECTORY = "ocr-models"
        private const val MANIFEST = "model.properties"

        /** The name a model that is not a zip archive gets. */
        const val MODEL_FILE = "model.bin"

        private const val KEY_ID = "id"
        private const val KEY_VERSION = "version"
        private const val KEY_ENGINE = "engine"
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
