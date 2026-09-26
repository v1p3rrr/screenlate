package com.vpr.screenlate.bubble

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.overlay.settings.AimMode
import com.vpr.screenlate.overlay.settings.DockSide
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BubbleSettingsViewModel @Inject constructor(private val repository: OverlaySettingsRepository) : ViewModel() {
    val settings: StateFlow<OverlaySettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlaySettings())

    fun setAimMode(mode: AimMode) = launch { repository.setAimMode(mode) }

    fun setDockSide(side: DockSide) = launch { repository.setDockSide(side) }

    fun setHighlight(enabled: Boolean) = launch { repository.setHighlightWord(enabled) }

    fun setHaptics(enabled: Boolean) = launch { repository.setHaptics(enabled) }

    fun setTextSource(source: TextSource) = launch { repository.setTextSource(source) }

    fun setHidden(packageName: String, hidden: Boolean) = launch { repository.setHidden(packageName, hidden) }

    fun setBubbleSize(dp: Int) = launch { repository.setBubbleSize(dp) }

    fun setSmallText(mode: SmallTextMode) = launch { repository.setSmallText(mode) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/** Aim point, dock side, highlight, haptics, and the apps where the bubble stays hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleSettingsScreen(onBack: () -> Unit, viewModel: BubbleSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) { value = launchableApps(context) }
    var filter by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bubble_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text(
                        stringResource(R.string.bubble_aim_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.bubble_dock_side), style = MaterialTheme.typography.titleMedium)
                    Segments(
                        options = DockSide.entries,
                        selected = settings.dockSide,
                        label = {
                            stringResource(if (it == DockSide.LEFT) R.string.bubble_dock_left else R.string.bubble_dock_right)
                        },
                        onSelect = viewModel::setDockSide,
                    )
                    Text(stringResource(R.string.bubble_text_source), style = MaterialTheme.typography.titleMedium)
                    Segments(
                        options = TextSource.entries,
                        selected = settings.textSource,
                        label = {
                            stringResource(
                                if (it == TextSource.SCREEN) R.string.bubble_text_screen else R.string.bubble_text_app,
                            )
                        },
                        onSelect = viewModel::setTextSource,
                    )
                    Text(
                        stringResource(R.string.bubble_text_source_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.bubble_small_text), style = MaterialTheme.typography.titleMedium)
                    Segments(
                        options = SmallTextMode.entries,
                        selected = settings.smallText,
                        label = {
                            stringResource(
                                when (it) {
                                    SmallTextMode.OFF -> R.string.bubble_small_text_off
                                    SmallTextMode.ON_DEMAND -> R.string.bubble_small_text_on_demand
                                    SmallTextMode.ALWAYS -> R.string.bubble_small_text_always
                                },
                            )
                        },
                        onSelect = viewModel::setSmallText,
                    )
                    Text(
                        stringResource(R.string.bubble_small_text_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BubbleSizeRow(settings.bubbleSizeDp, viewModel::setBubbleSize)
                    SwitchRow(stringResource(R.string.bubble_highlight), settings.highlightWord, viewModel::setHighlight)
                    SwitchRow(stringResource(R.string.bubble_haptics), settings.haptics, viewModel::setHaptics)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.bubble_hidden_apps), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.bubble_hidden_apps_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text(stringResource(R.string.bubble_filter_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val visible = apps.orEmpty()
                .filter { filter.isBlank() || it.label.contains(filter, ignoreCase = true) }
                .sortedWith(compareByDescending<LaunchableApp> { it.packageName in settings.hiddenPackages }.thenBy { it.label.lowercase() })
            items(visible, key = { it.packageName }) { app ->
                val hidden = app.packageName in settings.hiddenPackages
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setHidden(app.packageName, !hidden) },
                ) {
                    if (app.icon != null) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                    } else {
                        Spacer(Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    Checkbox(checked = hidden, onCheckedChange = { viewModel.setHidden(app.packageName, it) })
                }
            }
        }
    }
}

private suspend fun launchableApps(context: Context): List<LaunchableApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            LaunchableApp(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
            )
        }
}

private const val ICON_PX = 96

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
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segments(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
