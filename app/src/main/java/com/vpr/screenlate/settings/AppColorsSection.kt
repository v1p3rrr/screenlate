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
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppColorsViewModel @Inject constructor(private val settings: AppSettingsRepository) : ViewModel() {
    val colors: StateFlow<AppColors> =
        settings.appColors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppColors.SCREENLATE)

    fun setColors(colors: AppColors) {
        viewModelScope.launch { settings.setAppColors(colors) }
    }
}

/** Screenlate's own colors or the system's; only on Android 12 and later, where the phone picks colors for apps. */
@Composable
fun AppColorsSection(viewModel: AppColorsViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val colors by viewModel.colors.collectAsStateWithLifecycle()
    SectionCard(title = stringResource(R.string.settings_colors_title)) {
        Segments(
            options = AppColors.entries,
            selected = colors,
            label = {
                stringResource(
                    when (it) {
                        AppColors.SCREENLATE -> R.string.settings_colors_screenlate
                        AppColors.SYSTEM -> R.string.settings_colors_system
                    },
                )
            },
            onSelect = viewModel::setColors,
        )
        Hint(stringResource(R.string.settings_colors_hint))
    }
}
