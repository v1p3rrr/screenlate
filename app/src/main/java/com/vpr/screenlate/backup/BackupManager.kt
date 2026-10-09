package com.vpr.screenlate.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vpr.screenlate.BuildConfig
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import com.vpr.screenlate.dictionary.api.registry.keptTermDictionaries
import com.vpr.screenlate.overlay.fonts.PopupFonts
import com.vpr.screenlate.settings.EInkSizes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface BackupState {
    data object Idle : BackupState

    /** @property progress 0..1, or null while the size is unknown. */
    data class Working(val restoring: Boolean, val progress: Float?) : BackupState

    data class Created(val bytes: Long, val dictionaries: Int) : BackupState

    /**
     * A backup chosen for restoring.
     *
     * @property dictionaryFiles titles of the dictionaries whose files it holds.
     */
    data class Loaded(
        val uri: Uri,
        val manifest: BackupManifest,
        val dictionaries: Int,
        val dictionaryFiles: List<String>,
    ) : BackupState

    data class Restored(val summary: RestoreSummary) : BackupState

    data class Failed(val reason: BackupFailure) : BackupState
}

enum class BackupFailure { NOT_BACKUP, NEWER_VERSION, WRITE, READ }

/**
 * @property settings sections restored from the settings file.
 * @property dictionaryFiles dictionaries added or replaced from the files.
 * @property failed dictionaries whose files could not be restored.
 * @property missing dictionaries in the list that are neither installed nor in the backup's files.
 */
data class RestoreSummary(
    val settings: List<BackupSection>,
    val fonts: Int?,
    val dictionaryFiles: Int?,
    val failed: List<String>,
    val listApplied: Boolean,
    val missing: List<String>,
    val keptOn: List<String> = emptyList(),
)

/**
 * Creates and restores backups: the settings, the dictionary list and installed fonts, and optionally the
 * dictionaries in the app's own format. Runs in its own scope, so leaving the screen does not stop it.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
    private val dictionaries: DictionaryRepository,
    private val storage: DictionaryStorage,
    private val fonts: PopupFonts,
    private val appSettings: AppSettingsRepository,
    private val eInkSizes: EInkSizes,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = mutableState
    private var job: Job? = null

    private val busy: Boolean get() = job?.isActive == true

    fun reset() {
        if (!busy) mutableState.value = BackupState.Idle
    }

    /** Writes a backup into the document at [uri]; [includeDictionaries] adds the dictionary files. */
    fun create(uri: Uri, includeDictionaries: Boolean) {
        if (busy) return
        job = scope.launch {
            mutableState.value = BackupState.Working(restoring = false, progress = null)
            val result = runCatching {
                val installed = dictionaries.getAll()
                val sortId = dictionaries.sortDictionaryId.first()
                val withFiles = if (includeDictionaries) installed.filter { storage.hasFiles(it) }.map { it.id }.toSet() else emptySet()
                val sections = BackupSection.entries.filter { it != BackupSection.DICTIONARY_FILES || withFiles.isNotEmpty() }
                val contents = BackupArchive.Contents(
                    manifest = BackupManifest(appVersion = BuildConfig.VERSION_NAME, createdAt = System.currentTimeMillis(), sections = sections),
                    settings = BackupPreferences.encode(preferences.data.first()),
                    dictionaries = BackupDictionaryList(
                        dictionaries = installed.map { BackupDictionary.of(it, files = it.id in withFiles) },
                        sortDictionary = installed.firstOrNull { it.id == sortId }?.title,
                    ),
                    fonts = fonts.backupFiles(),
                    dictionaryFiles = installed.withIndex()
                        .filter { it.value.id in withFiles }
                        .associate { it.index to storage.directoryOf(it.value) },
                )
                val total = BackupArchive.size(contents)
                if (total > 0) mutableState.value = BackupState.Working(restoring = false, progress = 0f)
                val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output")
                output.use {
                    BackupArchive.write(it, contents) { written ->
                        if (total > 0) mutableState.value = BackupState.Working(false, written.toFloat() / total)
                    }
                }
                BackupState.Created(bytes = size(uri) ?: 0, dictionaries = withFiles.size)
            }
            mutableState.value = result.getOrElse { error ->
                Log.w(TAG, "Creating the backup failed", error.redacted())
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                BackupState.Failed(BackupFailure.WRITE)
            }
        }
    }

    /** Reads what the backup at [uri] holds, to offer its sections. */
    fun open(uri: Uri) {
        if (busy) return
        job = scope.launch {
            mutableState.value = BackupState.Working(restoring = true, progress = null)
            mutableState.value = runCatching {
                val head = context.contentResolver.openInputStream(uri)?.use { BackupArchive.readHead(it) } ?: error("No input")
                BackupState.Loaded(
                    uri = uri,
                    manifest = head.manifest,
                    dictionaries = head.dictionaries.dictionaries.size,
                    dictionaryFiles = head.dictionaries.dictionaries.filter { it.files }.map { it.title },
                )
            }.getOrElse { error ->
                Log.w(TAG, "Reading the backup failed", error.redacted())
                BackupState.Failed(failure(error))
            }
        }
    }

    /** Replaces the chosen [sections] with the backup's. */
    fun restore(uri: Uri, sections: Set<BackupSection>) {
        if (busy) return
        job = scope.launch {
            mutableState.value = BackupState.Working(restoring = true, progress = null)
            mutableState.value = runCatching { BackupState.Restored(restoreFrom(uri, sections)) }.getOrElse { error ->
                Log.w(TAG, "Restoring the backup failed", error.redacted())
                BackupState.Failed(failure(error))
            }
        }
    }

    private suspend fun restoreFrom(uri: Uri, sections: Set<BackupSection>): RestoreSummary {
        val total = size(uri)
        val fontDirectory = File(context.cacheDir, "backup-fonts-${UUID.randomUUID()}").apply { mkdirs() }
        val staging = mutableMapOf<Int, File>()
        val restoredFiles = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var fontCount: Int? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("No input")
            var list = BackupDictionaryList(emptyList())
            val head = Counting(input) { read ->
                if (total != null && total > 0) mutableState.value = BackupState.Working(true, read.toFloat() / total)
            }.use { counted ->
                BackupArchive.read(
                    counted,
                    dictionaryFiles = BackupSection.DICTIONARY_FILES in sections,
                    reader = object : BackupArchive.Reader {
                        override suspend fun font(name: String, input: InputStream) {
                            if (BackupSection.POPUP !in sections) return
                            File(fontDirectory, name).outputStream().use { input.copyTo(it) }
                        }

                        override suspend fun dictionaryFile(index: Int, path: String, input: InputStream) {
                            if (BackupSection.DICTIONARY_FILES !in sections) return
                            val directory = staging.getOrPut(index) { storage.newStagingDirectory() }
                            val file = File(directory, path)
                            file.parentFile?.mkdirs()
                            file.outputStream().use { input.copyTo(it) }
                        }

                        override suspend fun dictionaryDone(index: Int) {
                            val directory = staging.remove(index) ?: return
                            // The dictionary list comes before any file, so it is known here.
                            val dictionary = list.dictionaries.getOrNull(index)
                            if (dictionary == null) {
                                directory.deleteRecursively()
                                return
                            }
                            runCatching { dictionaries.restore(dictionary.toEntity(directory = ""), directory) }
                                .onSuccess { restoredFiles += dictionary.title }
                                .onFailure {
                                    Log.w(TAG, "Restoring a dictionary failed", it.redacted())
                                    failed += dictionary.title
                                    directory.deleteRecursively()
                                }
                        }
                    },
                    onHead = { head ->
                        list = head.dictionaries
                        restoreSettings(head, sections)
                    },
                )
            }
            if (BackupSection.POPUP in sections) fontCount = fonts.restore(fontDirectory)
            var missing = emptyList<String>()
            var keptOn = emptyList<String>()
            if (BackupSection.DICTIONARY_LIST in sections) {
                val installed = dictionaries.getAll()
                val outcome = BackupDictionaries.applyList(installed, head.dictionaries.dictionaries)
                val sortId = head.dictionaries.sortDictionary?.let { BackupDictionaries.match(outcome.updated, it)?.id }
                val kept = keptTermDictionaries(installed, outcome.updated, storage::hasFiles).mapTo(hashSetOf()) { it.id }
                dictionaries.applyStates(outcome.updated.map { if (it.id in kept) it.copy(enabled = true) else it }, sortId)
                missing = outcome.missing
                keptOn = outcome.updated.filter { it.id in kept }.map { it.title }
            }
            return RestoreSummary(
                settings = sections.filter { it in PREFERENCE_SECTIONS && it in head.manifest.sections },
                fonts = fontCount,
                dictionaryFiles = restoredFiles.size.takeIf { BackupSection.DICTIONARY_FILES in sections },
                failed = failed,
                listApplied = BackupSection.DICTIONARY_LIST in sections,
                missing = missing,
                keptOn = keptOn,
            )
        } finally {
            fontDirectory.deleteRecursively()
            staging.values.forEach { it.deleteRecursively() }
        }
    }

    private suspend fun restoreSettings(head: BackupArchive.Head, sections: Set<BackupSection>) {
        if (sections.none { it in PREFERENCE_SECTIONS }) return
        val backup = BackupPreferences.decode(head.settings)
        // E-ink mode turned off by the backup brings back the sizes it enlarged, as the switch does; sizes the backup
        // holds are written after that and win.
        if (BackupSection.GENERAL in sections && !BackupPreferences.eInk(backup) && appSettings.eInk.first()) eInkSizes.restore()
        preferences.edit { BackupPreferences.restore(it, backup, sections) }
    }

    private fun failure(error: Throwable): BackupFailure = when (error) {
        is BackupArchive.NotBackupException, is java.util.zip.ZipException -> BackupFailure.NOT_BACKUP
        is BackupArchive.NewerFormatException -> BackupFailure.NEWER_VERSION
        else -> BackupFailure.READ
    }

    private fun size(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    /** Reports the bytes read so far. */
    private class Counting(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var total = 0L
        private var reported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it.toLong()) }

        private fun count(bytes: Long) {
            total += bytes
            if (total - reported >= REPORT_STEP) {
                reported = total
                onRead(total)
            }
        }
    }

    companion object {
        private const val TAG = "Backup"
        private const val REPORT_STEP = 1L shl 20

        /** Sections kept in the settings file. */
        val PREFERENCE_SECTIONS = setOf(
            BackupSection.GENERAL,
            BackupSection.BUBBLE,
            BackupSection.LOOKUP,
            BackupSection.POPUP,
            BackupSection.ANKI,
            BackupSection.AUDIO,
            BackupSection.TRANSLATION,
        )
    }
}
