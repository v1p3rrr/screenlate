package com.vpr.screenlate.dictionary.api.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_DELETE_FILE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ERROR
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NAME
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PATH
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PERCENT
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SOURCE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_STAGE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_TITLES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_BUNDLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_FILE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_DOWNLOAD
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** State of one queued, running or finished import as shown in the UI. */
data class ImportTask(
    val id: UUID,
    val name: String,
    val state: State,
    /** Download progress in percent, or null when unknown or not downloading. */
    val downloadPercent: Int?,
    val titles: List<String>,
    val error: String?,
) {
    enum class State { QUEUED, DOWNLOADING, IMPORTING, SUCCEEDED, FAILED }

    val finished: Boolean get() = state == State.SUCCEEDED || state == State.FAILED
}

/** Queues dictionary imports on WorkManager, one at a time, and reports their progress. */
@Singleton
class DictionaryImports @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: DictionaryStorage,
) {
    private val workManager get() = WorkManager.getInstance(context)

    val tasks: Flow<List<ImportTask>> = workManager.getWorkInfosByTagFlow(TAG).map { infos ->
        infos.sortedBy { it.id }.map { it.toTask() }
    }

    /** Installs bundled dictionaries that are not installed yet. Cheap when there is nothing to do. */
    fun installBundled() = enqueue(workDataOf(KEY_SOURCE to SOURCE_BUNDLED), name = "")

    /** Copies the archive behind [uri] into app storage and queues its import. */
    suspend fun importFrom(uri: Uri) {
        val name = displayName(uri)
        val archive = storage.newArchiveFile()
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
            input.use { archive.outputStream().use(it::copyTo) }
        }
        importFile(archive, name, deleteAfter = true)
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

    fun download(url: String, name: String) =
        enqueue(workDataOf(KEY_SOURCE to SOURCE_URL, KEY_URL to url, KEY_NAME to name), name = name)

    /** Removes finished tasks from [tasks]. */
    fun clearFinished() {
        workManager.pruneWork()
    }

    private fun enqueue(data: Data, name: String) {
        val request = OneTimeWorkRequestBuilder<DictionaryImportWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag(NAME_TAG_PREFIX + name)
            .build()
        // One queue for all imports: the native importer is memory hungry and imports are cheaper one at a time.
        workManager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }

    private fun WorkInfo.toTask(): ImportTask {
        val data = if (state.isFinished) outputData else progress
        val name = progress.getString(KEY_NAME)
            ?: tags.firstOrNull { it.startsWith(NAME_TAG_PREFIX) }?.removePrefix(NAME_TAG_PREFIX).orEmpty()
        val taskState = when (state) {
            WorkInfo.State.SUCCEEDED -> ImportTask.State.SUCCEEDED
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> ImportTask.State.FAILED
            WorkInfo.State.RUNNING ->
                if (progress.getString(KEY_STAGE) == STAGE_DOWNLOAD) ImportTask.State.DOWNLOADING
                else ImportTask.State.IMPORTING
            else -> ImportTask.State.QUEUED
        }
        return ImportTask(
            id = id,
            name = name,
            state = taskState,
            downloadPercent = progress.getInt(KEY_PERCENT, -1).takeIf { taskState == ImportTask.State.DOWNLOADING && it >= 0 },
            titles = data.getStringArray(KEY_TITLES)?.toList().orEmpty(),
            error = outputData.getString(KEY_ERROR),
        )
    }

    private companion object {
        const val TAG = "dictionary-import"
        const val NAME_TAG_PREFIX = "dictionary-import-name:"
        const val QUEUE = "dictionary-imports"
    }
}
