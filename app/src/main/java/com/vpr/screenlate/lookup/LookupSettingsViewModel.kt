package com.vpr.screenlate.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.languages.ShownLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LookupSettingsViewModel @Inject constructor(
    private val repository: LookupSettingsRepository,
    profiles: LanguageProfiles,
) : ViewModel() {
    val shown = ShownLanguage(profiles, viewModelScope)

    /** The shown language's settings with the shared ones. */
    val settings: StateFlow<LookupSettings?> = shown.language
        .flatMapLatest { repository.settings(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.cachedSettings(shown.language.value))

    fun setScanLength(value: Int) = launch { repository.setScanLength(language(), value) }

    fun setMaxResults(value: Int) = launch { repository.setMaxResults(value) }

    fun setRomaji(enabled: Boolean) = launch { repository.setRomaji(enabled) }

    fun setSingleKanji(enabled: Boolean) = launch { repository.setSingleKanji(language(), enabled) }

    private fun language(): Language = shown.language.value

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
