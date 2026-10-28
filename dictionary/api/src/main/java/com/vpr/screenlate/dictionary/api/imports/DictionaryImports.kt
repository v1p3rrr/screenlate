package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.await
import androidx.work.workDataOf
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.dictionary.api.catalog.ASSET_URL_PREFIX
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ARCHIVE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CANCELLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CONNECTION_LOST
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_DELETE_FILE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_DOWNLOAD_STOPPED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ENTRY_ID
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ERROR
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_FREE_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_INDEX_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_INTERRUPTED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NAME
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NEEDED_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PATH
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PAUSED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PERCENT
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_REPLACE_ID
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_REQUEUED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_RESUMABLE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_RETRIES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SELECTED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SIZE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SOURCE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_STAGE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_TITLES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URI
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_BUNDLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_FILE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_MODEL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_YOMITAN_BACKUP
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_CHECK
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_CONVERT
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_DOWNLOAD
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_WAIT
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** State of one queued, running or finished import as shown in the UI. */
data class ImportTask(
    val id: UUID,
    val name: String,
    val state: State,
    /** Progress of a download, conversion or multi-dictionary import in percent; null when unknown. */
    val percent: Int?,
    val titles: List<String>,
    val error: String?,
    /** Set when a collection import did not start because the storage cannot hold it. */
    val shortage: CollectionSpacePlan.NotEnough? = null,
    /** Set when the import stopped because the app died during it again after it was started over. */
    val interrupted: Boolean = false,
    /**
     * Set on an [interrupted] install of the bundled dictionaries: none runs again until the app is updated or the user
     * starts one ([BundledDictionaries.resumeInstall]).
     */
    val paused: Boolean = false,
    /** The worker's source kind, independent of the displayed archive or dictionary name. */
    val source: String? = null,
    /** Catalog language and entry, kept independently of changing progress names. */
    val language: String? = null,
    val catalogEntry: String? = null,
    /** Set on a download that stopped for any reason; [DictionaryImports.resume] starts it again where it broke off. */
    val resumable: Boolean = false,
    /** Set when the download itself broke off, not the import after it. */
    val downloadStopped: Boolean = false,
    /** Set when the download stopped because the connection broke off; [error] then holds the technical reason. */
    val connectionLost: Boolean = false,
    /**
     * The automatic retries of a download that broke off, set while the retry waits ([DictionaryImports.requeue]) for
     * its delay, the network or the tasks before it.
     */
    val retries: Int = 0,
    /** Set on a download that has not started yet: it starts only with a network ([DictionaryImports.online]). */
    val needsNetwork: Boolean = false,
) {
    enum class State { QUEUED, DOWNLOADING, CHECKING_SPACE, CONVERTING, IMPORTING, SUCCEEDED, FAILED }

    val finished: Boolean get() = state == State.SUCCEEDED || state == State.FAILED

    /** The [name] to show: see [shownImportName]. */
    fun shownName(entries: List<CatalogEntry>, locale: Locale = Locale.getDefault()): String =
        shownImportName(name, catalogEntry, entries, locale)
}

/**
 * A catalog download is named after its entry's own title, which other code matches; the user sees the entry's
 * display title, as the download screens list it. Updates, files and the bundled install keep their [name].
 */
fun shownImportName(name: String, entryId: String?, entries: List<CatalogEntry>, locale: Locale = Locale.getDefault()): String =
    entryId?.let { id -> entries.firstOrNull { it.id == id && it.title == name } }?.displayTitle(locale) ?: name

/**
 * Queues dictionary imports on WorkManager and reports their progress. Two tasks run at a time, on two chains, so a
 * download that stalls does not hold up all the others; their import steps run one at a time ([importing]).
 */
@Singleton
class DictionaryImports @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: DictionaryStorage,
    private val preferences: DataStore<Preferences>,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** The queue position of the last enqueued import; see [ORDER_TAG_PREFIX]. */
    private val lastOrder = AtomicLong()

    /** Import workers running in this process; a cancelled one runs on until its current step returns. */
    private val running = MutableStateFlow(0)

    /** Whether an import of this process removed what earlier processes left behind; see the worker. */
    @Volatile
    internal var leftoversRemoved = false

    /** Running imports of this process that [cancel] stops. */
    private val runs = CancellableRuns()

    /** Held while a task is queued, so that the next one sees it; see [queue]. */
    private val queueing = Mutex()

    /** Held by the import step of a task; see [importing]. */
    private val importLock = Mutex()

    /** Imports in the order they were queued; cancelled and dismissed ones are left out. */
    val tasks: Flow<List<ImportTask>> = combine(
        workManager.getWorkInfosByTagFlow(TAG),
        // Every settings write emits; the tasks change only with these.
        preferences.data.map { TaskMarks(it[CANCELLED].orEmpty(), it[DISMISSED].orEmpty()) }.distinctUntilChanged(),
    ) { infos, marks -> importTasks(infos, marks.cancelled, marks.dismissed) }

    private data class TaskMarks(val cancelled: Set<String>, val dismissed: Set<String>)

    /**
     * Whether a network that downloads wait for is up: the default network has internet access the system validated,
     * as the downloads' network constraint asks.
     */
    val online: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            send(true)
            awaitClose()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.downloadsCanRun())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(manager.activeNetwork?.let(manager::getNetworkCapabilities)?.downloadsCanRun() == true)
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities.downloadsCanRun(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    /**
     * Whether the network is gone ([online]) now or within [settleMs]: a download that breaks off as the network goes
     * may fail a moment before the system notices.
     */
    internal suspend fun networkLost(settleMs: Long): Boolean = withTimeoutOrNull(settleMs) { online.first { !it } } != null

    /** Waits up to [timeoutMs] for a network ([online]); returns whether one came. */
    internal suspend fun awaitNetwork(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { online.first { it } } != null

    /**
     * Installs bundled dictionaries that are not installed yet; returns the task's id. Cheap when there is nothing to
     * do or the install is paused ([BundledDictionaries.pauseUntilUpdate]).
     */
    suspend fun installBundled(): UUID = enqueue(workDataOf(KEY_SOURCE to SOURCE_BUNDLED), name = "")

    /** Returns once the task [id] has finished, failed or been cancelled or removed. */
    suspend fun awaitFinished(id: UUID) {
        workManager.getWorkInfoByIdFlow(id).first { it == null || it.state.isFinished }
    }

    /**
     * File names of the archives that queued imports still need; null when an import queued by an older app version,
     * which does not name its archive, may need one.
     */
    internal suspend fun archivesInUse(): Set<String>? = archivesInUse(workManager.getWorkInfosByTagFlow(TAG).first())

    /** Copies the archive behind [uri] into app storage and queues its import. */
    suspend fun importFrom(uri: Uri) {
        val name = displayName(uri)
        importFile(copy(uri), name, deleteAfter = true)
    }

    /** Dictionaries listed in a Yomitan collection export ("Export dictionary collection"); reads its start only. */
    suspend fun scanCollection(uri: Uri): List<CollectionDictionary> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
        input.use { YomitanBackup.scan(it) }
    }

    /**
     * Queues the conversion and import of the chosen dictionaries of a collection export. The file is read where
     * it is when the app may keep access to it, which saves copying gigabytes; otherwise it is copied first.
     */
    suspend fun importCollection(uri: Uri, titles: Set<String>) {
        val name = displayName(uri)
        val size = withContext(Dispatchers.IO) { size(uri) }
        val kept = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        Log.i(LOG_TAG, "Collection of ${size shr 20} MB, ${titles.size} dictionaries chosen; access kept: $kept")
        val source = if (kept) KEY_URI to uri.toString() else KEY_PATH to copy(uri).absolutePath
        enqueue(
            workDataOf(
                KEY_SOURCE to SOURCE_YOMITAN_BACKUP,
                source,
                KEY_NAME to name,
                KEY_SELECTED to titles.toTypedArray(),
                KEY_SIZE to size,
            ),
            name = name,
        )
    }

    private fun size(uri: Uri): Long =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L

    private suspend fun copy(uri: Uri): File {
        val target = storage.newArchiveFile()
        val started = System.currentTimeMillis()
        try {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
                input.use { target.outputStream().use(it::copyTo) }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Copying the picked file failed after ${target.length() shr 10} KB", e)
            target.delete()
            throw e
        }
        Log.i(LOG_TAG, "Copied the picked file, ${target.length() shr 10} KB, in ${System.currentTimeMillis() - started} ms")
        return target
    }

    suspend fun importFile(archive: File, name: String, deleteAfter: Boolean) = enqueue(
        workDataOf(
            KEY_SOURCE to SOURCE_FILE,
            KEY_PATH to archive.absolutePath,
            KEY_NAME to name,
            KEY_DELETE_FILE to deleteAfter,
        ),
        name = name,
    )

    /**
     * Downloads and imports an archive; with [indexUrl], the index's current `downloadUrl` is preferred.
     * [replaces] is the id of the installed dictionary this download updates. A download of the same entry or URL is
     * queued once; see [enqueueDownload].
     */
    suspend fun download(url: String, name: String, indexUrl: String? = null, replaces: Long? = null, entry: CatalogEntry? = null) = enqueueDownload(
        workDataOf(
            KEY_SOURCE to SOURCE_URL,
            KEY_URL to url,
            KEY_INDEX_URL to indexUrl,
            KEY_NAME to name,
            KEY_REPLACE_ID to (replaces ?: -1L),
            // A dictionary the worker builds from the download looks its entry up.
            KEY_ENTRY_ID to entry?.id,
        ),
        name = name,
        language = entry?.sourceLanguage,
        entryId = entry?.id,
    )

    /** Downloads and installs an on-device recognition model of the catalog ([CatalogEntry.isModel]), once; see [enqueueDownload]. */
    suspend fun downloadModel(entry: CatalogEntry) = enqueueDownload(
        workDataOf(
            KEY_SOURCE to SOURCE_MODEL,
            KEY_ENTRY_ID to entry.id,
            KEY_NAME to entry.title,
        ),
        name = entry.title,
        language = entry.sourceLanguage,
        entryId = entry.id,
    )

    /** Removes the finished task [id] from [tasks] and deletes what its download left. */
    suspend fun dismiss(id: UUID) {
        Log.i(LOG_TAG, "Task $id dismissed")
        preferences.edit { it[DISMISSED] = it[DISMISSED].orEmpty() + id.toString() }
        val info = workManager.getWorkInfoByIdFlow(id).first() ?: return
        if (info.tagValue(SOURCE_TAG_PREFIX) in DOWNLOAD_SOURCES) archiveOf(info)?.takeIf { it.isNotEmpty() }?.let(::deleteDownload)
    }

    /**
     * Queues the stopped download [id] ([ImportTask.resumable]) again; it continues the file it left when the server
     * allows that. The stopped task leaves [tasks].
     */
    suspend fun resume(id: UUID) {
        queue {
            val info = workManager.getWorkInfoByIdFlow(id).first()
            // A second tap finds it resumed.
            val dismissed = id.toString() in preferences.data.first()[DISMISSED].orEmpty()
            if (info != null && info.outputData.getBoolean(KEY_RESUMABLE, false) && !dismissed) resumeQueued(info)
        }
    }

    /** Queues the stopped download [info] again and dismisses it; see [resume]. Needs [queueing] held. */
    private suspend fun resumeQueued(info: WorkInfo): UUID {
        val data = Data.Builder().putAll(info.outputData.keyValueMap.filterKeys { it in RESUME_KEYS }).build()
        preferences.edit { it[DISMISSED] = it[DISMISSED].orEmpty() + info.id.toString() }
        Log.i(LOG_TAG, "Task ${info.id} resumed")
        return add(
            data,
            name = data.getString(KEY_NAME).orEmpty(),
            language = info.tagValue(LANGUAGE_TAG_PREFIX),
            entryId = info.tagValue(ENTRY_TAG_PREFIX),
        )
    }

    /**
     * Queues the download of the task [id], which broke off, again at the end of the queue, as its retry number
     * [retries]: it waits [RETRY_DELAY_SECONDS], doubled with each retry, and continues the archive [data] names. The
     * task [id] then ends and leaves [tasks]; [tags] are its tags. Returns the new task's id; null when the task
     * [id] was cancelled or [stopped] by WorkManager meanwhile, which runs it again itself or cancelled all.
     */
    internal suspend fun requeue(id: UUID, data: Data, tags: Set<String>, retries: Int, stopped: () -> Boolean): UUID? = queue {
        val next = add(
            Data.Builder().putAll(data).putInt(KEY_RETRIES, retries).build(),
            name = data.getString(KEY_NAME).orEmpty(),
            language = tags.tagValue(LANGUAGE_TAG_PREFIX),
            entryId = tags.tagValue(ENTRY_TAG_PREFIX),
            exclude = id,
        )
        // Still the last task of its chain: nothing queued after it is cancelled with it.
        if (stopped() || isCancelled(id)) {
            Log.i(LOG_TAG, "Task $id ended while it was queued again; task $next cancelled")
            workManager.cancelWorkById(next).await()
            null
        } else {
            next
        }
    }

    private fun deleteDownload(name: String) {
        val archive = storage.downloadFile(name)
        archive.delete()
        storage.downloadState(archive).delete()
    }

    /**
     * Cancels the task [id] only; the imports queued after it go on. A queued task ends without running when its turn
     * comes, a running one stops at its next step (the engine's import of an archive runs to its end, and its result
     * is dropped). The task leaves [tasks] at once. Kept in the settings file, as the process may die before the task
     * ends.
     */
    suspend fun cancel(id: UUID) {
        Log.i(LOG_TAG, "Task $id cancelled by the user")
        preferences.edit { it[CANCELLED] = it[CANCELLED].orEmpty() + id.toString() }
        runs.cancel(id)
    }

    /** Cancels [id] and waits for its running step before its language's files are deleted. */
    suspend fun cancelAndAwait(id: UUID) {
        cancel(id)
        runs.cancelAndJoin(id)
    }

    /** Whether the user cancelled the task [id]. */
    internal suspend fun isCancelled(id: UUID): Boolean = id.toString() in preferences.data.first()[CANCELLED].orEmpty()

    /** Forgets the cancel of the task [id], which has ended. */
    internal suspend fun forgetCancel(id: UUID) {
        preferences.edit { prefs ->
            val ids = prefs[CANCELLED].orEmpty()
            if (id.toString() in ids) prefs[CANCELLED] = ids - id.toString()
        }
    }

    /**
     * Forgets the cancels of tasks that ended without seeing them (cancelled just as they finished), and the stops of
     * tasks that ended without forgetting them (the process died first).
     */
    internal suspend fun forgetEnded() {
        val works = workManager.getWorkInfosByTagFlow(TAG).first()
        val unfinished = works.filterNot { it.state.isFinished }.mapTo(hashSetOf()) { it.id.toString() }
        val known = works.mapTo(hashSetOf()) { it.id.toString() }
        preferences.edit { prefs ->
            val ids = prefs[CANCELLED].orEmpty()
            if (ids.any { it !in unfinished }) {
                Log.i(LOG_TAG, "Forgot ${ids.count { it !in unfinished }} cancels of ended tasks")
                prefs[CANCELLED] = ids.filterTo(hashSetOf()) { it in unfinished }
            }
            val stops = readStops(prefs[STOPPED].orEmpty())
            if (stops.keys.any { it !in unfinished }) prefs[STOPPED] = writeStops(stops.filterKeys { it in unfinished })
            val ends = prefs[ENDED].orEmpty()
            if (ends.any { endId(it) !in unfinished }) prefs[ENDED] = unfinishedEnds(ends, unfinished)
            if (prefs[RETRIES] != null) prefs.remove(RETRIES)
            // Dismissed tasks stay out of the list until WorkManager prunes their work.
            val dismissed = prefs[DISMISSED].orEmpty()
            if (dismissed.any { it !in known }) prefs[DISMISSED] = dismissed.filterTo(hashSetOf()) { it in known }
        }
    }

    /**
     * Records that WorkManager stopped the running task [id]: the system (a time limit, power saving) runs it again
     * later, and that run does not count as one after the app died. A task cancelled by [cancelAll] does not run again.
     */
    internal suspend fun markStopped(id: UUID) {
        preferences.edit { prefs ->
            val stops = readStops(prefs[STOPPED].orEmpty())
            prefs[STOPPED] = writeStops(stops + (id.toString() to (stops[id.toString()] ?: 0) + 1))
        }
    }

    /** Takes back one [markStopped] of the task [id]: a download counted as stopped while it ran has finished. */
    internal suspend fun unmarkStopped(id: UUID) {
        preferences.edit { prefs ->
            val stops = readStops(prefs[STOPPED].orEmpty())
            val count = stops[id.toString()] ?: return@edit
            prefs[STOPPED] = writeStops(if (count > 1) stops + (id.toString() to count - 1) else stops - id.toString())
        }
    }

    /** How often WorkManager stopped the task [id] while it ran; see [markStopped]. */
    internal suspend fun stopsOf(id: UUID): Int = readStops(preferences.data.first()[STOPPED].orEmpty())[id.toString()] ?: 0

    /** Forgets the stops of the task [id], which has ended. */
    internal suspend fun forgetStops(id: UUID) {
        preferences.edit { prefs ->
            val stops = readStops(prefs[STOPPED].orEmpty())
            if (id.toString() in stops) prefs[STOPPED] = writeStops(stops - id.toString())
        }
    }

    /**
     * Records that the task [id] ended with [output]. WorkManager drops the result of a worker it stops at that moment
     * and runs the task again: that run ends with the same output ([endOf]) instead of doing the work again. Records of
     * tasks that have finished meanwhile are dropped.
     */
    internal suspend fun recordEnd(id: UUID, output: Data) {
        val unfinished = workManager.getWorkInfosByTagFlow(TAG).first().filterNot { it.state.isFinished }
            .mapTo(hashSetOf()) { it.id.toString() }
        preferences.edit { prefs -> prefs[ENDED] = recordedEnds(prefs[ENDED].orEmpty(), id, output, unfinished) }
    }

    /** The output the task [id] ended with in a run whose result WorkManager dropped; see [recordEnd]. */
    internal suspend fun endOf(id: UUID): Data? = endIn(preferences.data.first()[ENDED].orEmpty(), id)

    /**
     * Runs the worker [block] of the task [id] so that [cancel] stops it; returns null when it did. A stop by
     * WorkManager ([cancelAll], the system) still ends the worker as a cancellation.
     */
    internal suspend fun <T> cancellable(id: UUID, block: suspend () -> T): T? = runs.run(id, { isCancelled(id) }, block)

    /**
     * Cancels every queued and running import and returns once the running one has stopped, so nothing it was
     * writing lands later; the cancelled tasks are removed from [tasks].
     */
    suspend fun cancelAll() {
        Log.i(LOG_TAG, "Cancelling all tasks, ${running.value} running")
        val started = System.currentTimeMillis()
        workManager.cancelAllWorkByTag(TAG).await()
        running.first { it == 0 }
        workManager.pruneWork().await()
        preferences.edit {
            it.remove(CANCELLED)
            it.remove(STOPPED)
            it.remove(ENDED)
            it.remove(RETRIES)
        }
        Log.i(LOG_TAG, "All tasks cancelled in ${System.currentTimeMillis() - started} ms")
    }

    /** Runs a worker's [block], counted for [cancelAll]. */
    internal suspend fun <T> tracked(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    /**
     * Runs the import step [block] of a task once no other task imports; [onWait] runs first when it has to wait.
     * Downloads go on two at a time, but the native importer needs a lot of memory and imports one archive at a time.
     */
    internal suspend fun <T> importing(onWait: suspend () -> Unit, block: suspend () -> T): T {
        if (!importLock.tryLock()) {
            onWait()
            importLock.lock()
        }
        try {
            return block()
        } finally {
            importLock.unlock()
        }
    }

    /** Runs [block], which queues tasks, after the tasks being queued; leaving a screen does not stop it. */
    private suspend fun <T> queue(block: suspend () -> T): T = withContext(NonCancellable) { queueing.withLock { block() } }

    private suspend fun enqueue(data: Data, name: String): UUID = queue { add(data, name) }

    /**
     * Queues the download [data] unless a task of the same catalog entry or URL ([downloadKey]) has not finished: its
     * id is returned then. One that stopped and is still listed is resumed ([resume]) instead.
     */
    private suspend fun enqueueDownload(data: Data, name: String, language: String?, entryId: String?): UUID = queue {
        val marks = preferences.data.first()
        val works = workManager.getWorkInfosByTagFlow(TAG).first()
        val same = sameDownload(works, downloadKey(data), marks[CANCELLED].orEmpty(), marks[DISMISSED].orEmpty())
        when {
            same == null -> add(data, name, language, entryId)
            same.state.isFinished -> resumeQueued(same)
            else -> same.id.also { Log.i(LOG_TAG, "Task $it is queued already") }
        }
    }

    /**
     * Queues an import at the end of the chain with fewer unfinished tasks ([leastLoadedChain]), the task [exclude]
     * left out. Needs [queueing] held.
     */
    private suspend fun add(
        data: Data,
        name: String,
        language: String? = null,
        entryId: String? = null,
        exclude: UUID? = null,
    ): UUID {
        val source = data.getString(KEY_SOURCE).orEmpty()
        val download = source in DOWNLOAD_SOURCES
        // A download keeps its archive across runs, so that a run after a break continues it.
        val input = if (download && data.getString(KEY_ARCHIVE) == null) {
            Data.Builder().putAll(data).putString(KEY_ARCHIVE, storage.newArchiveName()).build()
        } else {
            data
        }
        val retries = input.getInt(KEY_RETRIES, 0)
        val chain = leastLoadedChain(workManager.getWorkInfosByTagFlow(TAG).first(), preferences.data.first()[CANCELLED].orEmpty(), exclude)
        val request = OneTimeWorkRequestBuilder<DictionaryImportWorker>()
            .setInputData(input)
            .setInputMerger(OwnInputMerger::class.java)
            .addTag(TAG)
            .addTag(NAME_TAG_PREFIX + name)
            .addTag(SOURCE_TAG_PREFIX + source)
            .addTag(CHAIN_TAG_PREFIX + chain)
            .apply {
                if (language != null && entryId != null) {
                    addTag(LANGUAGE_TAG_PREFIX + language)
                    addTag(ENTRY_TAG_PREFIX + entryId)
                }
                downloadKey(input)?.let { addTag(DOWNLOAD_TAG_PREFIX + it) }
                if (download && input.getString(KEY_URL)?.startsWith(ASSET_URL_PREFIX) != true) {
                    setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    // WorkManager counts every run, also one it stopped for a lost network, and starts the next no
                    // sooner than the backoff after the enqueue: the least linear one (10 s a run) stays behind the
                    // time the runs took, where the default exponential one holds the task back longer after each stop.
                    setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                }
                if (retries > 0) {
                    // A download that broke off tries again after a wait (DictionaryImportWorker.MAX_RETRIES); an
                    // expedited task cannot wait.
                    addTag(RETRY_TAG_PREFIX + retries)
                    setInitialDelay(RETRY_DELAY_SECONDS shl (retries - 1), TimeUnit.SECONDS)
                } else {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .addTag(ORDER_TAG_PREFIX + lastOrder.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) })
            // Empty for imports that make their own temporary archive; see archivesInUse.
            .addTag(ARCHIVE_TAG_PREFIX + (input.getString(KEY_PATH)?.let { File(it).name } ?: input.getString(KEY_ARCHIVE)).orEmpty())
            .build()
        // Awaited, so that the next task queued sees this one.
        workManager.enqueueUniqueWork(QUEUES[chain], ExistingWorkPolicy.APPEND_OR_REPLACE, request).await()
        Log.i(LOG_TAG, "Task ${request.id} ($source) queued on chain $chain" + if (retries > 0) ", retry $retries" else "")
        return request.id
    }

    private suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }

    internal companion object {
        const val TAG = "dictionary-import"
        private const val LOG_TAG = "DictionaryImports"
        const val NAME_TAG_PREFIX = "dictionary-import-name:"
        const val SOURCE_TAG_PREFIX = "dictionary-import-source:"
        const val LANGUAGE_TAG_PREFIX = "dictionary-import-language:"
        const val ENTRY_TAG_PREFIX = "dictionary-import-entry:"

        /** Followed by a number that grows with every enqueued import; work ids are random. */
        const val ORDER_TAG_PREFIX = "dictionary-import-order:"

        /** Followed by the file name of the archive the import reads, or nothing. */
        const val ARCHIVE_TAG_PREFIX = "dictionary-import-archive:"

        /** Followed by what the download gets ([downloadKey]); on download tasks only. */
        const val DOWNLOAD_TAG_PREFIX = "dictionary-import-download:"

        /** Followed by the index of the task's chain in [QUEUES]; tasks queued without one are on the first. */
        const val CHAIN_TAG_PREFIX = "dictionary-import-chain:"

        /** Followed by the retry number of a download that broke off; see [requeue]. */
        const val RETRY_TAG_PREFIX = "dictionary-import-retry:"

        /** The chains of tasks, each run one task after another; see [add]. */
        val QUEUES = listOf("dictionary-imports", "dictionary-imports-2")

        /** Ids of the tasks the user cancelled that have not ended yet. */
        val CANCELLED = stringSetPreferencesKey("dictionary_imports_cancelled")

        /**
         * Runs of each unfinished task that do not count as runs the app died in, as `id=count`: WorkManager stopped
         * them, they were downloading, or they ended for a retry; see [markStopped].
         */
        val STOPPED = stringSetPreferencesKey("dictionary_imports_stopped")

        /**
         * Outputs of tasks that ended, as `id=` and the output's bytes in Base64, kept until their works have finished;
         * see [recordEnd].
         */
        val ENDED = stringSetPreferencesKey("dictionary_imports_ended")

        /** Retries of each unfinished download as earlier versions kept them, as `id=count`; removed when found. */
        val RETRIES = stringSetPreferencesKey("dictionary_imports_retries")

        /** Ids of finished tasks the user dismissed or resumed, kept out of [tasks]. */
        val DISMISSED = stringSetPreferencesKey("dictionary_imports_dismissed")

        /** Sources whose tasks download their archive and may continue it; see [ImportTask.resumable]. */
        val DOWNLOAD_SOURCES = setOf(SOURCE_URL, SOURCE_MODEL)

        /** The first wait before a download that broke off tries again; it doubles with each retry. */
        private const val RETRY_DELAY_SECONDS = 30L

        /** What a stopped download keeps in its output to be queued again; see [resume]. */
        val RESUME_KEYS = listOf(KEY_SOURCE, KEY_URL, KEY_INDEX_URL, KEY_NAME, KEY_REPLACE_ID, KEY_ENTRY_ID, KEY_ARCHIVE)

        /** Reads [STOPPED]; entries that do not parse are dropped. */
        fun readStops(entries: Set<String>): Map<String, Int> = entries.mapNotNull { entry ->
            val count = entry.substringAfterLast('=', "").toIntOrNull() ?: return@mapNotNull null
            entry.substringBeforeLast('=') to count
        }.toMap()

        fun writeStops(stops: Map<String, Int>): Set<String> = stops.mapTo(hashSetOf()) { (id, count) -> "$id=$count" }

        /** [ends] ([ENDED]) with the end of the task [id] recorded and those of tasks not [unfinished] dropped. */
        fun recordedEnds(ends: Set<String>, id: UUID, output: Data, unfinished: Set<String>): Set<String> =
            unfinishedEnds(ends, unfinished - id.toString()) + "$id=${Base64.getEncoder().encodeToString(output.toByteArray())}"

        /** The entries of [ends] ([ENDED]) of the [unfinished] tasks. */
        fun unfinishedEnds(ends: Set<String>, unfinished: Set<String>): Set<String> = ends.filterTo(hashSetOf()) { endId(it) in unfinished }

        /** The output [ends] ([ENDED]) holds for the task [id]; null when none, or when it does not parse. */
        fun endIn(ends: Set<String>, id: UUID): Data? = ends.firstOrNull { endId(it) == id.toString() }?.let { entry ->
            runCatching { Data.fromByteArray(Base64.getDecoder().decode(entry.substringAfter('='))) }.getOrNull()
        }

        private fun endId(entry: String): String = entry.substringBefore('=')
    }
}

/**
 * Archives the unfinished [works] read and those stopped downloads may continue; null when an unfinished one does not
 * say (queued by an older version).
 */
internal fun archivesInUse(works: List<WorkInfo>): Set<String>? =
    works.filter { !it.state.isFinished || it.outputData.getBoolean(KEY_RESUMABLE, false) }.mapTo(mutableSetOf()) { work ->
        archiveOf(work) ?: return null
    }.apply { remove("") }

/** The archive file name the import [work] reads; empty when it makes its own, null when it does not say. */
internal fun archiveOf(work: WorkInfo): String? = work.tagValue(DictionaryImports.ARCHIVE_TAG_PREFIX)

internal fun Set<String>.tagValue(prefix: String): String? = firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

private fun WorkInfo.tagValue(prefix: String): String? = tags.tagValue(prefix)

/** What the download [data] gets, the same for two downloads of one thing: its catalog entry, else its URL. */
internal fun downloadKey(data: Data): String? {
    if (data.getString(KEY_SOURCE) !in DictionaryImports.DOWNLOAD_SOURCES) return null
    return data.getString(KEY_ENTRY_ID)?.let { "entry:$it" } ?: data.getString(KEY_URL)?.let { "url:$it" }
}

/**
 * The unfinished task of the download [key], or else its last stopped one, which may be resumed; tasks the user
 * [cancelled] or [dismissed] are left out.
 */
internal fun sameDownload(works: List<WorkInfo>, key: String?, cancelled: Set<String>, dismissed: Set<String>): WorkInfo? {
    if (key == null) return null
    val same = works.filter {
        it.tagValue(DictionaryImports.DOWNLOAD_TAG_PREFIX) == key && it.state != WorkInfo.State.CANCELLED &&
            it.id.toString() !in cancelled && it.id.toString() !in dismissed
    }
    return same.firstOrNull { !it.state.isFinished } ?: same.filter { it.outputData.getBoolean(KEY_RESUMABLE, false) }.maxByOrNull { it.order() }
}

/** The chain with the fewest unfinished tasks, those the user [cancelled] and [exclude] left out; the first on a tie. */
internal fun leastLoadedChain(works: List<WorkInfo>, cancelled: Set<String>, exclude: UUID? = null): Int {
    val loads = IntArray(DictionaryImports.QUEUES.size)
    works.filter { !it.state.isFinished && it.id != exclude && it.id.toString() !in cancelled }.forEach { loads[it.chain()]++ }
    return loads.indices.minBy { loads[it] }
}

private fun WorkInfo.chain(): Int =
    tagValue(DictionaryImports.CHAIN_TAG_PREFIX)?.toIntOrNull()?.takeIf { it in DictionaryImports.QUEUES.indices } ?: 0

/**
 * The import works as tasks, in queue order; those the user cancelled ([cancelled] ids), those a dictionary reset
 * cancelled and downloads queued again for a retry are left out.
 */
internal fun importTasks(
    infos: List<WorkInfo>,
    cancelled: Set<String> = emptySet(),
    dismissed: Set<String> = emptySet(),
): List<ImportTask> =
    infos.filterNot {
        it.state == WorkInfo.State.CANCELLED ||
            it.id.toString() in cancelled ||
            it.id.toString() in dismissed ||
            it.outputData.getBoolean(KEY_CANCELLED, false) ||
            it.outputData.getBoolean(KEY_REQUEUED, false)
    }
        .sortedWith(compareBy<WorkInfo> { it.order() }.thenBy { it.id })
        .map { it.toTask() }

private fun WorkInfo.order(): Long =
    tags.firstOrNull { it.startsWith(DictionaryImports.ORDER_TAG_PREFIX) }
        ?.removePrefix(DictionaryImports.ORDER_TAG_PREFIX)?.toLongOrNull() ?: 0L

private fun WorkInfo.toTask(): ImportTask {
    val data = if (state.isFinished) outputData else progress
    val name = progress.getString(KEY_NAME)
        ?: tags.firstOrNull { it.startsWith(DictionaryImports.NAME_TAG_PREFIX) }
            ?.removePrefix(DictionaryImports.NAME_TAG_PREFIX).orEmpty()
    val error = outputData.getString(KEY_ERROR)
    val interrupted = outputData.getBoolean(KEY_INTERRUPTED, false)
    val taskState = when (state) {
        // The worker reports its failures as results with an error.
        WorkInfo.State.SUCCEEDED -> if (error != null || interrupted) ImportTask.State.FAILED else ImportTask.State.SUCCEEDED
        WorkInfo.State.FAILED -> ImportTask.State.FAILED
        WorkInfo.State.RUNNING -> when (progress.getString(KEY_STAGE)) {
            STAGE_DOWNLOAD -> ImportTask.State.DOWNLOADING
            STAGE_CHECK -> ImportTask.State.CHECKING_SPACE
            STAGE_CONVERT -> ImportTask.State.CONVERTING
            // Its download is done; another task imports.
            STAGE_WAIT -> ImportTask.State.QUEUED
            else -> ImportTask.State.IMPORTING
        }
        else -> ImportTask.State.QUEUED
    }
    return ImportTask(
        id = id,
        name = name,
        state = taskState,
        percent = progress.getInt(KEY_PERCENT, -1).takeIf { !state.isFinished && it >= 0 },
        titles = data.getNullableStringArray(KEY_TITLES)?.filterNotNull().orEmpty(),
        error = error,
        shortage = outputData.getLong(KEY_NEEDED_BYTES, -1).takeIf { it >= 0 }?.let { needed ->
            CollectionSpacePlan.NotEnough(needed, outputData.getLong(KEY_FREE_BYTES, 0))
        },
        interrupted = interrupted,
        paused = outputData.getBoolean(KEY_PAUSED, false),
        source = tags.firstOrNull { it.startsWith(DictionaryImports.SOURCE_TAG_PREFIX) }
            ?.removePrefix(DictionaryImports.SOURCE_TAG_PREFIX)
            ?: progress.getString(KEY_SOURCE),
        language = tags.firstOrNull { it.startsWith(DictionaryImports.LANGUAGE_TAG_PREFIX) }?.removePrefix(DictionaryImports.LANGUAGE_TAG_PREFIX),
        catalogEntry = tags.firstOrNull { it.startsWith(DictionaryImports.ENTRY_TAG_PREFIX) }?.removePrefix(DictionaryImports.ENTRY_TAG_PREFIX),
        resumable = taskState == ImportTask.State.FAILED && outputData.getBoolean(KEY_RESUMABLE, false),
        downloadStopped = outputData.getBoolean(KEY_DOWNLOAD_STOPPED, false),
        connectionLost = outputData.getBoolean(KEY_CONNECTION_LOST, false),
        retries = if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED) {
            tagValue(DictionaryImports.RETRY_TAG_PREFIX)?.toIntOrNull() ?: 0
        } else {
            0
        },
        needsNetwork = (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED) &&
            constraints.requiredNetworkType != NetworkType.NOT_REQUIRED,
    )
}
