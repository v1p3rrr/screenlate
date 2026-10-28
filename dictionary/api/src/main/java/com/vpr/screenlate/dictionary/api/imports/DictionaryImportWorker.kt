package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.dictionary.api.languages.InstalledLanguages
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.R
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
 * archive downloaded from a URL. Runs as a foreground `dataSync` job so the system does not stop a long import.
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
    httpClient: OkHttpClient,
) : CoroutineWorker(context, params) {
    private val downloadClient = httpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Languages for dictionaries whose index.json names none. */
    private val catalogEntries: List<CatalogEntry> by lazy { catalog.local() }

    /**
     * Whether this run is counted as one the app did not die in ([DictionaryImports.markStopped]): set while it
     * downloads, waits for a network or for a retry, so that a death then does not count towards [MAX_RUN_ATTEMPTS].
     */
    private var uncounted = false

    override suspend fun doWork(): Result = imports.tracked {
        val source = inputData.getString(KEY_SOURCE)
        Log.i(TAG, "Task $id ($source) started, run ${runAttemptCount + 1}")
        try {
            withContext(Dispatchers.IO) {
                val ended = imports.endOf(id)
                // WorkManager counts every run, also those the system stopped; only deaths of the app count here.
                val interrupted = interruptions(runAttemptCount, imports.stopsOf(id))
                val result = when {
                    // WorkManager stopped the last run just as it ended and dropped its result; the task stays as it ended.
                    ended != null -> {
                        Log.i(TAG, "Task $id ended in its last run; not run again")
                        Result.success(ended)
                    }
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
                        imports.cancellable(id) { work() } ?: cancelled(started = true)
                    }
                }
                ended(result.outputData)
                result
            }
        } catch (e: CancellationException) {
            if (!isStopped) throw e
            // Cancelled by a dictionary reset, or stopped by the system, which runs it again later: once the network is
            // back after a stop for its loss.
            val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stopReason else WorkInfo.STOP_REASON_UNKNOWN
            Log.i(TAG, "Task $id stopped by WorkManager (reason $reason): ${afterStop(reason)}")
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

    private fun cancelled(started: Boolean): Result {
        Log.i(TAG, if (started) "Task $id cancelled while running" else "Task $id cancelled before it started")
        // A task cancelled before it ran never got to the clean-up at the end of its run.
        discardInput()
        return Result.success(workDataOf(KEY_CANCELLED to true))
    }

    /**
     * Records the task's end ([DictionaryImports.recordEnd]) before anything else of the run, then forgets its cancel,
     * stops and retries: a stop by WorkManager from here on drops the [output], and the run it starts then ends with it.
     */
    private suspend fun ended(output: Data) {
        withContext(NonCancellable) {
            try {
                imports.recordEnd(id, output)
            } catch (e: Exception) {
                Log.w(TAG, "Recording the end of task $id failed: ${e.javaClass.simpleName}")
            }
        }
        imports.forgetCancel(id)
        imports.forgetStops(id)
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
        runCatching { setForeground(foregroundInfo(name)) }
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
                    val url = inputData.getString(KEY_INDEX_URL)?.let { latestDownloadUrl(it) }
                        ?: requireNotNull(inputData.getString(KEY_URL))
                    listOf(download(url, name, ::importDownloaded))
                }
                else -> error("Unknown import source")
            }
            Log.i(TAG, "Task $id done in ${System.currentTimeMillis() - started} ms: ${titles.size} dictionaries $titles")
            workDataOf(KEY_TITLES to titles.toTypedArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadBrokeOffException) {
            Log.w(TAG, "Task $id: the download broke off after $MAX_RETRIES retries; stopped", e.cause)
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
        if (source != SOURCE_URL) return output
        return Data.Builder()
            .putAll(inputData.keyValueMap.filterKeys { it in DictionaryImports.RESUME_KEYS })
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
        return bundled.pending { repository.getAll().map { BundledDictionaries.Copy(it.title, it.revision) } }.map { asset ->
            Log.i(TAG, "Installing ${asset.name} (${asset.size shr 10} KB)")
            // The dictionary's own title, as the Dictionaries screen will list it; the file name only if unreadable.
            val name = bundled.titleOf(asset) ?: asset.displayName
            setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT))
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
            SOURCE_URL -> inputData.getString(KEY_ARCHIVE)?.let { name ->
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
     * Downloads [url] into the task's archive, which [install] gets; returns what [install] returns. A download that
     * breaks off waits for its retry and continues ([MAX_RETRIES] times), one whose network went waits for it; the
     * archive stays when it stops ([DownloadBrokeOffException], a stop by WorkManager), so a later run continues it. It
     * is deleted once installed or when the install fails.
     */
    private suspend fun download(url: String, name: String, install: suspend (File) -> String): String {
        // Queued by an older version: no archive of its own to continue.
        val archive = inputData.getString(KEY_ARCHIVE)?.let(storage::downloadFile) ?: storage.newArchiveFile()
        val state = storage.downloadState(archive)
        countRunAsStopped()
        val started = System.currentTimeMillis()
        val before = if (archive.exists()) archive.length() else 0L
        Log.i(TAG, "Downloading $url" + if (before > 0) ", ${before shr 10} KB already there" else "")
        var throttle = ProgressThrottle()
        while (true) {
            try {
                ResumableDownload(downloadClient).fetch(url, archive, state) { copied, total ->
                    val percent = if (total > 0) (copied * 100 / total).toInt() else -1
                    if (throttle.pass(percent)) {
                        setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_DOWNLOAD, KEY_PERCENT to percent))
                    }
                }
                break
            } catch (e: ResumableDownload.FinalStatusException) {
                throw e
            } catch (e: IOException) {
                val failure = failedRead(
                    // Stopped by WorkManager meanwhile, e.g. for the lost network: it runs the task again.
                    ensureActive = { currentCoroutineContext().ensureActive() },
                    networkLost = { imports.networkLost(NETWORK_SETTLE_MS) },
                    awaitNetwork = { imports.awaitNetwork(NETWORK_WAIT_MS) },
                )
                // A lost network is not a break-off of the host: no retry is counted. WorkManager stops the task as the
                // network went; a network back before that continues here, and so does one it does not stop for, as a
                // retry result would wait WorkManager's backoff, which grows with every run.
                val reason = e.javaClass.simpleName
                when (failure) {
                    FailedRead.NETWORK_BACK -> Log.i(TAG, "Task $id: the network went away during the download ($reason) and is back; continues")
                    FailedRead.NO_NETWORK -> Log.i(TAG, "Task $id: the network went away during the download ($reason), none for ${NETWORK_WAIT_MS / 1000} s; tries again")
                    FailedRead.BROKE_OFF -> {
                        val retries = imports.retriesOf(id)
                        if (retries >= MAX_RETRIES) throw DownloadBrokeOffException(e)
                        val wait = retryDelayMs(retries + 1)
                        Log.w(TAG, "Task $id: the download broke off ($reason); retry ${retries + 1} of $MAX_RETRIES in ${wait / 1000} s")
                        imports.markRetry(id)
                        // Waited here: WorkManager's backoff after a retry result grows with every run, also with the
                        // runs it stopped for a lost network.
                        setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_RETRY))
                        delay(wait)
                        setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_DOWNLOAD))
                        throttle = ProgressThrottle()
                    }
                }
            }
        }
        Log.i(TAG, "Downloaded ${(archive.length() - before) shr 10} KB of ${archive.length() shr 10} KB in ${System.currentTimeMillis() - started} ms")
        countRunAgain()
        setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT))
        try {
            return install(archive)
        } finally {
            // WorkManager runs a task it stopped again (the network constraint stops the import too); that run imports
            // the finished download instead of downloading it again.
            if (!isStopped) {
                archive.delete()
                state.delete()
            }
        }
    }

    /**
     * A download that broke off while the network stayed up (a timeout, a server error) after its retries; a resume
     * continues it.
     */
    private class DownloadBrokeOffException(cause: IOException) : IOException(cause) {
        fun reason(): String = cause?.message ?: cause?.javaClass?.simpleName.orEmpty()
    }

    private suspend fun importDownloaded(archive: File): String {
        val replaces = inputData.getLong(KEY_REPLACE_ID, -1).takeIf { it >= 0 }
        return repository.import(archive, replaces = replaces, catalog = catalogEntries).title
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(inputData.getString(KEY_NAME).orEmpty())

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

        const val STAGE_DOWNLOAD = "download"

        /** A download waiting in its run for its automatic retry; its task shows as queued with its retries. */
        const val STAGE_RETRY = "retry"
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

        /** The wait before the first automatic retry; it doubles with each retry ([retryDelayMs]). */
        private const val RETRY_DELAY_MS = 30_000L

        /** How long after a download broke off the network may still go, which makes it a wait for the network. */
        private const val NETWORK_SETTLE_MS = 2_000L

        /**
         * How long a download whose network went waits for it before it tries again, in its run; WorkManager normally
         * stops the task first and runs it again once a network is up.
         */
        private const val NETWORK_WAIT_MS = 30_000L

        /**
         * The wait in the run before the automatic [retry] (from 1) of a download that broke off: 30 s, 1 min, 2 min.
         * It does not depend on WorkManager's run count, which grows with every stop for a lost network too.
         */
        fun retryDelayMs(retry: Int): Long = RETRY_DELAY_MS shl (retry - 1)

        /** What follows a stop by WorkManager for [reason] (`WorkInfo.STOP_REASON_*`), for the log. */
        fun afterStop(reason: Int): String = when (reason) {
            WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled"
            WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "waits for a network, then runs again"
            else -> "runs again later"
        }

        /**
         * Earlier runs of a task the app died in: WorkManager's [runAttemptCount] less the runs the system [stops]
         * ([DictionaryImports.markStopped]), which WorkManager counts as well.
         */
        fun interruptions(runAttemptCount: Int, stops: Int): Int = (runAttemptCount - stops).coerceAtLeast(0)
    }
}

/** What a failed read of a download was; see [failedRead]. */
internal enum class FailedRead {
    /** The network went and came back: the download continues at once. */
    NETWORK_BACK,

    /** The network went and did not come back within the wait: the download tries again. */
    NO_NETWORK,

    /** The network stayed up: a timeout, a server error. The download waits for its retry or stops. */
    BROKE_OFF,
}

/**
 * Tells a lost network from a download that broke off, after a failed read. [ensureActive] throws when WorkManager
 * stopped the task meanwhile; [networkLost] says whether the network is gone now or a moment later (a read may fail
 * just before the system notices); [awaitNetwork] waits a while for the network and says whether it came back.
 */
internal suspend fun failedRead(
    ensureActive: suspend () -> Unit,
    networkLost: suspend () -> Boolean,
    awaitNetwork: suspend () -> Boolean,
): FailedRead {
    ensureActive()
    return when {
        !networkLost() -> FailedRead.BROKE_OFF
        awaitNetwork() -> FailedRead.NETWORK_BACK
        else -> FailedRead.NO_NETWORK
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
