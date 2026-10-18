package com.vpr.screenlate.core.ocr.model

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.vpr.screenlate.diagnostics.Diag
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
 * [file] is the path a download that is not a zip archive is kept under, [OcrModelStore.MODEL_FILE] without it.
 */
data class OcrModelSpec(val id: String, val version: String, val engine: String, val sha256: String, val file: String? = null)

/** An installed model: its files lie in [directory], named as in the downloaded archive. */
data class InstalledOcrModel(val id: String, val version: String, val engine: String, val directory: File) {
    fun file(name: String): File = File(directory, name)

    /** Bytes the model takes on disk. */
    val bytes: Long get() = directory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** The model's own files, without the store's description of it; what [OcrModelStore.restore] takes back. */
    val files: List<File>
        get() = directory.walkTopDown().filter { it.isFile && it != File(directory, OcrModelStore.MANIFEST) }.sortedBy { it.path }.toList()
}

/** The downloaded archive is not the file the catalog pinned. */
class ModelChecksumException : IOException("The downloaded model does not match its checksum")

/**
 * Downloaded OCR models, one directory per model under [root]. A download is installed only when its SHA-256 matches
 * the pinned one; a zip archive is unpacked, any other file is kept as it is under its [OcrModelSpec.file] name. A new
 * version replaces the old one.
 *
 * The manifest records each file's size and SHA-256. A model whose files are gone or cut short, or changed when
 * [verify] reads them, is [damaged] and counts as not installed.
 */
@Singleton
class OcrModelStore internal constructor(private val root: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, DIRECTORY))

    private val mutex = Mutex()

    /** Guards [holds] and the deletion of held models. */
    private val lock = Any()
    private val holds = HashMap<String, Int>()
    private val installed = MutableStateFlow<List<InstalledOcrModel>>(emptyList())
    private val broken = MutableStateFlow<List<InstalledOcrModel>>(emptyList())

    init {
        // Deleted during a scan that never ended: the process was gone before the model was released.
        root.listFiles().orEmpty().filter { File(it, DELETED).exists() }.forEach { it.deleteRecursively() }
        scan()
    }

    /** The installed models. */
    val models: StateFlow<List<InstalledOcrModel>> = installed.asStateFlow()

    /** Models whose files are gone, cut short or changed; downloading one again replaces it. */
    val damaged: StateFlow<List<InstalledOcrModel>> = broken.asStateFlow()

    fun model(id: String): InstalledOcrModel? = installed.value.firstOrNull { it.id == id }

    /** Whether [spec]'s version of the model is installed. */
    fun isInstalled(id: String, version: String): Boolean = model(id)?.version == version

    /** Checks [archive] against [spec] and installs it in place of an installed version; [archive] stays. */
    suspend fun install(spec: OcrModelSpec, archive: File): InstalledOcrModel = withContext(Dispatchers.IO) {
        require(spec.id.isSafeName()) { "Bad model id" }
        val name = spec.file ?: MODEL_FILE
        require(name.split('/').all { it.isSafeName() }) { "Bad model file name" }
        val started = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        if (!sha256(archive).equals(spec.sha256, ignoreCase = true)) {
            Diag.event("model_install", "model" to spec.id, "error" to "checksum", "archive_kb" to (archive.length() shr 10))
            throw ModelChecksumException()
        }
        val checked = SystemClock.elapsedRealtime()
        mutex.withLock {
            val locked = SystemClock.elapsedRealtime()
            root.mkdirs()
            val staging = File(root, ".${spec.id}.new").apply { deleteRecursively() }
            try {
                staging.mkdirs()
                if (isZip(archive)) unzip(archive, staging) else archive.copyTo(File(staging, name))
                place(spec.id, spec.version, spec.engine, staging).also {
                    // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
                    Diag.event(
                        "model_install", "model" to spec.id, "version" to spec.version, "engine" to spec.engine,
                        "archive_kb" to (archive.length() shr 10), "sha_ms" to checked - started, "wait_ms" to locked - checked,
                        "place_ms" to SystemClock.elapsedRealtime() - locked,
                    )
                }
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /** A new empty directory inside the store, for [restore]. */
    fun newStagingDirectory(): File = File(root, ".restore-${UUID.randomUUID()}").apply { mkdirs() }

    /**
     * Installs the files in [staging] (from [newStagingDirectory], laid out as [InstalledOcrModel.files] gave them) as
     * the model [id] in place of an installed version. For a backup, which has no checksum to check; [staging] is
     * moved or deleted.
     */
    suspend fun restore(id: String, version: String, engine: String, staging: File): InstalledOcrModel = withContext(Dispatchers.IO) {
        try {
            require(id.isSafeName()) { "Bad model id" }
            mutex.withLock { place(id, version, engine, staging) }
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Describes the model in [staging] and swaps it in for [id]'s directory. */
    private fun place(id: String, version: String, engine: String, staging: File): InstalledOcrModel {
        Properties().apply {
            setProperty(KEY_ID, id)
            setProperty(KEY_VERSION, version)
            setProperty(KEY_ENGINE, engine)
            record(staging)
        }.let { manifest -> File(staging, MANIFEST).outputStream().use { manifest.store(it, null) } }
        val target = File(root, id)
        val old = File(root, ".$id.old").apply { deleteRecursively() }
        if (target.exists() && !target.renameTo(old)) throw IOException("Cannot replace the model")
        if (!staging.renameTo(target)) {
            old.renameTo(target)
            throw IOException("Cannot install the model")
        }
        old.deleteRecursively()
        Log.i(TAG, "Model $id $version installed for $engine")
        scan()
        return requireNotNull(model(id))
    }

    /**
     * Keeps the installed models [ids] from being deleted until the returned handle is closed; a deletion meanwhile
     * takes them out of [models] at once and deletes their files at the close. Null when one of them is not installed.
     */
    fun hold(ids: Collection<String>): AutoCloseable? = synchronized(lock) {
        val present = installed.value.mapTo(hashSetOf()) { it.id }
        if (!present.containsAll(ids)) return null
        ids.forEach { holds[it] = (holds[it] ?: 0) + 1 }
        val closed = AtomicBoolean(false)
        AutoCloseable { if (closed.compareAndSet(false, true)) release(ids) }
    }

    private fun release(ids: Collection<String>) = synchronized(lock) {
        ids.forEach { id ->
            val left = (holds[id] ?: 1) - 1
            if (left > 0) {
                holds[id] = left
            } else {
                holds -= id
                val directory = File(root, id)
                if (File(directory, DELETED).exists() && directory.deleteRecursively()) Log.i(TAG, "Model $id deleted after its scan")
            }
        }
    }

    /** Deletes the models [ids]; unknown ids are skipped. A held model ([hold]) goes when it is released. */
    suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ids.filter { it.isSafeName() }.forEach { id ->
                val directory = File(root, id)
                if (!directory.exists()) return@forEach
                synchronized(lock) {
                    if ((holds[id] ?: 0) > 0) {
                        File(directory, DELETED).createNewFile()
                        Log.i(TAG, "Model $id deleted once its scan ends")
                    } else if (directory.deleteRecursively()) {
                        Log.i(TAG, "Model $id deleted")
                    }
                }
            }
            scan()
        }
    }

    /**
     * Checks again whether each model's files are there with their sizes; records the files of a model installed
     * before the store recorded them, as they are now.
     */
    suspend fun recheck() = withContext(Dispatchers.IO) {
        mutex.withLock {
            directories().forEach { directory ->
                val properties = manifest(directory) ?: return@forEach
                if (recorded(properties).isEmpty()) {
                    properties.record(directory)
                    write(directory, properties)
                }
            }
            scan()
        }
    }

    /**
     * Reads the files of the models [ids] and marks a model [damaged] when one differs from what was installed;
     * for an engine that failed to read a model. True when one was damaged.
     */
    suspend fun verify(ids: Collection<String>): Boolean = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        mutex.withLock {
            val damaged = ids.filter { it.isSafeName() }.mapNotNull { id -> File(root, id).takeIf { it.isDirectory } }.filter { directory ->
                val properties = manifest(directory) ?: return@filter false
                val intact = recorded(properties).all { (path, file) ->
                    File(directory, path).let { it.isFile && it.length() == file.size && sha256(it).equals(file.sha256, ignoreCase = true) }
                }
                if (!intact) {
                    properties.setProperty(KEY_DAMAGED, true.toString())
                    write(directory, properties)
                    Log.w(TAG, "Model ${directory.name} is damaged")
                }
                !intact
            }
            scan()
            Diag.event("model_verify", "models" to ids.toList(), "damaged" to damaged.map { it.name }, "ms" to SystemClock.elapsedRealtime() - started) // TEMPORARY DIAGNOSTICS BUILD
            damaged.isNotEmpty()
        }
    }

    /** Reads every model into [models] and [damaged]; a model deleted during a scan is in neither. */
    private fun scan() {
        val healthy = mutableListOf<InstalledOcrModel>()
        val damaged = mutableListOf<InstalledOcrModel>()
        directories().forEach { directory ->
            if (File(directory, DELETED).exists()) return@forEach
            val properties = manifest(directory) ?: return@forEach
            val model = InstalledOcrModel(
                id = properties.getProperty(KEY_ID) ?: return@forEach,
                version = properties.getProperty(KEY_VERSION).orEmpty(),
                engine = properties.getProperty(KEY_ENGINE).orEmpty(),
                directory = directory,
            )
            val whole = properties.getProperty(KEY_DAMAGED) != true.toString() &&
                recorded(properties).all { (path, file) -> File(directory, path).let { it.isFile && it.length() == file.size } }
            if (whole) healthy += model else damaged += model
        }
        installed.value = healthy.sortedBy { it.id }
        broken.value = damaged.sortedBy { it.id }
    }

    private fun directories(): List<File> = root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith('.') }

    private fun manifest(directory: File): Properties? {
        val manifest = File(directory, MANIFEST).takeIf { it.isFile } ?: return null
        return runCatching { Properties().apply { manifest.inputStream().use(::load) } }.getOrNull()
    }

    private fun write(directory: File, properties: Properties) {
        File(directory, MANIFEST).outputStream().use { properties.store(it, null) }
    }

    /** The size and SHA-256 of each file of the model in [directory], under its path inside it. */
    private fun Properties.record(directory: File) {
        directory.walkTopDown().filter { it.isFile && it.name != MANIFEST && it.name != DELETED }.forEach { file ->
            setProperty(FILE_PREFIX + file.relativeTo(directory).invariantSeparatorsPath, "${file.length()}:${sha256(file)}")
        }
    }

    private fun recorded(properties: Properties): Map<String, RecordedFile> =
        properties.stringPropertyNames().filter { it.startsWith(FILE_PREFIX) }.mapNotNull { key ->
            val (size, hash) = properties.getProperty(key).split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            key.removePrefix(FILE_PREFIX) to RecordedFile(size.toLongOrNull() ?: return@mapNotNull null, hash)
        }.toMap()

    private data class RecordedFile(val size: Long, val sha256: String)

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
        internal const val MANIFEST = "model.properties"

        /** The name a model that is not a zip archive gets when its spec names no file. */
        const val MODEL_FILE = "model.bin"

        private const val KEY_ID = "id"
        private const val KEY_VERSION = "version"
        private const val KEY_ENGINE = "engine"
        private const val KEY_DAMAGED = "damaged"

        /** A file of the model: `file.<path>=<size>:<sha256>`. */
        private const val FILE_PREFIX = "file."

        /** Marks a model deleted while a scan held it; [scan] skips it, the release or the next start deletes it. */
        private const val DELETED = ".deleted"
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
