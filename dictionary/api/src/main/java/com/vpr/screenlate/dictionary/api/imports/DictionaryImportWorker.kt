package com.vpr.screenlate.dictionary.api.imports

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.R
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    httpClient: OkHttpClient,
) : CoroutineWorker(context, params) {
    private val downloadClient = httpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val name = inputData.getString(KEY_NAME).orEmpty()
        runCatching { setForeground(foregroundInfo(name)) }
        try {
            val titles = when (inputData.getString(KEY_SOURCE)) {
                SOURCE_BUNDLED -> installBundled()
                SOURCE_FILE -> listOf(importFile(File(requireNotNull(inputData.getString(KEY_PATH)))))
                SOURCE_YOMITAN_BACKUP -> importBackup(File(requireNotNull(inputData.getString(KEY_PATH))))
                SOURCE_URL -> {
                    val url = inputData.getString(KEY_INDEX_URL)?.let { latestDownloadUrl(it) }
                        ?: requireNotNull(inputData.getString(KEY_URL))
                    listOf(download(url, name))
                }
                else -> error("Unknown import source")
            }
            Result.success(workDataOf(KEY_TITLES to titles.toTypedArray()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: DictionaryImportException) {
            Log.w(TAG, "Import failed", e)
            Result.failure(workDataOf(KEY_ERROR to e.message))
        } catch (e: IOException) {
            Log.w(TAG, "Import failed", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private suspend fun installBundled(): List<String> {
        repository.cleanUp()
        return bundled.pending().map { asset ->
            setProgress(workDataOf(KEY_NAME to asset.displayName, KEY_STAGE to STAGE_IMPORT))
            val archive = storage.newArchiveFile()
            try {
                bundled.copy(asset, archive)
                val started = System.currentTimeMillis()
                val title = repository.import(archive, bundled = true).title
                bundled.markInstalled(asset)
                Log.i(TAG, "Installed $title in ${System.currentTimeMillis() - started} ms")
                title
            } finally {
                archive.delete()
            }
        }
    }

    private suspend fun importFile(archive: File): String {
        setProgress(workDataOf(KEY_STAGE to STAGE_IMPORT))
        try {
            return repository.import(archive).title
        } finally {
            if (inputData.getBoolean(KEY_DELETE_FILE, false)) archive.delete()
        }
    }

    /** Splits a Yomitan database export into archives and imports them one by one. */
    private suspend fun importBackup(backup: File): List<String> {
        val staging = storage.newStagingDirectory()
        try {
            setProgress(workDataOf(KEY_STAGE to STAGE_CONVERT))
            val archives = backup.inputStream().use { YomitanBackup(staging).convert(it) }
            return archives.map { archive ->
                setProgress(workDataOf(KEY_STAGE to STAGE_IMPORT))
                try {
                    repository.import(archive).title
                } finally {
                    archive.delete()
                }
            }
        } finally {
            staging.deleteRecursively()
            backup.delete()
        }
    }

    /** `downloadUrl` from a Yomitan index file, or null if it cannot be read. */
    private fun latestDownloadUrl(indexUrl: String): String? = runCatching {
        downloadClient.newCall(Request.Builder().url(indexUrl).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            Json.parseToJsonElement(response.body.string()).jsonObject["downloadUrl"]?.jsonPrimitive?.contentOrNull
        }
    }.onFailure { Log.w(TAG, "Cannot read $indexUrl", it) }.getOrNull()

    private suspend fun download(url: String, name: String): String {
        val archive = storage.newArchiveFile()
        try {
            val request = Request.Builder().url(url).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength()
                body.byteStream().use { input ->
                    archive.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        var copied = 0L
                        var reported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val percent = if (total > 0) (copied * 100 / total).toInt() else -1
                            if (percent != reported) {
                                reported = percent
                                setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_DOWNLOAD, KEY_PERCENT to percent))
                            }
                        }
                    }
                }
            }
            setProgress(workDataOf(KEY_NAME to name, KEY_STAGE to STAGE_IMPORT))
            val replaces = inputData.getLong(KEY_REPLACE_ID, -1).takeIf { it >= 0 }
            return repository.import(archive, replaces = replaces).title
        } finally {
            archive.delete()
        }
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
        const val KEY_DELETE_FILE = "delete_file"
        const val KEY_TITLES = "titles"
        const val KEY_ERROR = "error"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"

        const val SOURCE_BUNDLED = "bundled"
        const val SOURCE_FILE = "file"
        const val SOURCE_URL = "url"
        const val SOURCE_YOMITAN_BACKUP = "yomitan_backup"

        const val STAGE_DOWNLOAD = "download"
        const val STAGE_IMPORT = "import"
        const val STAGE_CONVERT = "convert"

        private const val TAG = "DictionaryImport"
        private const val CHANNEL_ID = "dictionary_imports"
        private const val NOTIFICATION_ID = 1001
    }
}
