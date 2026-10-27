package com.vpr.screenlate.recognition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.languages.OcrModelRows
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.LabelWithInfo
import com.vpr.screenlate.ui.components.NavigationRow
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecognitionSettingsViewModel @Inject constructor(private val repository: OverlaySettingsRepository) : ViewModel() {
    /** Starts from the settings as last read; null only before the first read, so the page never shows the defaults first. */
    val settings: StateFlow<OverlaySettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.cachedSettings)

    fun setOcrEngines(engines: OcrEngines) = launch { repository.setOcrEngines(engines) }

    fun setSmallText(mode: SmallTextMode) = launch { repository.setSmallText(mode) }

    fun setOcrSaving(enabled: Boolean) = launch { repository.setOcrSaving(enabled) }

    fun setTextSource(source: TextSource) = launch { repository.setTextSource(source) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * How the bubble and the camera read text: the engines, OCR boost, load reduction, each language's on-device model,
 * the app text switches, and the OCR test.
 *
 * @param showAppText scrolls to the app text switches, for the e-ink hint.
 * @param ocrModels opens the on-device models of the language with this code.
 */
@Composable
fun RecognitionSettingsScreen(
    onBack: () -> Unit,
    onOpenOcrTest: () -> Unit,
    showAppText: Boolean = false,
    ocrModels: String? = null,
    viewModel: RecognitionSettingsViewModel = hiltViewModel(),
) {
    val stored by viewModel.settings.collectAsStateWithLifecycle()
    val recognizes = stored?.textSource != TextSource.APP_TEXT_ONLY
    var confirmAppTextOnly by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var appTextTop by remember { mutableStateOf<Int?>(null) }
    // The page is composed once the settings are read; scrolling waits for the app text card to be placed.
    LaunchedEffect(showAppText, appTextTop) {
        val top = appTextTop
        if (showAppText && top != null) scroll.animateScrollTo(top)
    }

    SettingsScaffold(
        stringResource(R.string.recognition_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.RECOGNITION) },
    ) { padding ->
        val settings = stored ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(stringResource(R.string.bubble_recognition)) {
                LabelWithInfo(
                    stringResource(R.string.bubble_ocr_engines),
                    stringResource(R.string.bubble_ocr_engines_info),
                    style = MaterialTheme.typography.titleMedium,
                )
                Segments(
                    options = OcrEngines.entries,
                    selected = settings.ocrEngines,
                    label = {
                        stringResource(
                            when (it) {
                                OcrEngines.BOTH -> R.string.bubble_ocr_engines_both
                                OcrEngines.CLOUD -> R.string.bubble_ocr_engines_cloud
                                OcrEngines.DEVICE -> R.string.bubble_ocr_engines_device
                            },
                        )
                    },
                    onSelect = viewModel::setOcrEngines,
                    enabled = recognizes,
                )
                Hint(stringResource(R.string.bubble_ocr_engines_hint))
                LabelWithInfo(
                    stringResource(R.string.bubble_small_text),
                    stringResource(R.string.bubble_small_text_hint),
                    style = MaterialTheme.typography.titleMedium,
                )
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
                    enabled = recognizes && settings.ocrEngines != OcrEngines.DEVICE,
                )
                SwitchRow(
                    stringResource(R.string.bubble_ocr_saving),
                    settings.ocrSaving,
                    viewModel::setOcrSaving,
                    hint = stringResource(R.string.bubble_ocr_saving_hint),
                    enabled = recognizes && settings.ocrEngines == OcrEngines.BOTH,
                )
                OcrModelRows(enabled = recognizes && settings.ocrEngines != OcrEngines.CLOUD, openLanguage = ocrModels)
            }
            SectionCard(
                stringResource(R.string.recognition_app_text_title),
                modifier = Modifier.onGloballyPositioned { appTextTop = it.positionInParent().y.toInt() },
            ) {
                SwitchRow(
                    stringResource(R.string.bubble_read_app_text),
                    settings.textSource != TextSource.SCREEN,
                    { viewModel.setTextSource(if (it) TextSource.APP_TEXT else TextSource.SCREEN) },
                    hint = stringResource(R.string.bubble_read_app_text_hint),
                    enabled = recognizes,
                    info = stringResource(R.string.bubble_read_app_text_info),
                )
                SwitchRow(
                    stringResource(R.string.bubble_app_text_only),
                    !recognizes,
                    { if (it) confirmAppTextOnly = true else viewModel.setTextSource(TextSource.APP_TEXT) },
                    hint = stringResource(R.string.bubble_app_text_only_hint),
                    info = stringResource(R.string.bubble_app_text_only_info),
                )
            }
            SectionCard(stringResource(R.string.recognition_test_title)) {
                NavigationRow(stringResource(R.string.ocr_test_title), stringResource(R.string.recognition_test_hint), onOpenOcrTest)
            }
        }
    }

    if (confirmAppTextOnly) {
        AlertDialog(
            onDismissRequest = { confirmAppTextOnly = false },
            title = { Text(stringResource(R.string.bubble_app_text_only)) },
            text = { Text(stringResource(R.string.bubble_app_text_only_info)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTextSource(TextSource.APP_TEXT_ONLY)
                    confirmAppTextOnly = false
                }) { Text(stringResource(R.string.bubble_app_text_only_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAppTextOnly = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
