package com.vpr.screenlate.yomitan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.dictionary.api.imports.CollectionDictionary
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * @property installed a dictionary with this title, or the same one in another revision, is installed here.
 */
data class CollectionItem(val dictionary: CollectionDictionary, val installed: Boolean, val checked: Boolean)

sealed interface CollectionState {
    data object Idle : CollectionState

    data object Scanning : CollectionState

    data object NotCollection : CollectionState

    data class Listed(val uri: Uri, val items: List<CollectionItem>) : CollectionState

    data class Queued(val count: Int) : CollectionState
}

/** A Yomitan dictionary collection export shown as a checklist; only checked dictionaries are imported. */
@HiltViewModel
class CollectionImportViewModel @Inject constructor(
    private val imports: DictionaryImports,
    private val repository: DictionaryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CollectionState>(CollectionState.Idle)
    val state: StateFlow<CollectionState> = mutableState

    fun load(uri: Uri) {
        mutableState.value = CollectionState.Scanning
        viewModelScope.launch {
            val listed = runCatching { imports.scanCollection(uri) }.getOrNull()
            if (listed.isNullOrEmpty()) {
                mutableState.value = CollectionState.NotCollection
                return@launch
            }
            val installed = repository.getAll()
            val titles = installed.map { it.title }.toSet()
            val keys = installed.map { dictionaryKey(it.title) }.toSet()
            mutableState.value = CollectionState.Listed(
                uri,
                listed.map { dictionary ->
                    val present = dictionary.title in titles || dictionaryKey(dictionary.title) in keys
                    CollectionItem(dictionary, installed = present, checked = !present)
                },
            )
        }
    }

    fun toggle(title: String) {
        val listed = mutableState.value as? CollectionState.Listed ?: return
        mutableState.value = listed.copy(
            items = listed.items.map { if (it.dictionary.title == title) it.copy(checked = !it.checked) else it },
        )
    }

    fun import() {
        val listed = mutableState.value as? CollectionState.Listed ?: return
        val titles = listed.items.filter { it.checked }.map { it.dictionary.title }.toSet()
        if (titles.isEmpty()) return
        viewModelScope.launch {
            runCatching { imports.importCollection(listed.uri, titles) }
                .onSuccess { mutableState.value = CollectionState.Queued(titles.size) }
                .onFailure { mutableState.value = CollectionState.NotCollection }
        }
    }

    fun reset() {
        mutableState.value = CollectionState.Idle
    }
}
