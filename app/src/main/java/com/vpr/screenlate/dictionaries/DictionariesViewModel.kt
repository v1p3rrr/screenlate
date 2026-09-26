package com.vpr.screenlate.dictionaries

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A catalog entry with its state relative to the installed dictionaries and running imports. */
data class CatalogItem(val entry: CatalogEntry, val installed: Boolean, val inProgress: Boolean)

data class DictionariesState(
    val dictionaries: List<DictionaryEntity> = emptyList(),
    val tasks: List<ImportTask> = emptyList(),
    val catalog: List<CatalogItem> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class DictionariesViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val imports: DictionaryImports,
    catalog: DictionaryCatalog,
) : ViewModel() {
    private val copyError = MutableStateFlow<String?>(null)

    val state: StateFlow<DictionariesState> = combine(
        repository.dictionaries,
        imports.tasks,
        flow { emit(catalog.entries()) },
    ) { dictionaries, tasks, entries ->
        val running = tasks.filter { !it.finished }.map { it.name }.toSet()
        DictionariesState(
            dictionaries = dictionaries,
            tasks = tasks.filter { !it.finished || it.state == ImportTask.State.FAILED },
            catalog = entries.map { entry ->
                CatalogItem(
                    entry = entry,
                    installed = dictionaries.any(entry::matches),
                    inProgress = entry.title in running,
                )
            },
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

    fun dismissImportError() {
        copyError.value = null
    }

    fun download(entry: CatalogEntry) {
        imports.download(entry.downloadUrl, entry.title, indexUrl = entry.indexUrl.takeIf { entry.resolveLatest })
    }

    fun setEnabled(dictionary: DictionaryEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(dictionary.id, enabled) }
    }

    fun reorder(dictionaries: List<DictionaryEntity>) {
        viewModelScope.launch { repository.reorder(dictionaries.map { it.id }) }
    }

    fun delete(dictionary: DictionaryEntity) {
        viewModelScope.launch { repository.delete(dictionary.id) }
    }

    fun clearFinishedTasks() {
        imports.clearFinished()
    }
}
