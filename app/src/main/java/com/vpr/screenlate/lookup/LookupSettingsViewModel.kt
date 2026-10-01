package com.vpr.screenlate.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
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
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, repository.cachedSettings)

    fun setScanLength(value: Int) = launch { repository.setScanLength(value) }

    fun setMaxResults(value: Int) = launch { repository.setMaxResults(value) }

    fun setRomaji(enabled: Boolean) = launch { repository.setRomaji(enabled) }

    fun setSingleKanji(enabled: Boolean) = launch { repository.setSingleKanji(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
