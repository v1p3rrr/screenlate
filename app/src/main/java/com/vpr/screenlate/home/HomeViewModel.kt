package com.vpr.screenlate.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DictionarySummary(val installed: Int = 0, val enabled: Int = 0, val importing: Boolean = false)

/** Deck and note type when Anki export is configured, null otherwise. */
data class AnkiSummary(val deck: String, val model: String)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: DictionaryRepository,
    imports: DictionaryImports,
    ankiSettings: AnkiSettingsRepository,
) : ViewModel() {
    val anki: StateFlow<AnkiSummary?> = ankiSettings.settings
        .map { if (it.configured) AnkiSummary(it.deckName.orEmpty(), it.modelName.orEmpty()) else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dictionaries: StateFlow<DictionarySummary> =
        combine(repository.dictionaries, imports.tasks) { dictionaries, tasks ->
            DictionarySummary(
                installed = dictionaries.size,
                enabled = dictionaries.count { it.enabled },
                importing = tasks.any { !it.finished },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionarySummary())
}
