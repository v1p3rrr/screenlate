package com.vpr.screenlate.dictionaries

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryReset
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdate
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdates
import com.vpr.screenlate.dictionary.api.registry.isFor
import com.vpr.screenlate.languages.LanguageFiles
import com.vpr.screenlate.languages.ShownLanguage
import com.vpr.screenlate.overlay.fonts.CssCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A catalog entry with its state relative to the installed dictionaries and running imports; [task] is its queued or
 * running import, e.g. a download from the catalog or an update. [outdated] is an old copy to replace with the current
 * build ([CatalogEntry.outdatedCopy]); the entry does not count as installed then.
 */
data class CatalogItem(
    val entry: CatalogEntry,
    val installed: Boolean,
    val task: ImportTask? = null,
    val outdated: DictionaryEntity? = null,
)

/** Catalog entries with their installed state and the unfinished import named after each, if any. */
internal fun catalogItems(
    entries: List<CatalogEntry>,
    dictionaries: List<DictionaryEntity>,
    tasks: List<ImportTask>,
): List<CatalogItem> {
    val running = tasks.filter { !it.finished }.associateBy { it.name }
    return entries.map { entry ->
        val copies = dictionaries.filter(entry::matches)
        // A download is named after the entry, an update after the installed dictionary it replaces.
        val task = running[entry.title] ?: copies.firstNotNullOfOrNull { running[it.title] }
        val outdated = entry.outdatedCopy(copies)
        CatalogItem(entry, installed = copies.isNotEmpty() && outdated == null, task = task, outdated = outdated)
    }
}

/**
 * Catalog entries of one kind and language pair. [targetLanguage] is set for term dictionaries only; frequency,
 * pitch and kanji dictionaries are grouped by source language alone.
 */
data class CatalogSection(val kind: DictionaryKind, val targetLanguage: String?, val items: List<CatalogItem>)

data class CatalogGroup(val sourceLanguage: String, val sections: List<CatalogSection>)

/** Installed dictionaries of one kind, in priority order. */
data class InstalledSection(val kind: DictionaryKind, val dictionaries: List<DictionaryEntity>)

/**
 * Installed dictionaries of a language that is not turned on, or that the app does not support; lookups do not use
 * them. [bytes] is null until their files are measured.
 */
data class OtherLanguage(val code: String, val dictionaries: List<DictionaryEntity>, val bytes: Long?)

data class DictionariesState(
    /** Every installed dictionary, in priority order. */
    val all: List<DictionaryEntity> = emptyList(),
    /** The dictionaries of the [shown] language and those without a language. */
    val installed: List<InstalledSection> = emptyList(),
    val others: List<OtherLanguage> = emptyList(),
    val shown: Language = Language.JAPANESE,
    val tasks: List<ImportTask> = emptyList(),
    val catalog: List<CatalogGroup> = emptyList(),
    /** The frequency dictionary that sorts the [shown] language's results. */
    val sortDictionaryId: Long? = null,
    /** Website and download links by dictionary id. */
    val links: Map<Long, DictionaryLinks> = emptyMap(),
    /** Hosts that the styles of a switched on dictionary load files from, by dictionary title. */
    val remoteCss: Map<String, List<String>> = emptyMap(),
    /** Ids of the dictionaries whose files are gone. */
    val withoutFiles: Set<Long> = emptySet(),
    /** Bytes on disk by dictionary id, for the dictionaries in [others]; empty until measured. */
    val sizes: Map<Long, Long> = emptyMap(),
    val profiles: LanguageProfilesState = LanguageProfilesState.DEFAULT,
    val loaded: Boolean = false,
) {
    /** The turned-on languages, for which the last dictionary with definitions stays on. */
    val languages: List<Language> get() = profiles.turnedOn
}

/** Result of the last update check; [updates] is null while checking. */
data class UpdateCheck(val updates: List<DictionaryUpdate>?)

@HiltViewModel
class DictionariesViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val imports: DictionaryImports,
    private val dictionaryUpdates: DictionaryUpdates,
    private val bundled: BundledDictionaries,
    private val lookup: DictionaryLookup,
    private val dictionaryReset: DictionaryReset,
    private val cache: DictionariesStateCache,
    catalog: DictionaryCatalog,
    private val profiles: LanguageProfiles,
    private val languageFiles: LanguageFiles,
) : ViewModel() {
    private val shownLanguage = ShownLanguage(profiles, viewModelScope)
    private val copyError = MutableStateFlow<String?>(null)
    private val mutableDeleteError = MutableStateFlow<String?>(null)
    private val updateCheck = MutableStateFlow<UpdateCheck?>(null)

    /** Null until the user checks for updates. */
    val updateState: StateFlow<UpdateCheck?> = updateCheck

    val state: StateFlow<DictionariesState> = combine(
        repository.dictionaries,
        imports.tasks,
        catalog.entries(),
        combine(sortDictionaryIds(), profiles.state, shownLanguage.language, ::Triple),
        // Reading the styles loads the engine and checking files takes a moment; the list does not wait for them.
        combine(repository.dictionaries, profiles.state.map { it.turnedOn }.distinctUntilChanged(), ::Pair)
            .map { (dictionaries, turnedOn) ->
                FileChecks(remoteCss(), withoutFiles(), sizes(ofOtherLanguages(dictionaries, turnedOn)))
            }
            .onStart { emit(cache.last?.let { FileChecks(it.remoteCss, it.withoutFiles, it.sizes) } ?: FileChecks()) },
    ) { dictionaries, tasks, entries, (sortIds, profileState, shown), checks ->
        val items = catalogItems(entries, dictionaries, tasks)
        val shownDictionaries = dictionaries.filter { it.isFor(shown) }
        DictionariesState(
            all = dictionaries,
            installed = DictionaryKind.entries.mapNotNull { kind ->
                shownDictionaries.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { InstalledSection(kind, it) }
            },
            others = otherLanguages(dictionaries, profileState.turnedOn, checks.sizes),
            shown = shown,
            tasks = tasks.filter { !it.finished || it.state == ImportTask.State.FAILED },
            catalog = groupCatalog(items.filter { it.entry.sourceLanguage == shown.code }, shown),
            sortDictionaryId = sortDictionaryId(shownDictionaries.filter { it.id !in checks.withoutFiles }, sortIds[shown]),
            links = dictionaries.associate { dictionary ->
                dictionary.id to DictionaryLinks.of(dictionary, entries.firstOrNull { it.matches(dictionary) })
            },
            remoteCss = checks.remoteCss,
            withoutFiles = checks.withoutFiles,
            sizes = checks.sizes,
            profiles = profileState,
            loaded = true,
        )
    }
        .onEach { cache.last = it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), cache.last ?: DictionariesState())

    /** Only the loaded dictionaries have styles; a switched on or imported one changes the registry and is checked then. */
    private suspend fun remoteCss(): Map<String, List<String>> = try {
        Language.entries.flatMap { lookup.styles(it) }
            .associate { style -> style.dictionary to CssCheck.remoteFiles(style.css).map { it.detail }.distinct() }
            .filterValues { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The ⚠ for remote files is left out until the next registry change; the list itself still shows.
        Log.w(TAG, "Cannot check dictionary styles for remote files", e.redacted())
        emptyMap()
    }

    private suspend fun sizes(dictionaries: List<DictionaryEntity>): Map<Long, Long> = try {
        repository.sizes(dictionaries)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    /** Shows the dictionaries and the catalog of a turned-on [language]. */
    fun show(language: Language) = shownLanguage.show(language)

    private suspend fun withoutFiles(): Set<Long> = try {
        repository.missingFiles().mapTo(hashSetOf()) { it.id }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptySet()
    }

    /** The step of a running dictionary reset; null when none runs. */
    val resetPhase: StateFlow<DictionaryReset.Phase?> = dictionaryReset.phase

    /** Returns the dictionaries to a fresh install; leaving the screen does not stop it. */
    fun resetDictionaries() {
        viewModelScope.launch { withContext(NonCancellable) { dictionaryReset.reset() } }
    }

    /** Why the last dictionary reset failed; null when it did not or the message was dismissed. */
    val resetError: StateFlow<String?> = dictionaryReset.error

    fun dismissResetError() = dictionaryReset.dismissError()

    /** Whether a reset was given up after the app died during it twice. */
    val resetGaveUp: StateFlow<Boolean> =
        dictionaryReset.gaveUp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissResetGaveUp() {
        viewModelScope.launch { dictionaryReset.dismissGaveUp() }
    }

    /** Why the last delete failed; null when it did not or the message was dismissed. */
    val deleteError: StateFlow<String?> = mutableDeleteError

    fun dismissDeleteError() {
        mutableDeleteError.value = null
    }

    /** Error from copying a picked file, before the import is queued. */
    val importError: StateFlow<String?> = copyError

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching { imports.importFrom(uri) }.onFailure { copyError.value = it.message ?: it.javaClass.simpleName }
        }
    }

    fun dismissImportError() {
        copyError.value = null
    }

    fun download(entry: CatalogEntry) {
        imports.download(entry.downloadUrl, entry.title, indexUrl = entry.indexUrl.takeIf { entry.resolveLatest })
    }

    /** Replaces [copy], an old build of [entry], with the current build in its place. */
    fun replace(entry: CatalogEntry, copy: DictionaryEntity) {
        imports.download(
            entry.downloadUrl,
            copy.title,
            indexUrl = entry.indexUrl.takeIf { entry.resolveLatest },
            replaces = copy.id,
        )
    }

    fun setEnabled(dictionary: DictionaryEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(dictionary.id, enabled) }
    }

    /** Applies a new order to the dictionaries of one section; every other dictionary keeps its place. */
    fun reorder(ordered: List<DictionaryEntity>) {
        viewModelScope.launch { repository.reorder(reordered(state.value.all, ordered).map { it.id }) }
    }

    fun setLanguages(dictionary: DictionaryEntity, source: String?, target: String?) {
        viewModelScope.launch { repository.setLanguages(dictionary.id, source, target) }
    }

    /** Sorts the results of [dictionary]'s language by it; one without a language sorts the shown language's. */
    fun setSortDictionary(dictionary: DictionaryEntity) {
        val language = Language.of(dictionary.sourceLanguage) ?: shownLanguage.language.value
        viewModelScope.launch {
            repository.setSortDictionary(language, dictionary.id)
        }
    }

    private fun sortDictionaryIds(): Flow<Map<Language, Long?>> =
        combine(Language.entries.map { language -> repository.sortDictionaryId(language).map { language to it } }) { it.toMap() }

    fun checkUpdates() {
        updateCheck.value = UpdateCheck(null)
        viewModelScope.launch { updateCheck.value = UpdateCheck(dictionaryUpdates.check()) }
    }

    fun update(items: List<DictionaryUpdate>) {
        items.forEach { imports.download(it.downloadUrl, it.dictionary.title, replaces = it.dictionary.id) }
        updateCheck.value = UpdateCheck(updateCheck.value?.updates.orEmpty() - items.toSet())
    }

    fun delete(dictionary: DictionaryEntity) {
        viewModelScope.launch {
            // Leaving the screen must not stop a delete halfway; finding the bundled archive takes a while.
            withContext(NonCancellable) {
                try {
                    if (dictionary.bundled) bundled.markDeleted(dictionary.title)
                    repository.delete(dictionary.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // E.g. a full storage, where the settings or the registry cannot be written.
                    Log.w(TAG, "Deleting a dictionary failed", e)
                    mutableDeleteError.value = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    /** Deletes every dictionary of a language that is not turned on; leaving the screen does not stop it. */
    fun deleteLanguage(code: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    languageFiles.delete(code)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Deleting the dictionaries of a language failed", e)
                    mutableDeleteError.value = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    fun clearFinishedTasks() {
        imports.clearFinished()
    }

    /** Starts the bundled install that stopped after the app died during it twice; see [ImportTask.paused]. */
    fun retryBundledInstall() {
        viewModelScope.launch {
            bundled.resumeInstall()
            imports.clearFinished()
            imports.installBundled()
        }
    }

    /** Cancels one queued or running import; the others go on. */
    fun cancel(task: ImportTask) {
        viewModelScope.launch { imports.cancel(task.id) }
    }
}

private const val TAG = "Dictionaries"

/** Results of the checks that read files, which the list does not wait for. */
private data class FileChecks(
    val remoteCss: Map<String, List<String>> = emptyMap(),
    val withoutFiles: Set<Long> = emptySet(),
    val sizes: Map<Long, Long> = emptyMap(),
)

/**
 * [all] with the dictionaries of [ordered] in its order, in the places they held; the others stay where they are, so a
 * new order among one language's dictionaries leaves the other languages' alone.
 */
internal fun reordered(all: List<DictionaryEntity>, ordered: List<DictionaryEntity>): List<DictionaryEntity> {
    val moved = ordered.mapTo(hashSetOf()) { it.id }
    val next = ordered.iterator()
    return all.map { if (it.id in moved && next.hasNext()) next.next() else it }
}

/**
 * The frequency dictionary that sorts a language's results: the [stored] choice while it is among the enabled frequency
 * dictionaries of [dictionaries], otherwise the first of them, as lookups do.
 */
internal fun sortDictionaryId(dictionaries: List<DictionaryEntity>, stored: Long?): Long? {
    val frequencies = dictionaries.filter { it.enabled && it.frequencyCount > 0 }
    return (frequencies.firstOrNull { it.id == stored } ?: frequencies.firstOrNull())?.id
}

/** Dictionaries with a source language that is not turned on. */
private fun ofOtherLanguages(dictionaries: List<DictionaryEntity>, turnedOn: List<Language>): List<DictionaryEntity> {
    val on = turnedOn.mapTo(hashSetOf()) { it.code }
    return dictionaries.filter { it.sourceLanguage != null && it.sourceLanguage !in on }
}

/** Dictionaries with a source language that is not turned on, by language, ordered by the language's name. */
internal fun otherLanguages(
    dictionaries: List<DictionaryEntity>,
    turnedOn: List<Language>,
    sizes: Map<Long, Long>,
): List<OtherLanguage> {
    val locale = Locale.getDefault()
    return ofOtherLanguages(dictionaries, turnedOn)
        .groupBy { it.sourceLanguage!! }
        .map { (code, list) ->
            val bytes = list.map { sizes[it.id] }.takeIf { it.all { size -> size != null } }?.sumOf { it!! }
            OtherLanguage(code, list, bytes)
        }
        .sortedWith(compareBy(Collator.getInstance(locale)) { Locale.forLanguageTag(it.code).getDisplayLanguage(locale) })
}

/**
 * Source language, then term dictionaries by target language, then the other kinds. The [active] language's
 * dictionaries come first, then those for the interface language.
 */
private fun groupCatalog(items: List<CatalogItem>, active: Language): List<CatalogGroup> {
    val locale = Locale.getDefault()
    val userLanguage = locale.language
    return items.groupBy { it.entry.sourceLanguage }
        .toSortedMap(compareBy<String> { it != active.code }.thenBy { it != userLanguage }.thenBy { it })
        .map { (source, sourceItems) ->
            val terms = sourceItems.filter { it.entry.kind == DictionaryKind.TERM }
                .groupBy { it.entry.targetLanguage }
                .toSortedMap(catalogTargetOrder(source, locale))
                .map { (target, targetItems) -> CatalogSection(DictionaryKind.TERM, target, targetItems) }
            val others = DictionaryKind.entries.filter { it != DictionaryKind.TERM }.mapNotNull { kind ->
                sourceItems.filter { it.entry.kind == kind }.takeIf { it.isNotEmpty() }?.let { CatalogSection(kind, null, it) }
            }
            CatalogGroup(source, terms + others)
        }
}
