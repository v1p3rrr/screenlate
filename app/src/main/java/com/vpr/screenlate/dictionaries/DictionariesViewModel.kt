package com.vpr.screenlate.dictionaries

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdate
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdates
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val loaded: Boolean = false,
)

/** Result of the last update check; [updates] is null while checking. */
data class UpdateCheck(val updates: List<DictionaryUpdate>?)

@HiltViewModel
class DictionariesViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val imports: DictionaryImports,
    private val dictionaryUpdates: DictionaryUpdates,
    catalog: DictionaryCatalog,
) : ViewModel() {
    private val copyError = MutableStateFlow<String?>(null)
    private val updateCheck = MutableStateFlow<UpdateCheck?>(null)

    /** Null until the user checks for updates. */
    val updateState: StateFlow<UpdateCheck?> = updateCheck

    val state: StateFlow<DictionariesState> = combine(
        repository.dictionaries,
        imports.tasks,
        catalog.entries(),
        repository.sortDictionaryId,
    ) { dictionaries, tasks, entries, sortId ->
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
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionariesState())

    /** Error from copying a picked file, before the import is queued. */
    val importError: StateFlow<String?> = copyError

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching { imports.importFrom(uri) }.onFailure { copyError.value = it.message ?: it.javaClass.simpleName }
        }
    }

    fun importYomitanBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { imports.importYomitanBackup(uri) }
                .onFailure { copyError.value = it.message ?: it.javaClass.simpleName }
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
        viewModelScope.launch { repository.delete(dictionary.id) }
    }

    fun clearFinishedTasks() {
        imports.clearFinished()
    }
}

/** Source language, then term dictionaries by target language, then the other kinds. */
private fun groupCatalog(items: List<CatalogItem>): List<CatalogGroup> {
    val userLanguage = Locale.getDefault().language
    return items.groupBy { it.entry.sourceLanguage }
        .toSortedMap(compareBy<String> { it != userLanguage && it != "ja" }.thenBy { it })
        .map { (source, sourceItems) ->
            val terms = sourceItems.filter { it.entry.kind == DictionaryKind.TERM }
                .groupBy { it.entry.targetLanguage }
                .toSortedMap(compareBy<String?> { it != userLanguage }.thenBy { it.orEmpty() })
                .map { (target, targetItems) -> CatalogSection(DictionaryKind.TERM, target, targetItems) }
            val others = DictionaryKind.entries.filter { it != DictionaryKind.TERM }.mapNotNull { kind ->
                sourceItems.filter { it.entry.kind == kind }.takeIf { it.isNotEmpty() }?.let { CatalogSection(kind, null, it) }
            }
            CatalogGroup(source, terms + others)
        }
}
