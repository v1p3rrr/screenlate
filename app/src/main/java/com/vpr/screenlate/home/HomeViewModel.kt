package com.vpr.screenlate.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DictionarySummary(val installed: Int = 0, val enabled: Int = 0, val importing: Boolean = false)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: DictionaryRepository,
    imports: DictionaryImports,
) : ViewModel() {
    val dictionaries: StateFlow<DictionarySummary> =
        combine(repository.dictionaries, imports.tasks) { dictionaries, tasks ->
            DictionarySummary(
                installed = dictionaries.size,
                enabled = dictionaries.count { it.enabled },
                importing = tasks.any { !it.finished },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionarySummary())
}
