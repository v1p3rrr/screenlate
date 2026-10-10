package com.vpr.screenlate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.settings.AppColors
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    profiles: LanguageProfiles,
) : ViewModel() {

    /** Null until the settings are read, so no frame is drawn in a theme the user did not choose. */
    val theme: StateFlow<AppTheme?> =
        combine(settings.themeMode, settings.eInk, settings.appColors, ::AppTheme)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether to start on the first run's language question; null until the app has decided it. */
    val firstRun: StateFlow<Boolean?> = flow {
        profiles.awaitFirstRunSettled()
        emitAll(profiles.firstRunPending)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}

data class AppTheme(val mode: ThemeMode, val eInk: Boolean, val colors: AppColors) {
    val systemColors: Boolean get() = colors == AppColors.SYSTEM
}
