package com.vpr.screenlate.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettingsRepository,
) : ViewModel() {
    val offlineMode: StateFlow<Boolean> = appSettings.offlineMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setOfflineMode(enabled)
        }
    }
}
