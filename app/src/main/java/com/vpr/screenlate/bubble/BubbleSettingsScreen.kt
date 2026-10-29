package com.vpr.screenlate.bubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.overlay.settings.AimMode
import com.vpr.screenlate.overlay.settings.DockSide
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.NavigationRow
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.theme.AccentDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BubbleSettingsViewModel @Inject constructor(private val repository: OverlaySettingsRepository) : ViewModel() {
    /** Starts from the settings as last read; null only before the first read, so screens never show the defaults first. */
    val settings: StateFlow<OverlaySettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.cachedSettings)

    fun setBubbleVisible(visible: Boolean) = launch { repository.setBubbleVisible(visible) }

    fun setAimMode(mode: AimMode) = launch { repository.setAimMode(mode) }

    fun setDockSide(side: DockSide) = launch { repository.setDockSide(side) }

    fun setDockTopBottom(enabled: Boolean) = launch { repository.setDockTopBottom(enabled) }

    fun setHighlight(enabled: Boolean) = launch { repository.setHighlightWord(enabled) }

    fun setHaptics(enabled: Boolean) = launch { repository.setHaptics(enabled) }

    fun setHidden(packageName: String, hidden: Boolean) = launch { repository.setHidden(packageName, hidden) }

    fun setBubbleSize(dp: Int) = launch { repository.setBubbleSize(dp) }

    fun setKeepAlive(enabled: Boolean) = launch { repository.setKeepAlive(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** The bubble's visibility, aim point, dock side, size, highlight and haptics; [onOpenHiddenApps] opens the apps where it stays hidden. */
@Composable
fun BubbleSettingsScreen(
    onBack: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    viewModel: BubbleSettingsViewModel = hiltViewModel(),
) {
    val stored by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScaffold(
        stringResource(R.string.bubble_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.BUBBLE) },
    ) { padding ->
        val settings = stored ?: return@SettingsScaffold
        // Apps uninstalled since they were hidden stay stored but are not counted.
        val hidden = remember(settings.hiddenPackages) {
            settings.hiddenPackages.count { context.packageManager.getLaunchIntentForPackage(it) != null }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // One card for the whole list, so the page looks like the pages split into sections.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SwitchRow(
                        label = stringResource(R.string.bubble_visible),
                        checked = settings.bubbleVisible,
                        onChange = viewModel::setBubbleVisible,
                        hint = stringResource(R.string.bubble_visible_hint),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.bubble_aim), style = MaterialTheme.typography.titleMedium)
                    Segments(
                        options = AimMode.entries,
                        selected = settings.aimMode,
                        label = {
                            stringResource(
                                if (it == AimMode.ABOVE_FINGER) R.string.bubble_aim_above else R.string.bubble_aim_center,
                            )
                        },
                        onSelect = viewModel::setAimMode,
                    )
                    Hint(stringResource(R.string.bubble_aim_hint))
                    Text(stringResource(R.string.bubble_dock_side), style = MaterialTheme.typography.titleMedium)
                    // The segments name screen edges, so Left stays on the left in right-to-left languages too.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Segments(
                            options = if (settings.dockTopBottom) DockSide.entries else DockSide.entries.filterNot { it.horizontal },
                            selected = settings.dockSide,
                            label = {
                                stringResource(
                                    when (it) {
                                        DockSide.LEFT -> R.string.bubble_dock_left
                                        DockSide.RIGHT -> R.string.bubble_dock_right
                                        DockSide.TOP -> R.string.bubble_dock_top
                                        DockSide.BOTTOM -> R.string.bubble_dock_bottom
                                    },
                                )
                            },
                            onSelect = viewModel::setDockSide,
                        )
                    }
                    SwitchRow(
                        label = stringResource(R.string.bubble_dock_top_bottom),
                        checked = settings.dockTopBottom,
                        onChange = viewModel::setDockTopBottom,
                        hint = stringResource(R.string.bubble_dock_top_bottom_hint),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    BubbleSizeRow(settings.bubbleSizeDp, viewModel::setBubbleSize)
                    SwitchRow(stringResource(R.string.bubble_highlight), settings.highlightWord, viewModel::setHighlight)
                    SwitchRow(stringResource(R.string.bubble_haptics), settings.haptics, viewModel::setHaptics)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationRow(
                        stringResource(R.string.bubble_hidden_apps),
                        if (hidden == 0) {
                            stringResource(R.string.bubble_hidden_apps_none)
                        } else {
                            pluralStringResource(R.plurals.bubble_hidden_apps_count, hidden, hidden)
                        },
                        onOpenHiddenApps,
                    )
                }
            }
        }
    }
}

/** The slider moves in steps; the setting is written only when the finger is lifted. */
@Composable
private fun BubbleSizeRow(sizeDp: Int, onChange: (Int) -> Unit) {
    var value by remember(sizeDp) { mutableStateOf(sizeDp.toFloat()) }
    Column {
        Text(
            stringResource(R.string.bubble_size, value.toInt()),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = OverlaySettings.MIN_BUBBLE_DP.toFloat()..OverlaySettings.MAX_BUBBLE_DP.toFloat(),
            steps = (OverlaySettings.MAX_BUBBLE_DP - OverlaySettings.MIN_BUBBLE_DP) / 4 - 1,
            colors = AccentDefaults.sliderColors(),
        )
    }
}
