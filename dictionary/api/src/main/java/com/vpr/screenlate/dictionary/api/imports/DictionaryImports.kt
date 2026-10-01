package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_DELETE_FILE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ERROR
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_FREE_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_INDEX_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NAME
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NEEDED_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PATH
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PERCENT
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_REPLACE_ID
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SELECTED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SIZE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SOURCE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_STAGE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_TITLES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URI
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_BUNDLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_FILE
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
) {
    enum class State { QUEUED, DOWNLOADING, CHECKING_SPACE, CONVERTING, IMPORTING, SUCCEEDED, FAILED }

    val finished: Boolean get() = state == State.SUCCEEDED || state == State.FAILED
}

/** Queues dictionary imports on WorkManager, one at a time, and reports their progress. */
@Singleton
class DictionaryImports @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: DictionaryStorage,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** The queue position of the last enqueued import; see [ORDER_TAG_PREFIX]. */
    private val lastOrder = AtomicLong()

    /** Import workers running in this process; a cancelled one runs on until its current step returns. */
    private val running = MutableStateFlow(0)

    private val leftoversClaimed = AtomicBoolean()

    /** Imports in the order they were queued. */
    val tasks: Flow<List<ImportTask>> = workManager.getWorkInfosByTagFlow(TAG).map(::importTasks)

    /** Installs bundled dictionaries that are not installed yet; returns the task's id. Cheap when there is nothing to do. */
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
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
            input.use { target.outputStream().use(it::copyTo) }
        }
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
    fun download(url: String, name: String, indexUrl: String? = null, replaces: Long? = null) = enqueue(
        workDataOf(
            KEY_SOURCE to SOURCE_URL,
            KEY_URL to url,
            KEY_INDEX_URL to indexUrl,
            KEY_NAME to name,
            KEY_REPLACE_ID to (replaces ?: -1L),
        ),
        name = name,
    )

    /** Removes finished tasks from [tasks]. */
    fun clearFinished() {
        workManager.pruneWork()
    }

    /**
     * Cancels every queued and running import and returns once the running one has stopped, so nothing it was
     * writing lands later; the cancelled tasks are removed from [tasks].
     */
    suspend fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG).await()
        running.first { it == 0 }
        workManager.pruneWork().await()
    }

    /** True for the first import of this process only, which removes what earlier processes left behind. */
    internal fun firstOfProcess(): Boolean = leftoversClaimed.compareAndSet(false, true)

    /** Runs a worker's [block], counted for [cancelAll]. */
    internal suspend fun <T> tracked(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    private fun enqueue(data: Data, name: String): UUID {
        val request = OneTimeWorkRequestBuilder<DictionaryImportWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag(NAME_TAG_PREFIX + name)
            .addTag(ORDER_TAG_PREFIX + lastOrder.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) })
            // Empty for imports that make their own temporary archive; see archivesInUse.
            .addTag(ARCHIVE_TAG_PREFIX + data.getString(KEY_PATH)?.let { File(it).name }.orEmpty())
            .build()
        // One queue for all imports: the native importer is memory hungry and imports are cheaper one at a time.
        workManager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return request.id
    }

    private suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }

    internal companion object {
        const val TAG = "dictionary-import"
        const val NAME_TAG_PREFIX = "dictionary-import-name:"

        /** Followed by a number that grows with every enqueued import; work ids are random. */
        const val ORDER_TAG_PREFIX = "dictionary-import-order:"

        /** Followed by the file name of the archive the import reads, or nothing. */
        const val ARCHIVE_TAG_PREFIX = "dictionary-import-archive:"
        const val QUEUE = "dictionary-imports"
    }
}

/** Archives the unfinished [works] read; null when one of them does not say (queued by an older version). */
internal fun archivesInUse(works: List<WorkInfo>): Set<String>? =
    works.filterNot { it.state.isFinished }.mapTo(mutableSetOf()) { work ->
        work.tags.firstOrNull { it.startsWith(DictionaryImports.ARCHIVE_TAG_PREFIX) }
            ?.removePrefix(DictionaryImports.ARCHIVE_TAG_PREFIX) ?: return null
    }.apply { remove("") }

/** The import works as tasks, in queue order. */
internal fun importTasks(infos: List<WorkInfo>): List<ImportTask> =
    infos.sortedWith(compareBy<WorkInfo> { it.order() }.thenBy { it.id }).map { it.toTask() }

private fun WorkInfo.order(): Long =
    tags.firstOrNull { it.startsWith(DictionaryImports.ORDER_TAG_PREFIX) }
        ?.removePrefix(DictionaryImports.ORDER_TAG_PREFIX)?.toLongOrNull() ?: 0L

private fun WorkInfo.toTask(): ImportTask {
    val data = if (state.isFinished) outputData else progress
    val name = progress.getString(KEY_NAME)
        ?: tags.firstOrNull { it.startsWith(DictionaryImports.NAME_TAG_PREFIX) }
            ?.removePrefix(DictionaryImports.NAME_TAG_PREFIX).orEmpty()
    val error = outputData.getString(KEY_ERROR)
    val taskState = when (state) {
        // The worker reports its failures as results with an error.
        WorkInfo.State.SUCCEEDED -> if (error != null) ImportTask.State.FAILED else ImportTask.State.SUCCEEDED
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> ImportTask.State.FAILED
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
    )
}
