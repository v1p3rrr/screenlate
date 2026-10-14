package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
import androidx.work.await
import androidx.work.workDataOf
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
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_RESUMABLE
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
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
    /** Automatic retries of a download so far; a queued task with some waits for the network or its next try. */
    val retries: Int = 0,
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

/** Queues dictionary imports on WorkManager, one at a time, and reports their progress. */
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

    /** Imports in the order they were queued; cancelled and dismissed ones are left out. */
    val tasks: Flow<List<ImportTask>> = combine(
        workManager.getWorkInfosByTagFlow(TAG),
        // Every settings write emits; the tasks change only with these.
        preferences.data.map { TaskMarks(it[CANCELLED].orEmpty(), it[DISMISSED].orEmpty(), readStops(it[RETRIES].orEmpty())) }
            .distinctUntilChanged(),
    ) { infos, marks -> importTasks(infos, marks.cancelled, marks.dismissed, marks.retries) }

    private data class TaskMarks(val cancelled: Set<String>, val dismissed: Set<String>, val retries: Map<String, Int>)

    /**
     * Installs bundled dictionaries that are not installed yet; returns the task's id. Cheap when there is nothing to
     * do or the install is paused ([BundledDictionaries.pauseUntilUpdate]).
     */
    fun installBundled(): UUID = enqueue(workDataOf(KEY_SOURCE to SOURCE_BUNDLED), name = "")

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

    fun importFile(archive: File, name: String, deleteAfter: Boolean) = enqueue(
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
     * [replaces] is the id of the installed dictionary this download updates.
     */
    fun download(url: String, name: String, indexUrl: String? = null, replaces: Long? = null, entry: CatalogEntry? = null) = enqueue(
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

    /** Downloads and installs an on-device recognition model of the catalog ([CatalogEntry.isModel]). */
    fun downloadModel(entry: CatalogEntry) = enqueue(
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
        val info = workManager.getWorkInfoByIdFlow(id).first() ?: return
        val output = info.outputData
        if (!output.getBoolean(KEY_RESUMABLE, false)) return
        val data = Data.Builder().apply {
            RESUME_KEYS.forEach { key -> output.keyValueMap[key]?.let { put(key, it) } }
        }.build()
        preferences.edit { it[DISMISSED] = it[DISMISSED].orEmpty() + id.toString() }
        Log.i(LOG_TAG, "Task $id resumed")
        enqueue(
            data,
            name = data.getString(KEY_NAME).orEmpty(),
            language = info.tagValue(LANGUAGE_TAG_PREFIX),
            entryId = info.tagValue(ENTRY_TAG_PREFIX),
        )
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
            val retries = readStops(prefs[RETRIES].orEmpty())
            if (retries.keys.any { it !in unfinished }) prefs[RETRIES] = writeStops(retries.filterKeys { it in unfinished })
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

    /** Records an automatic retry of the download [id]. */
    internal suspend fun markRetry(id: UUID) {
        preferences.edit { prefs ->
            val retries = readStops(prefs[RETRIES].orEmpty())
            prefs[RETRIES] = writeStops(retries + (id.toString() to (retries[id.toString()] ?: 0) + 1))
        }
    }

    /** Automatic retries of the download [id] so far. */
    internal suspend fun retriesOf(id: UUID): Int = readStops(preferences.data.first()[RETRIES].orEmpty())[id.toString()] ?: 0

    /** Forgets the stops and retries of the task [id], which has ended. */
    internal suspend fun forgetStops(id: UUID) {
        preferences.edit { prefs ->
            val stops = readStops(prefs[STOPPED].orEmpty())
            if (id.toString() in stops) prefs[STOPPED] = writeStops(stops - id.toString())
            val retries = readStops(prefs[RETRIES].orEmpty())
            if (id.toString() in retries) prefs[RETRIES] = writeStops(retries - id.toString())
        }
    }

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

    private fun enqueue(data: Data, name: String, language: String? = null, entryId: String? = null): UUID {
        val source = data.getString(KEY_SOURCE).orEmpty()
        val download = source in DOWNLOAD_SOURCES
        // A download keeps its archive across runs, so that a run after a break continues it.
        val input = if (download && data.getString(KEY_ARCHIVE) == null) {
            Data.Builder().putAll(data).putString(KEY_ARCHIVE, storage.newArchiveName()).build()
        } else {
            data
        }
        val request = OneTimeWorkRequestBuilder<DictionaryImportWorker>()
            .setInputData(input)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag(NAME_TAG_PREFIX + name)
            .addTag(SOURCE_TAG_PREFIX + source)
            .apply {
                if (language != null && entryId != null) {
                    addTag(LANGUAGE_TAG_PREFIX + language)
                    addTag(ENTRY_TAG_PREFIX + entryId)
                }
                if (download) {
                    // Waits for a network; one that broke off tries again (DictionaryImportWorker.MAX_RETRIES).
                    setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
                }
            }
            .addTag(ORDER_TAG_PREFIX + lastOrder.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) })
            // Empty for imports that make their own temporary archive; see archivesInUse.
            .addTag(ARCHIVE_TAG_PREFIX + (input.getString(KEY_PATH)?.let { File(it).name } ?: input.getString(KEY_ARCHIVE)).orEmpty())
            .build()
        // One queue for all imports: the native importer is memory hungry and imports are cheaper one at a time.
        workManager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        Log.i(LOG_TAG, "Task ${request.id} (${data.getString(KEY_SOURCE)}) queued")
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
        const val QUEUE = "dictionary-imports"

        /** Ids of the tasks the user cancelled that have not ended yet. */
        val CANCELLED = stringSetPreferencesKey("dictionary_imports_cancelled")

        /**
         * Runs of each unfinished task that do not count as runs the app died in, as `id=count`: WorkManager stopped
         * them, they were downloading, or they ended for a retry; see [markStopped].
         */
        val STOPPED = stringSetPreferencesKey("dictionary_imports_stopped")

        /** Automatic retries of each unfinished download, as `id=count`; see [markRetry]. */
        val RETRIES = stringSetPreferencesKey("dictionary_imports_retries")

        /** Ids of finished tasks the user dismissed or resumed, kept out of [tasks]. */
        val DISMISSED = stringSetPreferencesKey("dictionary_imports_dismissed")

        /** Sources whose tasks download their archive and may continue it; see [ImportTask.resumable]. */
        private val DOWNLOAD_SOURCES = setOf(SOURCE_URL, SOURCE_MODEL)

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

private fun WorkInfo.tagValue(prefix: String): String? = tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

/**
 * The import works as tasks, in queue order; those the user cancelled ([cancelled] ids) and those a dictionary reset
 * cancelled are left out.
 */
internal fun importTasks(
    infos: List<WorkInfo>,
    cancelled: Set<String> = emptySet(),
    dismissed: Set<String> = emptySet(),
    retries: Map<String, Int> = emptyMap(),
): List<ImportTask> =
    infos.filterNot {
        it.state == WorkInfo.State.CANCELLED ||
            it.id.toString() in cancelled ||
            it.id.toString() in dismissed ||
            it.outputData.getBoolean(KEY_CANCELLED, false)
    }
        .sortedWith(compareBy<WorkInfo> { it.order() }.thenBy { it.id })
        .map { it.toTask(retries[it.id.toString()] ?: 0) }

private fun WorkInfo.order(): Long =
    tags.firstOrNull { it.startsWith(DictionaryImports.ORDER_TAG_PREFIX) }
        ?.removePrefix(DictionaryImports.ORDER_TAG_PREFIX)?.toLongOrNull() ?: 0L

private fun WorkInfo.toTask(retries: Int): ImportTask {
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
        retries = if (state.isFinished) 0 else retries,
    )
}
