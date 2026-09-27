package com.vpr.screenlate.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.dictionary.api.settings.TextReplacement
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LookupSettingsViewModel @Inject constructor(
    private val repository: LookupSettingsRepository,
) : ViewModel() {
    val settings: StateFlow<LookupSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setScanLength(value: Int) = launch { repository.setScanLength(value) }

    fun setMaxResults(value: Int) = launch { repository.setMaxResults(value) }

    fun setRomaji(enabled: Boolean) = launch { repository.setRomaji(enabled) }

    fun setSingleKanji(enabled: Boolean) = launch { repository.setSingleKanji(enabled) }

    fun setSearchOriginal(enabled: Boolean) = launch { repository.setSearchOriginal(enabled) }

    fun setReplacementGroups(groups: List<List<TextReplacement>>) = launch { repository.setReplacementGroups(groups) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        /** Texts a lookup of [text] would search with these replacement groups, for the test field. */
        fun replacementPreview(text: String, groups: List<List<TextReplacement>>): List<String> = groups.map { group ->
            group.filter { it.enabled }
                .fold(MappedText.identity(text)) { current, rule ->
                    rule.regex()?.let { current.replace(it, rule.replacement) } ?: current
                }
                .text
        }
    }
}
