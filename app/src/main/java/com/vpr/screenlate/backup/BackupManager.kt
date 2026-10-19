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
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import com.vpr.screenlate.dictionary.api.registry.keptTermDictionaries
import com.vpr.screenlate.overlay.fonts.FontBackup
import com.vpr.screenlate.overlay.fonts.InstalledFont
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
import kotlinx.coroutines.withContext

sealed interface BackupState {
    data object Idle : BackupState

    /** @property progress 0..1, or null while the size is unknown. */
    data class Working(val restoring: Boolean, val progress: Float?) : BackupState

    data class Created(val bytes: Long, val dictionaries: Int) : BackupState

    /**
     * A backup chosen for restoring.
     *
     * @property sections what it offers: the manifest's sections, and the fonts of an older backup, which has them
     *   with the popup settings.
     * @property listed the language of each dictionary in its list.
     * @property fonts its fonts, keyed by id.
     * @property dictionaryFiles the dictionaries whose files it holds, keyed by their index in its list.
     * @property languages its languages, keyed by code, with the size of their recognition models.
     */
    data class Loaded(
        val uri: Uri,
        val manifest: BackupManifest,
        val sections: List<BackupSection>,
        val listed: List<String?>,
        val fonts: List<BackupFile>,
        val dictionaryFiles: List<BackupFile>,
        val languages: List<BackupFile>,
    ) : BackupState

    data class Restored(val summary: RestoreSummary) : BackupState

    data class Failed(val reason: BackupFailure) : BackupState
}

enum class BackupFailure { NOT_BACKUP, NEWER_VERSION, WRITE, READ }

/**
 * A font, dictionary or language a backup can hold.
 *
 * @property key what a [FileChoice] keeps: a font's id, a dictionary's title, its index in a backup's list, or a
 *   language's code.
 * @property bytes null when unknown (dictionaries in backups of older versions); for a language, the size of its
 *   recognition models, null without any.
 * @property language the code of the language whose file it is; null for one every language shares.
 */
data class BackupFile(val key: String, val name: String, val bytes: Long?, val language: String? = null)

/**
 * The installed fonts and the dictionaries with files, which a backup can hold, in their order on screen. Those of
 * languages that are not turned on are left out.
 *
 * @property listed the language of each dictionary for the dictionary list.
 * @property languages the turned-on languages, keyed by code, with the size of their recognition models.
 */
data class BackupFiles(
    val fonts: List<BackupFile>,
    val dictionaries: List<BackupFile>,
    val listed: List<String?>,
    val languages: List<BackupFile>,
)

/**
 * @property settings sections restored from the settings file.
 * @property dictionaryFiles dictionaries added or replaced from the files.
 * @property failed dictionaries and recognition models whose files could not be restored.
 * @property missing dictionaries in the list that are neither installed nor in the backup's files.
 * @property models recognition models installed from the backup.
 * @property turnedOn languages the restore turned on.
 */
data class RestoreSummary(
    val settings: List<BackupSection>,
    val fonts: Int?,
    val dictionaryFiles: Int?,
    val failed: List<String>,
    val listApplied: Boolean,
    val missing: List<String>,
    val keptOn: List<String> = emptyList(),
    val models: Int = 0,
    val turnedOn: List<Language> = emptyList(),
)

/**
 * Creates and restores backups of the chosen parts: settings sections, the dictionary list, fonts, and dictionaries
 * in the app's own format, of the chosen languages with their recognition models. Runs in its own scope, so leaving
 * the screen does not stop it.
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
    private val profiles: LanguageProfiles,
    private val models: OcrModelStore,
    private val catalog: DictionaryCatalog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = mutableState
    private var job: Job? = null

    private val busy: Boolean get() = job?.isActive == true

    fun reset() {
        if (!busy) mutableState.value = BackupState.Idle
    }

    /** The fonts, dictionaries and languages a backup can hold now, with their sizes. */
    suspend fun files(): BackupFiles = withContext(Dispatchers.IO) {
        val sizes = fonts.sizes()
        val turnedOn = profiles.current().turnedOn
        val codes = turnedOn.flatMap { listOf(it.code, it.dictionaryCode) }
        val installed = dictionaries.getAll().filter { it.sourceLanguage == null || it.sourceLanguage in codes }
        val modelLanguages = modelLanguages()
        BackupFiles(
            fonts = fonts.installed.value.map { font ->
                BackupFile(font.id, font.family, sizes[font.id], fontLanguage(font))
            }.filter { it.language == null || it.language in codes },
            dictionaries = installed.filter { storage.hasFiles(it) }.map {
                BackupFile(it.title, it.title, bytesOf(storage.directoryOf(it)), it.sourceLanguage)
            },
            listed = installed.map { it.sourceLanguage },
            languages = turnedOn.map { language ->
                val bytes = models.models.value.filter { language.code in modelLanguages(it.id) }.sumOf { it.bytes }
                BackupFile(language.code, language.displayName(), bytes.takeIf { it > 0 })
            },
        )
    }

    /** The codes of the languages whose catalog entries use each recognition model. */
    private fun modelLanguages(): (String) -> List<String> {
        val entries = catalog.localCatalog().entries.filter { it.isModel }
        return { id -> entries.filter { it.model == id }.map { it.sourceLanguage }.distinct() }
    }

    /** The language a catalog font was made for; null for the user's own fonts and fonts offered by their letters. */
    private fun fontLanguage(font: InstalledFont): String? =
        font.catalogId?.let { id -> fonts.catalog.firstOrNull { it.id == id }?.language }

    /**
     * Writes a backup of [sections] into the document at [uri], with the fonts [fontIds] and the files of the
     * dictionaries titled [dictionaryTitles]. Only the shared settings and dictionaries and those of the [languages]
     * (codes) go in, with the languages' recognition models. Without the dictionary list, only the dictionaries whose
     * files go in are listed.
     */
    fun create(uri: Uri, sections: Set<BackupSection>, fontIds: Set<String>, dictionaryTitles: Set<String>, languages: Set<String>) {
        if (busy) return
        job = scope.launch {
            mutableState.value = BackupState.Working(restoring = false, progress = null)
            val result = runCatching {
                val chosen = Language.entries.filterTo(linkedSetOf()) { it.code in languages }
                val installed = dictionaries.getAll().filter { it.sourceLanguage == null || it.sourceLanguage in languages }
                val sortIds = chosen.associateWith { dictionaries.sortDictionaryId(it).first() }
                fun title(id: Long?) = installed.firstOrNull { it.id == id }?.title
                val withFiles = if (BackupSection.DICTIONARY_FILES in sections) {
                    installed.filter { it.title in dictionaryTitles && storage.hasFiles(it) }.map { it.id }.toSet()
                } else {
                    emptySet()
                }
                val withList = BackupSection.DICTIONARY_LIST in sections
                val listed = if (withList) installed else installed.filter { it.id in withFiles }
                val fontFiles = if (BackupSection.FONTS in sections) fonts.backup(fontIds) else null
                val included = sections.filterTo(linkedSetOf()) {
                    when (it) {
                        BackupSection.FONTS -> fontFiles != null
                        BackupSection.DICTIONARY_FILES -> withFiles.isNotEmpty()
                        else -> true
                    }
                }
                val modelLanguages = modelLanguages()
                val chosenModels = models.models.value.filter { model -> modelLanguages(model.id).any { it in languages } }
                val settings = TranslationSettingsRepository.withButton(preferences.data.first()).toMutablePreferences()
                val profile = profiles.current()
                val limited = BackupLanguages.limited(profile, chosen)
                if (limited != null) LanguageProfiles.write(settings, limited) else PROFILE_KEYS.forEach { settings.remove(it) }
                val contents = BackupArchive.Contents(
                    manifest = BackupManifest(
                        appVersion = BuildConfig.VERSION_NAME,
                        createdAt = System.currentTimeMillis(),
                        sections = BackupSection.entries.filter { it in included },
                        languages = profile.turnedOn.filter { it in chosen }.map { it.code },
                        models = chosenModels.map { model ->
                            BackupModel(model.id, model.version, model.engine, modelLanguages(model.id).filter { it in languages }, model.bytes)
                        },
                    ),
                    settings = BackupPreferences.encode(settings, included, chosen),
                    dictionaries = BackupDictionaryList(
                        dictionaries = listed.map { dictionary ->
                            val files = dictionary.id in withFiles
                            BackupDictionary.of(dictionary, files, bytes = if (files) bytesOf(storage.directoryOf(dictionary)) else 0)
                        },
                        sortDictionary = if (withList) title(sortIds[Language.JAPANESE]) else null,
                        sortDictionaries = if (withList) {
                            sortIds.mapNotNull { (language, id) -> title(id)?.let { language.code to it } }.toMap()
                        } else {
                            emptyMap()
                        },
                    ),
                    fontIndex = fontFiles?.index,
                    fonts = fontFiles?.files.orEmpty(),
                    models = chosenModels.associate { it.id to (it.directory to it.files) },
                    dictionaryFiles = listed.withIndex()
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
                val backupFonts = head.fontIndex?.let { index ->
                    runCatching { FontBackup.decode(index) }.getOrDefault(emptyList())
                }.orEmpty().filter { font -> font.files.all { it.name in head.fontSizes } }
                val fontFiles = backupFonts.map { font ->
                    BackupFile(font.id, font.family, font.files.sumOf { head.fontSizes[it.name] ?: 0 }, fontLanguage(font))
                }
                val list = head.dictionaries.dictionaries
                val languages = BackupLanguages.of(head.manifest, head.settings, list.map { it.sourceLanguage } + fontFiles.map { it.language })
                BackupState.Loaded(
                    uri = uri,
                    manifest = head.manifest,
                    sections = BackupSection.entries.filter {
                        it in head.manifest.sections || (it == BackupSection.FONTS && backupFonts.isNotEmpty())
                    },
                    listed = list.map { it.sourceLanguage },
                    fonts = fontFiles,
                    dictionaryFiles = list.withIndex().filter { it.value.files }.map {
                        BackupFile(it.index.toString(), it.value.title, it.value.bytes.takeIf { bytes -> bytes > 0 }, it.value.sourceLanguage)
                    },
                    languages = languages.map { language ->
                        val bytes = head.manifest.models.filter { language.code in it.languages }.sumOf { it.bytes }
                        BackupFile(language.code, language.displayName(), bytes.takeIf { it > 0 })
                    },
                )
            }.getOrElse { error ->
                Log.w(TAG, "Reading the backup failed", error.redacted())
                BackupState.Failed(failure(error))
            }
        }
    }

    /**
     * Replaces the chosen [sections] with the backup's, for the shared settings and those of the [languages] (codes);
     * of its fonts and dictionaries, [fontIds] and the files of the dictionaries at [dictionaryIndices] in its list are
     * added or replace the same ones. The languages' recognition models are installed unless the same version is.
     */
    fun restore(uri: Uri, sections: Set<BackupSection>, fontIds: Set<String>, dictionaryIndices: Set<Int>, languages: Set<String>) {
        if (busy) return
        job = scope.launch {
            mutableState.value = BackupState.Working(restoring = true, progress = null)
            mutableState.value = runCatching {
                BackupState.Restored(restoreFrom(uri, sections, fontIds, dictionaryIndices, languages))
            }.getOrElse { error ->
                Log.w(TAG, "Restoring the backup failed", error.redacted())
                BackupState.Failed(failure(error))
            }
        }
    }

    private suspend fun restoreFrom(
        uri: Uri,
        sections: Set<BackupSection>,
        fontIds: Set<String>,
        dictionaryIndices: Set<Int>,
        languages: Set<String>,
    ): RestoreSummary {
        val total = size(uri)
        val chosen = Language.entries.filterTo(linkedSetOf()) { it.code in languages }
        val fontDirectory = File(context.cacheDir, "backup-fonts-${UUID.randomUUID()}").apply { mkdirs() }
        val staging = mutableMapOf<Int, File>()
        val modelStaging = mutableMapOf<String, File>()
        var wantedModels = emptyMap<String, BackupModel>()
        val restoredFiles = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var fontCount: Int? = null
        val turnedOnBefore = profiles.current().turnedOn
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("No input")
            var list = BackupDictionaryList(emptyList())
            val head = Counting(input) { read ->
                if (total != null && total > 0) mutableState.value = BackupState.Working(true, read.toFloat() / total)
            }.use { counted ->
                BackupArchive.read(
                    counted,
                    dictionaryFiles = BackupSection.DICTIONARY_FILES in sections && dictionaryIndices.isNotEmpty(),
                    models = languages.isNotEmpty(),
                    reader = object : BackupArchive.Reader {
                        override suspend fun font(name: String, input: InputStream) {
                            if (BackupSection.FONTS !in sections) return
                            File(fontDirectory, name).outputStream().use { input.copyTo(it) }
                        }

                        override suspend fun modelFile(id: String, path: String, input: InputStream) {
                            if (id !in wantedModels) return
                            val directory = modelStaging.getOrPut(id) { models.newStagingDirectory() }
                            val file = File(directory, path)
                            file.parentFile?.mkdirs()
                            file.outputStream().use { input.copyTo(it) }
                        }

                        override suspend fun dictionaryFile(index: Int, path: String, input: InputStream) {
                            if (BackupSection.DICTIONARY_FILES !in sections || index !in dictionaryIndices) return
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
                        wantedModels = head.manifest.models
                            .filter { model -> model.languages.any { it in languages } && models.model(model.id)?.version != model.version }
                            .associateBy { it.id }
                        restoreSettings(head, sections, chosen)
                    },
                )
            }
            if (BackupSection.FONTS in sections) fontCount = fonts.restore(fontDirectory, fontIds)
            var modelCount = 0
            for ((id, directory) in modelStaging.entries.toList()) {
                val model = wantedModels.getValue(id)
                modelStaging.remove(id)
                runCatching { models.restore(id, model.version, model.engine, directory) }
                    .onSuccess { modelCount++ }
                    .onFailure {
                        Log.w(TAG, "Restoring a recognition model failed", it.redacted())
                        failed += id
                    }
            }
            var missing = emptyList<String>()
            var keptOn = emptyList<String>()
            if (BackupSection.DICTIONARY_LIST in sections) {
                val installed = dictionaries.getAll()
                val entries = head.dictionaries.dictionaries.filter { it.sourceLanguage == null || it.sourceLanguage in languages }
                val outcome = BackupDictionaries.applyList(installed, entries)
                val sortIds = chosen.associateWith { language ->
                    head.dictionaries.sortDictionary(language)?.let { BackupDictionaries.match(outcome.updated, it)?.id }
                }
                val languages = profiles.current().turnedOn
                val kept = keptTermDictionaries(installed, outcome.updated, languages, storage::hasFiles).mapTo(hashSetOf()) { it.id }
                dictionaries.applyStates(outcome.updated.map { if (it.id in kept) it.copy(enabled = true) else it }, sortIds)
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
                models = modelCount,
                turnedOn = profiles.current().turnedOn - turnedOnBefore.toSet(),
            )
        } finally {
            fontDirectory.deleteRecursively()
            staging.values.forEach { it.deleteRecursively() }
            modelStaging.values.forEach { it.deleteRecursively() }
        }
    }

    private suspend fun restoreSettings(head: BackupArchive.Head, sections: Set<BackupSection>, languages: Set<Language>) {
        if (sections.none { it in PREFERENCE_SECTIONS }) return
        val backup = BackupPreferences.decode(head.settings)
        // E-ink mode turned off by the backup brings back the sizes it enlarged, as the switch does; sizes the backup
        // holds are written after that and win.
        if (BackupSection.GENERAL in sections && !BackupPreferences.eInk(backup) && appSettings.eInk.first()) eInkSizes.restore()
        preferences.edit { BackupPreferences.restore(it, backup, sections, languages) }
    }

    private fun failure(error: Throwable): BackupFailure = when (error) {
        is BackupArchive.NotBackupException, is java.util.zip.ZipException -> BackupFailure.NOT_BACKUP
        is BackupArchive.NewerFormatException -> BackupFailure.NEWER_VERSION
        else -> BackupFailure.READ
    }

    private fun bytesOf(directory: File): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

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

        private val PROFILE_KEYS = listOf(LanguageProfiles.TURNED_ON, LanguageProfiles.ACTIVE, LanguageProfiles.STARRED, LanguageProfiles.FLAGS)

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
