package com.vpr.screenlate.dictionaries

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
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
import com.vpr.screenlate.overlay.fonts.CssCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A catalog entry with its state relative to the installed dictionaries and running imports. */
data class CatalogItem(val entry: CatalogEntry, val installed: Boolean, val inProgress: Boolean)

/**
 * Catalog entries of one kind and language pair. [targetLanguage] is set for term dictionaries only; frequency,
 * pitch and kanji dictionaries are grouped by source language alone.
 */
data class CatalogSection(val kind: DictionaryKind, val targetLanguage: String?, val items: List<CatalogItem>)

data class CatalogGroup(val sourceLanguage: String, val sections: List<CatalogSection>)

/** Installed dictionaries of one kind, in priority order. */
data class InstalledSection(val kind: DictionaryKind, val dictionaries: List<DictionaryEntity>)

data class DictionariesState(
    val installed: List<InstalledSection> = emptyList(),
    val tasks: List<ImportTask> = emptyList(),
    val catalog: List<CatalogGroup> = emptyList(),
    /** The frequency dictionary used for sorting. */
    val sortDictionaryId: Long? = null,
    /** Website and download links by dictionary id. */
    val links: Map<Long, DictionaryLinks> = emptyMap(),
    /** Hosts that the styles of a switched on dictionary load files from, by dictionary title. */
    val remoteCss: Map<String, List<String>> = emptyMap(),
    /** Ids of the dictionaries whose files are gone. */
    val withoutFiles: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

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
    catalog: DictionaryCatalog,
) : ViewModel() {
    private val copyError = MutableStateFlow<String?>(null)
    private val mutableDeleteError = MutableStateFlow<String?>(null)
    private val updateCheck = MutableStateFlow<UpdateCheck?>(null)

    /** Null until the user checks for updates. */
    val updateState: StateFlow<UpdateCheck?> = updateCheck

    val state: StateFlow<DictionariesState> = combine(
        repository.dictionaries,
        imports.tasks,
        catalog.entries(),
        repository.sortDictionaryId,
        repository.dictionaries.map { remoteCss() to withoutFiles() },
    ) { dictionaries, tasks, entries, sortId, (remoteCss, withoutFiles) ->
        val running = tasks.filter { !it.finished }.map { it.name }.toSet()
        val items = entries.map { entry ->
            CatalogItem(entry, installed = dictionaries.any(entry::matches), inProgress = entry.title in running)
        }
        DictionariesState(
            installed = DictionaryKind.entries.mapNotNull { kind ->
                dictionaries.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { InstalledSection(kind, it) }
            },
            tasks = tasks.filter { !it.finished || it.state == ImportTask.State.FAILED },
            catalog = groupCatalog(items),
            sortDictionaryId = dictionaries
                .filter { it.enabled && it.frequencyCount > 0 }
                .let { frequencies -> frequencies.firstOrNull { it.id == sortId } ?: frequencies.firstOrNull() }
                ?.id,
            links = dictionaries.associate { dictionary ->
                dictionary.id to DictionaryLinks.of(dictionary, entries.firstOrNull { it.matches(dictionary) })
            },
            remoteCss = remoteCss,
            withoutFiles = withoutFiles,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionariesState())

    /** Only the loaded dictionaries have styles; a switched on or imported one changes the registry and is checked then. */
    private suspend fun remoteCss(): Map<String, List<String>> = try {
        Language.entries.flatMap { lookup.styles(it) }
            .associate { style -> style.dictionary to CssCheck.remoteFiles(style.css).map { it.detail }.distinct() }
            .filterValues { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

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

    fun setEnabled(dictionary: DictionaryEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(dictionary.id, enabled) }
    }

    /** Applies a new order inside one section; other sections keep theirs. */
    fun reorder(kind: DictionaryKind, ordered: List<DictionaryEntity>) {
        val all = state.value.installed.flatMap { section ->
            if (section.kind == kind) ordered else section.dictionaries
        }
        viewModelScope.launch { repository.reorder(all.map { it.id }) }
    }

    fun setLanguages(dictionary: DictionaryEntity, source: String?, target: String?) {
        viewModelScope.launch { repository.setLanguages(dictionary.id, source, target) }
    }

    fun setSortDictionary(dictionary: DictionaryEntity) {
        viewModelScope.launch { repository.setSortDictionary(dictionary.id) }
    }

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

    fun clearFinishedTasks() {
        imports.clearFinished()
    }

    /** Cancels one queued or running import; the others go on. */
    fun cancel(task: ImportTask) {
        viewModelScope.launch { imports.cancel(task.id) }
    }
}

private const val TAG = "Dictionaries"

/** Source language, then term dictionaries by target language, then the other kinds. */
private fun groupCatalog(items: List<CatalogItem>): List<CatalogGroup> {
    val locale = Locale.getDefault()
    val userLanguage = locale.language
    return items.groupBy { it.entry.sourceLanguage }
        .toSortedMap(compareBy<String> { it != userLanguage && it != "ja" }.thenBy { it })
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
