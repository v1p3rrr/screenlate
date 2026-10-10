package com.vpr.screenlate.lookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.languages.LanguageCard
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.formContent
import com.vpr.screenlate.ui.theme.AccentDefaults

private val MAX_RESULT_OPTIONS = listOf(16, 32, 64, 128, 0)

/** Scan length, number of results, romaji and single kanji entries; the language-bound ones per turned-on language. */
@Composable
fun LookupSettingsScreen(onBack: () -> Unit, viewModel: LookupSettingsViewModel = hiltViewModel()) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val profiles by viewModel.shown.profiles.collectAsStateWithLifecycle()
    val shown by viewModel.shown.language.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    SettingsScaffold(
        stringResource(R.string.lookup_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.LOOKUP, shown, profiles.several) },
    ) { padding ->
        val settings = loaded ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val maxResults: @Composable ColumnScope.() -> Unit = { MaxResults(settings.maxResults, viewModel::setMaxResults) }
            // Scan length and single kanji entries belong to the shown language; romaji and the result count are shared.
            val ownRows: @Composable ColumnScope.() -> Unit = {
                ScanLengthRow(settings.scanLength, viewModel::setScanLength)
            }
            val support = shown.support
            val characterRows: @Composable ColumnScope.() -> Unit = {
                if (support.hasCharacterEntries) {
                    SwitchRow(
                        stringResource(R.string.lookup_single_kanji),
                        settings.singleKanji,
                        viewModel::setSingleKanji,
                        hint = stringResource(R.string.lookup_single_kanji_hint),
                    )
                }
                if (support.convertsLatin) {
                    SwitchRow(
                        stringResource(R.string.lookup_romaji),
                        settings.romaji,
                        viewModel::setRomaji,
                        hint = stringResource(R.string.lookup_romaji_hint),
                    )
                }
            }
            if (profiles.several) {
                SectionCard(title = stringResource(R.string.lookup_scanning)) { maxResults() }
                LanguageCard(profiles, shown, viewModel.shown::show) {
                    ownRows()
                    characterRows()
                }
            } else {
                SectionCard(title = stringResource(R.string.lookup_scanning)) {
                    ownRows()
                    maxResults()
                    characterRows()
                }
            }
        }
    }
}

@Composable
private fun MaxResults(maxResults: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.lookup_max_results), style = MaterialTheme.typography.labelLarge)
    // An imported value (Yomitan's own setting, e.g. 8) is offered next to the usual ones.
    Segments(
        options = (MAX_RESULT_OPTIONS + maxResults)
            .distinct()
            .sortedBy { if (it == 0) Int.MAX_VALUE else it },
        selected = maxResults,
        label = { if (it == 0) stringResource(R.string.lookup_max_results_unlimited) else it.toString() },
        onSelect = onSelect,
    )
    if (maxResults == 0 || maxResults > LookupSettings.DEFAULT_MAX_RESULTS) {
        Text(
            stringResource(R.string.lookup_max_results_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Hint(stringResource(R.string.lookup_max_results_hint))
    }
}

/**
 * The slider moves freely; the setting is written when the finger is lifted. A longer imported value widens the
 * slider, so touching it does not cut the value down unasked.
 */
@Composable
private fun ScanLengthRow(length: Int, onChange: (Int) -> Unit) {
    var value by remember(length) { mutableStateOf(length.toFloat()) }
    val max = maxOf(SCAN_LENGTH_SLIDER_MAX, length)
    Column {
        Text(stringResource(R.string.lookup_scan_length, value.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = 1f..max.toFloat(),
            steps = max - 2,
            colors = AccentDefaults.sliderColors(),
        )
        Hint(stringResource(R.string.lookup_scan_length_hint))
    }
}

private const val SCAN_LENGTH_SLIDER_MAX = 40
