package com.vpr.screenlate.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.settings.AppColors
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.theme.AppPaletteConfig
import com.vpr.screenlate.core.common.theme.ColorPreset
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppColorsViewModel @Inject constructor(private val settings: AppSettingsRepository) : ViewModel() {
    val paletteConfig: StateFlow<AppPaletteConfig> =
        settings.paletteConfig.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.cachedPaletteConfig ?: AppPaletteConfig(),
        )

    val colors: StateFlow<AppColors> =
        settings.appColors.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.cachedAppColors ?: AppColors.SCREENLATE,
        )

    fun setPaletteConfig(config: AppPaletteConfig) {
        viewModelScope.launch { settings.setPaletteConfig(config) }
    }

    fun setColors(colors: AppColors) {
        viewModelScope.launch { settings.setAppColors(colors) }
    }
}

/** Color palette for the app and floating bubble: presets, system wallpaper (Android 12+), or a custom color. */
@Composable
fun AppColorsSection(viewModel: AppColorsViewModel = hiltViewModel()) {
    val config by viewModel.paletteConfig.collectAsStateWithLifecycle()
    SectionCard(title = stringResource(R.string.settings_colors_title)) {
        ColorPaletteSelector(
            selectedColor = if (config.useSystemWallpaper) null else config.effectiveSeed,
            isSystemSelected = config.useSystemWallpaper,
            showSystemOption = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onSelectPreset = { preset ->
                viewModel.setPaletteConfig(AppPaletteConfig(useSystemWallpaper = false, preset = preset))
            },
            onSelectSystem = {
                viewModel.setPaletteConfig(AppPaletteConfig(useSystemWallpaper = true))
            },
            onSelectCustom = { color ->
                viewModel.setPaletteConfig(
                    AppPaletteConfig(useSystemWallpaper = false, preset = null, customSeedColor = color),
                )
            },
        )
        Hint(stringResource(R.string.settings_colors_hint))
    }
}
