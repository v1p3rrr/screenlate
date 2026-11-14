package com.vpr.screenlate.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BubbleColorViewModel @Inject constructor(
    private val repository: OverlaySettingsRepository,
) : ViewModel() {
    val settings: StateFlow<OverlaySettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        repository.cachedSettings ?: OverlaySettings(),
    )

    fun setBubbleUseAppColor(useApp: Boolean) {
        viewModelScope.launch { repository.setBubbleUseAppColor(useApp) }
    }

    fun setBubbleCustomColor(color: Int) {
        viewModelScope.launch { repository.setBubbleCustomColor(color) }
    }
}

/**
 * Dedicated settings section card for the floating bubble's color.
 * Displayed on the Appearance screen and synchronized with Bubble Settings.
 */
@Composable
fun BubbleColorSection(viewModel: BubbleColorViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SectionCard(title = stringResource(R.string.bubble_color_title)) {
        BubbleColorControls(
            useAppColor = settings.bubbleUseAppColor,
            customColor = settings.bubbleCustomColor,
            onUseAppColorChange = viewModel::setBubbleUseAppColor,
            onCustomColorChange = viewModel::setBubbleCustomColor,
        )
    }
}

/**
 * Reusable controls for bubble color configuration: "Use app theme color" toggle and palette selector.
 */
@Composable
fun BubbleColorControls(
    useAppColor: Boolean,
    customColor: Int,
    onUseAppColorChange: (Boolean) -> Unit,
    onCustomColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchRow(
        label = stringResource(R.string.bubble_color_use_app),
        checked = useAppColor,
        onChange = onUseAppColorChange,
        hint = stringResource(R.string.bubble_color_use_app_hint),
    )
    if (!useAppColor) {
        ColorPaletteSelector(
            selectedColor = customColor,
            isSystemSelected = false,
            showSystemOption = false,
            onSelectPreset = { preset -> onCustomColorChange(preset.primarySeed) },
            onSelectCustom = onCustomColorChange,
            modifier = modifier.padding(top = 4.dp),
        )
    }
}
