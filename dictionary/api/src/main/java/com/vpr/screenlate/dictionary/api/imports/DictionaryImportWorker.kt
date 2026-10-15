package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.dictionary.api.languages.InstalledLanguages
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.R
import com.vpr.screenlate.dictionary.api.catalog.ASSET_URL_PREFIX
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Imports dictionaries in the background: the archives bundled in the APK, a local archive file, or an
 * archive downloaded from a URL; builds the dictionaries the catalog has as source files ([CatalogEntry.build]); also
 * downloads on-device recognition models of the catalog. Runs as a foreground `dataSync` job so the system does not
 * stop a long import.
 */
@HiltWorker
class DictionaryImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DictionaryRepository,
    private val storage: DictionaryStorage,
    private val bundled: BundledDictionaries,
    private val catalog: DictionaryCatalog,
    private val installedLanguages: InstalledLanguages,
    private val imports: DictionaryImports,
    private val profiles: LanguageProfiles,
    private val models: ModelInstaller,
    httpClient: OkHttpClient,
) : CoroutineWorker(context, params) {
    private val downloadClient = httpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Languages for dictionaries whose index.json names none. */
    private val catalogEntries: List<CatalogEntry> by lazy { catalog.local() }

    /**
     * Whether this run is counted as one the app did not die in ([DictionaryImports.markStopped]): set while it
     * downloads, so that a death then does not count towards [MAX_RUN_ATTEMPTS], and kept when it ends for a retry.
     */
    private var uncounted = false

    override suspend fun doWork(): Result = imports.tracked {
        val source = inputData.getString(KEY_SOURCE)
        Log.i(TAG, "Task $id ($source) started, run ${runAttemptCount + 1}")
        try {
            withContext(Dispatchers.IO) {
                // WorkManager counts every run, also those the system stopped; only deaths of the app count here.
                val interrupted = interruptions(runAttemptCount, imports.stopsOf(id))
                when {
                    imports.isCancelled(id) -> cancelled(started = false)
                    // Started over once after the app died during it; a second death may well be the import's own
                    // doing, e.g. a damaged archive, and another try would only repeat it.
                    interrupted >= MAX_RUN_ATTEMPTS -> {
                        Log.w(TAG, "Task $id interrupted $interrupted times; not started again")
                        removeLeftovers()
                        // Likely the import's own doing: a resumed download starts over.
                        discardInput()
                        // App starts queue the bundled install again; the same archives would end the same way. A
                        // failed work would fail the imports queued after it, so a failed write is only logged.
                        if (source == SOURCE_BUNDLED) {
                            try {
                                bundled.pauseUntilUpdate()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Pausing the bundled install failed", e)
                            }
                        }
                        Result.success(resumable(workDataOf(KEY_INTERRUPTED to true, KEY_PAUSED to (source == SOURCE_BUNDLED))))
                    }
                    else -> {
                        removeLeftovers()
                        imports.cancellable(id) { work() }?.also { imports.forgetCancel(id) } ?: cancelled(started = true)
                    }
                }.also { if (it !is Result.Retry) imports.forgetStops(id) }
            }
        } catch (e: CancellationException) {
            if (!isStopped) throw e
            // Cancelled by a dictionary reset, or stopped by the system, which runs it again later.
            val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) " (reason $stopReason)" else ""
            Log.i(TAG, "Task $id stopped by WorkManager$reason")
            withContext(NonCancellable) {
                try {
                    if (!uncounted) imports.markStopped(id)
                } catch (e: Exception) {
                    Log.w(TAG, "Recording the stop failed: ${e.javaClass.simpleName}")
                }
            }
            throw e
        }
    }

    private suspend fun cancelled(started: Boolean): Result {
        Log.i(TAG, if (started) "Task $id cancelled while running" else "Task $id cancelled before it started")
        // A task cancelled before it ran never got to the clean-up at the end of its run.
        discardInput()
        imports.forgetCancel(id)
        return Result.success(workDataOf(KEY_CANCELLED to true))
    }

    /**
     * The first import of a process removes what imports cut short by an earlier process left behind, before it
     * needs the space itself; an import started again after the process died writes new files. One cancelled before
     * it got to that leaves it to the next.
     */
    private suspend fun removeLeftovers() {
        if (imports.leftoversRemoved) return
        try {
            repository.cleanUp(imports.archivesInUse())
            imports.forgetEnded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Removing import leftovers failed", e)
        }
        imports.leftoversRemoved = true
    }

    private suspend fun work(): Result {
        val name = inputData.getString(KEY_NAME).orEmpty()
        runCatching { setForeground(foregroundInfo(shownName())) }
            .onFailure { Log.w(TAG, "Task $id runs without a foreground service: ${it.javaClass.simpleName}") }
        val started = System.currentTimeMillis()
        // Failures are results with an error: a failed work would fail every import queued after it unrun.
        val output = try {
            val titles = when (inputData.getString(KEY_SOURCE)) {
                SOURCE_BUNDLED -> installBundled()
                SOURCE_FILE -> listOf(importFile(File(requireNotNull(inputData.getString(KEY_PATH)))))
                SOURCE_YOMITAN_BACKUP -> importCollection(name)
                SOURCE_URL -> {
                    // Reading the index and connecting take a moment; the task says it is downloading meanwhile.
                    setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_DOWNLOAD))
                    val built = inputData.getString(KEY_ENTRY_ID)
                        ?.let { id -> catalogEntries.firstOrNull { it.id == id && it.build != null } }
                    if (built != null) {
                        listOf(download(built.downloadUrl, name) { buildAndImport(built, it, name) })
                    } else {
                        val url = inputData.getString(KEY_INDEX_URL)?.let { latestDownloadUrl(it) }
                            ?: requireNotNull(inputData.getString(KEY_URL))
                        listOf(download(url, name, ::importDownloaded))
                    }
                }
                SOURCE_MODEL -> {
                    setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_DOWNLOAD))
                    val id = inputData.getString(KEY_ENTRY_ID)
                    val entry = catalogEntries.firstOrNull { it.id == id && it.isModel } ?: error("The model is not in the catalog")
                    listOf(
                        download(entry.downloadUrl, name) { archive ->
                            models.install(entry, archive)
                            entry.title
                        },
                    )
                }
                else -> error("Unknown import source")
            }
            Log.i(TAG, "Task $id done in ${System.currentTimeMillis() - started} ms: ${titles.size} dictionaries $titles")
            workDataOf(KEY_TITLES to titles.toTypedArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadBrokeOffException) {
            val retries = imports.retriesOf(id)
            if (retries < MAX_RETRIES) {
                Log.w(TAG, "Task $id: the download broke off (${e.cause?.javaClass?.simpleName}); retry ${retries + 1} of $MAX_RETRIES")
                imports.markRetry(id)
                countRunAsStopped()
                return Result.retry()
            }
            Log.w(TAG, "Task $id: the download broke off $MAX_RETRIES times; stopped", e.cause)
            resumable(workDataOf(KEY_ERROR to e.reason().take(MAX_ERROR_LENGTH), KEY_DOWNLOAD_STOPPED to true, KEY_CONNECTION_LOST to true))
        } catch (e: ResumableDownload.FinalStatusException) {
            Log.w(TAG, "Task $id: the server answered HTTP ${e.code}")
            resumable(workDataOf(KEY_ERROR to "HTTP ${e.code}", KEY_DOWNLOAD_STOPPED to true))
        } catch (e: NotEnoughSpaceException) {
            Log.w(TAG, "Task $id: not enough space: ${e.neededBytes shr 20} MB needed, ${e.freeBytes shr 20} MB free")
            workDataOf(KEY_ERROR to e.message, KEY_NEEDED_BYTES to e.neededBytes, KEY_FREE_BYTES to e.freeBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Task $id failed after ${System.currentTimeMillis() - started} ms", e)
            // Work data is limited to 10 KB.
            resumable(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName).take(MAX_ERROR_LENGTH)))
        }
        return Result.success(output)
    }

    /** [output] of a failed download with what [DictionaryImports.resume] needs; other imports' output as it is. */
    private fun resumable(output: Data): Data {
        val source = inputData.getString(KEY_SOURCE)
        if (source != SOURCE_URL && source != SOURCE_MODEL) return output
        return Data.Builder()
            .apply { DictionaryImports.RESUME_KEYS.forEach { key -> inputData.keyValueMap[key]?.let { put(key, it) } } }
            .putAll(output)
            .putBoolean(KEY_RESUMABLE, true)
            .build()
    }

    /** Counts this run as one the app did not die in; once per run. */
    private suspend fun countRunAsStopped() {
        if (uncounted) return
        imports.markStopped(id)
        uncounted = true
    }

    /** Takes back [countRunAsStopped]: what follows counts again. */
    private suspend fun countRunAgain() {
        if (!uncounted) return
        imports.unmarkStopped(id)
        uncounted = false
    }

    private suspend fun installBundled(): List<String> {
        if (bundled.isPaused()) {
            Log.w(TAG, "Bundled install skipped: paused until the app is updated")
            return emptyList()
        }
        // Their language may be off, or not chosen yet on a first run; turning it on installs them.
        val wanted = !profiles.firstRunPending.first() && bundled.language in profiles.current().turnedOn
        if (!wanted) Log.i(TAG, "Bundled install skipped: their language is off")
        val pending = if (wanted) bundled.pending { repository.getAll().map { BundledDictionaries.Copy(it.title, it.revision) } } else emptyList()
        return pending.map { asset ->
            Log.i(TAG, "Installing ${asset.name} (${asset.size shr 10} KB)")
            // The dictionary's own title, as the Dictionaries screen will list it; the file name only if unreadable.
            val name = bundled.titleOf(asset) ?: asset.displayName
            setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT, KEY_SOURCE to SOURCE_BUNDLED))
            val archive = storage.newArchiveFile()
            try {
                bundled.copy(asset, archive)
                val started = System.currentTimeMillis()
                val dictionary = repository.import(archive, bundled = true, catalog = catalogEntries)
                bundled.markInstalled(asset, dictionary.title, dictionary.revision)
                Log.i(TAG, "Installed ${dictionary.title} in ${System.currentTimeMillis() - started} ms")
                dictionary.title
            } finally {
                archive.delete()
            }
        }.also {
            repository.fillBundledTagNotes(bundled::tagNotesOf)
            repository.decodeStoredTexts()
            repository.replaceIndexAll(catalogEntries)
            try {
                installedLanguages.fillOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Filling in languages failed", e)
            }
        }
    }

    private suspend fun importFile(archive: File): String {
        Log.i(TAG, "Importing a file of ${archive.length() shr 10} KB")
        setProgress(workDataOf(KEY_STAGE to STAGE_IMPORT))
        try {
            return repository.import(archive, catalog = catalogEntries).title
        } finally {
            discardInput()
        }
    }

    /**
     * Deletes the copy of the picked file this import reads and gives back the access to the file it kept; deletes
     * what a download got so far.
     */
    private fun discardInput() {
        val path = inputData.getString(KEY_PATH)
        when (inputData.getString(KEY_SOURCE)) {
            SOURCE_URL, SOURCE_MODEL -> inputData.getString(KEY_ARCHIVE)?.let { name ->
                val archive = storage.downloadFile(name)
                archive.delete()
                storage.downloadState(archive).delete()
            }
            SOURCE_FILE -> if (inputData.getBoolean(KEY_DELETE_FILE, false)) path?.let { File(it).delete() }
            SOURCE_YOMITAN_BACKUP -> {
                path?.let { File(it).delete() }
                inputData.getString(KEY_URI)?.let { uri ->
                    runCatching {
                        applicationContext.contentResolver
                            .releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
        }
    }

    /**
     * Splits a Yomitan collection export into archives of the chosen dictionaries and imports them one by one.
     * The export is read from the picked document when the app kept access to it, otherwise from a copy.
     *
     * A first pass measures the chosen dictionaries: the archives are written uncompressed when there is room
     * for that, compressed when only that fits, and nothing is written when even that does not fit. With plenty of
     * room ([CollectionSpace.clearlyEnough]) the pass is skipped and the archives are uncompressed. Free space leaves
     * out the cache the system could clear: counting it would need `StorageManager.allocateBytes` before writing.
     */
    @SuppressLint("UsableSpace")
    private suspend fun importCollection(name: String): List<String> {
        val uri = inputData.getString(KEY_URI)?.toUri()
        val copy = inputData.getString(KEY_PATH)?.let(::File)
        val selected = inputData.getNullableStringArray(KEY_SELECTED)?.filterNotNull()?.toSet()
        val size = inputData.getLong(KEY_SIZE, -1)
        val staging = storage.newStagingDirectory()
        val job = currentCoroutineContext().job
        fun open() = uri?.let { applicationContext.contentResolver.openInputStream(it) }
            ?: copy?.inputStream()
            ?: throw IOException("Cannot open the file")
        fun progress(stage: String): (Long) -> Unit {
            val throttle = ProgressThrottle()
            return { read ->
                // Called every few megabytes from blocking reads, which otherwise run to the end of a cancelled import.
                job.ensureActive()
                if (size > 0) {
                    val percent = (read * 100 / size).toInt().coerceIn(0, 100)
                    if (throttle.pass(percent)) {
                        setProgressAsync(workDataOf(KEY_NAME to name, KEY_STAGE to stage, KEY_PERCENT to percent))
                    }
                }
            }
        }
        try {
            var started = System.currentTimeMillis()
            val free = staging.usableSpace
            Log.i(
                TAG,
                "Collection of ${size shr 20} MB, read ${if (uri != null) "in place" else "from a copy"}, " +
                    "${selected?.size ?: "all"} dictionaries chosen",
            )
            val plan = if (CollectionSpace.clearlyEnough(size, free)) {
                Log.i(TAG, "Skipped measuring: ${free shr 20} MB free for a ${size shr 20} MB file")
                CollectionSpacePlan.Uncompressed
            } else {
                setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_CHECK, KEY_PERCENT to 0))
                val sizes = open().use { YomitanBackup.measure(it, selected, progress(STAGE_CHECK)) }
                CollectionSpace.plan(sizes, free).also { plan ->
                    Log.i(
                        TAG,
                        "Measured ${sizes.size} dictionaries in ${System.currentTimeMillis() - started} ms: $plan; " +
                            "peak ${CollectionSpace.peakBytes(sizes, compressed = false) shr 20} MB uncompressed, " +
                            "${CollectionSpace.peakBytes(sizes, compressed = true) shr 20} MB compressed, " +
                            "${free shr 20} MB free",
                    )
                }
            }
            if (plan is CollectionSpacePlan.NotEnough) throw NotEnoughSpaceException(plan.neededBytes, plan.freeBytes)
            started = System.currentTimeMillis()
            setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_CONVERT, KEY_PERCENT to 0))
            val compress = plan == CollectionSpacePlan.Compressed
            val backup = YomitanBackup(staging, compress)
            val archives = open().use { backup.convert(it, selected, progress(STAGE_CONVERT)) }
            Log.i(TAG, "Wrote ${archives.size} archives in ${System.currentTimeMillis() - started} ms")
            started = System.currentTimeMillis()
            val titles = importEach(archives, backup::titleOf) { index, archive ->
                setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT, KEY_PERCENT to index * 100 / archives.size))
                repository.import(archive, catalog = catalogEntries).title
            }
            Log.i(TAG, "Imported ${titles.size} dictionaries in ${System.currentTimeMillis() - started} ms")
            return titles
        } finally {
            staging.deleteRecursively()
            discardInput()
        }
    }

    /** `downloadUrl` from a Yomitan index file, or null if it cannot be read. */
    private fun latestDownloadUrl(indexUrl: String): String? = runCatching {
        downloadClient.newCall(Request.Builder().url(indexUrl).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Cannot read $indexUrl: HTTP ${response.code}")
                return null
            }
            Json.parseToJsonElement(response.body.string()).jsonObject["downloadUrl"]?.jsonPrimitive?.contentOrNull
        }
    }.onFailure { Log.w(TAG, "Cannot read $indexUrl", it) }.getOrNull()

    /**
     * Downloads [url] into the task's archive, which [install] gets; returns what [install] returns. The archive stays
     * when the download breaks off ([DownloadBrokeOffException]), so the next run continues it; it is deleted once
     * installed or when the install fails.
     */
    private suspend fun download(url: String, name: String, install: suspend (File) -> String): String {
        if (url.startsWith(ASSET_URL_PREFIX)) return installAsset(url.removePrefix(ASSET_URL_PREFIX), name, install)
        // Queued by an older version: no archive of its own to continue.
        val archive = inputData.getString(KEY_ARCHIVE)?.let(storage::downloadFile) ?: storage.newArchiveFile()
        val state = storage.downloadState(archive)
        countRunAsStopped()
        val started = System.currentTimeMillis()
        val before = if (archive.exists()) archive.length() else 0L
        Log.i(TAG, "Downloading $url" + if (before > 0) ", ${before shr 10} KB already there" else "")
        val throttle = ProgressThrottle()
        try {
            ResumableDownload(downloadClient).fetch(url, archive, state) { copied, total ->
                val percent = if (total > 0) (copied * 100 / total).toInt() else -1
                if (throttle.pass(percent)) {
                    setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_DOWNLOAD, KEY_PERCENT to percent))
                }
            }
        } catch (e: ResumableDownload.FinalStatusException) {
            throw e
        } catch (e: IOException) {
            throw DownloadBrokeOffException(e)
        }
        Log.i(TAG, "Downloaded ${(archive.length() - before) shr 10} KB of ${archive.length() shr 10} KB in ${System.currentTimeMillis() - started} ms")
        countRunAgain()
        setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT))
        try {
            return install(archive)
        } finally {
            archive.delete()
            state.delete()
        }
    }

    /** Copies the archive the app carries at [path] in its assets into a new archive file, which [install] gets. */
    private suspend fun installAsset(path: String, name: String, install: suspend (File) -> String): String {
        val archive = storage.newArchiveFile()
        try {
            withContext(Dispatchers.IO) {
                applicationContext.assets.open(path).use { input -> archive.outputStream().use { input.copyTo(it) } }
            }
            Log.i(TAG, "Copied the bundled archive $path, ${archive.length() shr 10} KB")
            setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT))
            return install(archive)
        } finally {
            archive.delete()
        }
    }

    /** A download that broke off: the network, a timeout, a server error. The next run continues it. */
    private class DownloadBrokeOffException(cause: IOException) : IOException(cause) {
        fun reason(): String = cause?.message ?: cause?.javaClass?.simpleName.orEmpty()
    }

    private suspend fun importDownloaded(archive: File): String {
        val replaces = inputData.getLong(KEY_REPLACE_ID, -1).takeIf { it >= 0 }
        return repository.import(archive, replaces = replaces, catalog = catalogEntries).title
    }

    /**
     * Builds the dictionary of [entry] from its checked [source] file and imports it. A frequency dictionary built
     * for a language sorts its results, unless the user chose another one.
     */
    private suspend fun buildAndImport(entry: CatalogEntry, source: File, name: String): String {
        if (!WordfreqDictionary.hasChecksum(source, requireNotNull(entry.sha256))) {
            throw IOException("The downloaded file does not match its checksum")
        }
        setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_CONVERT))
        val archive = storage.newArchiveFile()
        try {
            val started = System.currentTimeMillis()
            val words = WordfreqDictionary.build(source, entry.sourceLanguage, requireNotNull(entry.version), archive)
            Log.i(TAG, "Built ${entry.id}: $words words, ${archive.length() shr 10} KB in ${System.currentTimeMillis() - started} ms")
            currentCoroutineContext().ensureActive()
            setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT))
            val replaces = inputData.getLong(KEY_REPLACE_ID, -1).takeIf { it >= 0 }
            val dictionary = repository.import(archive, replaces = replaces, catalog = catalogEntries)
            if (dictionary.kind == DictionaryKind.FREQUENCY) {
                Language.of(entry.sourceLanguage)?.let { repository.preferSortDictionary(it, dictionary.id) }
            }
            return dictionary.title
        } finally {
            archive.delete()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(shownName())

    private fun shownName(): String =
        shownImportName(inputData.getString(KEY_NAME).orEmpty(), inputData.getString(KEY_ENTRY_ID), catalogEntries)

    private fun foregroundInfo(name: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.dictionary_import_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dictionary_import)
            .setContentTitle(applicationContext.getString(R.string.dictionary_import_notification))
            .setContentText(name)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, applicationContext.getString(R.string.dictionary_import_cancel), ImportCancelReceiver.intent(applicationContext, id))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_SOURCE = "source"
        const val KEY_NAME = "name"
        const val KEY_PATH = "path"
        const val KEY_URL = "url"
        const val KEY_INDEX_URL = "index_url"
        const val KEY_REPLACE_ID = "replace_id"

        /** The file name of a download's archive in the downloads directory, kept across the task's runs. */
        const val KEY_ARCHIVE = "archive"
        const val KEY_DELETE_FILE = "delete_file"
        const val KEY_TITLES = "titles"

        /** The catalog entry of a model download. */
        const val KEY_ENTRY_ID = "entry_id"
        const val KEY_ERROR = "error"

        /** Output of an import the app died during twice; see [MAX_RUN_ATTEMPTS]. */
        const val KEY_INTERRUPTED = "interrupted"

        /** Output of an interrupted bundled install: none runs until [BundledDictionaries.resumeInstall]. */
        const val KEY_PAUSED = "paused"

        /** Output of an import the user cancelled. */
        const val KEY_CANCELLED = "cancelled"

        /** Output of a download that stopped: it holds the input [DictionaryImports.resume] queues again. */
        const val KEY_RESUMABLE = "resumable"

        /** Output of a download that broke off, as opposed to one whose import failed. */
        const val KEY_DOWNLOAD_STOPPED = "download_stopped"

        /** Output of a download stopped because the connection broke off ([MAX_RETRIES] times), not by the server. */
        const val KEY_CONNECTION_LOST = "connection_lost"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val KEY_URI = "uri"
        const val KEY_SELECTED = "selected"
        const val KEY_SIZE = "size"
        const val KEY_NEEDED_BYTES = "needed_bytes"
        const val KEY_FREE_BYTES = "free_bytes"

        const val SOURCE_BUNDLED = "bundled"
        const val SOURCE_FILE = "file"
        const val SOURCE_URL = "url"
        const val SOURCE_YOMITAN_BACKUP = "yomitan_backup"
        const val SOURCE_MODEL = "model"

        const val STAGE_DOWNLOAD = "download"
        const val STAGE_IMPORT = "import"
        const val STAGE_CONVERT = "convert"
        const val STAGE_CHECK = "check"

        private const val TAG = "DictionaryImport"
        private const val CHANNEL_ID = "dictionary_imports"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_ERROR_LENGTH = 2000

        /** Runs of one import: the first, and one more after the app died during it (owner). */
        private const val MAX_RUN_ATTEMPTS = 2

        /** Automatic retries of a download that broke off, after which it waits for the user (owner). */
        const val MAX_RETRIES = 3

        /**
         * Earlier runs of a task the app died in: WorkManager's [runAttemptCount] less the runs the system [stops]
         * ([DictionaryImports.markStopped]), which WorkManager counts as well.
         */
        fun interruptions(runAttemptCount: Int, stops: Int): Int = (runAttemptCount - stops).coerceAtLeast(0)
    }
}

/**
 * Imports [archives] in order and deletes each one afterwards. A dictionary that fails does not keep the others out:
 * the failures are thrown together at the end, each named by [titleOf].
 */
internal suspend fun importEach(
    archives: List<File>,
    titleOf: (File) -> String?,
    import: suspend (index: Int, archive: File) -> String,
): List<String> {
    val failures = mutableListOf<String>()
    val titles = archives.mapIndexedNotNull { index, archive ->
        try {
            import(index, archive)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DictionaryImport", "A dictionary of the collection failed", e)
            failures += "${titleOf(archive) ?: archive.name}: ${e.message ?: e.javaClass.simpleName}"
            null
        } finally {
            archive.delete()
        }
    }
    if (failures.isNotEmpty()) throw DictionaryImportException(failures.joinToString("; "))
    return titles
}
