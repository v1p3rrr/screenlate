package com.vpr.screenlate.lookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.TextReplacement
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.components.formContent

private val MAX_RESULT_OPTIONS = listOf(16, 32, 64, 128, 0)

/** Scan length, number of results, romaji, single kanji entries and text replacements. */
@Composable
fun LookupSettingsScreen(onBack: () -> Unit, viewModel: LookupSettingsViewModel = hiltViewModel()) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    SettingsScaffold(stringResource(R.string.lookup_title), onBack) { padding ->
        val settings = loaded ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.lookup_scanning)) {
                ScanLengthRow(settings.scanLength, viewModel::setScanLength)
                Text(stringResource(R.string.lookup_max_results), style = MaterialTheme.typography.labelLarge)
                Segments(
                    options = MAX_RESULT_OPTIONS,
                    selected = settings.maxResults.takeIf { it in MAX_RESULT_OPTIONS } ?: LookupSettings.DEFAULT_MAX_RESULTS,
                    label = { if (it == 0) stringResource(R.string.lookup_max_results_unlimited) else it.toString() },
                    onSelect = viewModel::setMaxResults,
                )
                if (settings.maxResults == 0 || settings.maxResults > LookupSettings.DEFAULT_MAX_RESULTS) {
                    Text(
                        stringResource(R.string.lookup_max_results_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Hint(stringResource(R.string.lookup_max_results_hint))
                }
                SwitchRow(
                    stringResource(R.string.lookup_single_kanji),
                    settings.singleKanji,
                    viewModel::setSingleKanji,
                    hint = stringResource(R.string.lookup_single_kanji_hint),
                )
                SwitchRow(
                    stringResource(R.string.lookup_romaji),
                    settings.romaji,
                    viewModel::setRomaji,
                    hint = stringResource(R.string.lookup_romaji_hint),
                )
            }
            ReplacementsCard(settings, viewModel)
        }
    }
}

/** The slider moves freely; the setting is written when the finger is lifted. */
@Composable
private fun ScanLengthRow(length: Int, onChange: (Int) -> Unit) {
    var value by remember(length) { mutableStateOf(length.toFloat()) }
    Column {
        Text(stringResource(R.string.lookup_scan_length, value.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = 1f..SCAN_LENGTH_SLIDER_MAX.toFloat(),
            steps = SCAN_LENGTH_SLIDER_MAX - 2,
        )
        Hint(stringResource(R.string.lookup_scan_length_hint))
    }
}

private const val SCAN_LENGTH_SLIDER_MAX = 40

/**
 * Yomitan's text replacement groups. The screen edits its own copy, so typing is not disturbed by the settings
 * being written back; every change is saved right away.
 */
@Composable
private fun ReplacementsCard(settings: LookupSettings, viewModel: LookupSettingsViewModel) {
    var groups by remember { mutableStateOf(settings.replacementGroups) }
    fun update(new: List<List<TextReplacement>>) {
        groups = new
        viewModel.setReplacementGroups(new)
    }
    SectionCard(title = stringResource(R.string.lookup_replacements)) {
        Hint(stringResource(R.string.lookup_replacements_hint))
        SwitchRow(
            stringResource(R.string.lookup_replacements_original),
            settings.searchOriginal,
            viewModel::setSearchOriginal,
        )
        groups.forEachIndexed { groupIndex, group ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lookup_replacements_group, groupIndex + 1),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { update(groups.filterIndexed { i, _ -> i != groupIndex }) }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.lookup_replacements_delete_group))
                }
            }
            group.forEachIndexed { ruleIndex, rule ->
                ReplacementRule(
                    rule = rule,
                    onChange = { changed ->
                        update(groups.mapIndexed { i, g -> if (i != groupIndex) g else g.mapIndexed { j, r -> if (j == ruleIndex) changed else r } })
                    },
                    onDelete = {
                        update(groups.mapIndexed { i, g -> if (i != groupIndex) g else g.filterIndexed { j, _ -> j != ruleIndex } })
                    },
                )
            }
            TextButton(onClick = {
                update(groups.mapIndexed { i, g -> if (i == groupIndex) g + TextReplacement(pattern = "") else g })
            }) { Text(stringResource(R.string.lookup_replacements_add_rule)) }
        }
        OutlinedButton(
            onClick = { update(groups + listOf(listOf(TextReplacement(pattern = "")))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.lookup_replacements_add_group)) }
        if (groups.isNotEmpty()) ReplacementTest(groups)
    }
}

@Composable
private fun ReplacementRule(rule: TextReplacement, onChange: (TextReplacement) -> Unit, onDelete: () -> Unit) {
    val invalid = rule.pattern.isNotEmpty() && rule.regex() == null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = rule.pattern,
            onValueChange = { onChange(rule.copy(pattern = it)) },
            label = { Text(stringResource(R.string.lookup_replacements_pattern)) },
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.lookup_replacements_invalid)) }
            } else {
                null
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rule.replacement,
            onValueChange = { onChange(rule.copy(replacement = it)) },
            label = { Text(stringResource(R.string.lookup_replacements_replacement)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = doneClearsFocus(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rule.ignoreCase, onCheckedChange = { onChange(rule.copy(ignoreCase = it)) })
            Text(stringResource(R.string.lookup_replacements_ignore_case), modifier = Modifier.weight(1f))
            Switch(checked = rule.enabled, onCheckedChange = { onChange(rule.copy(enabled = it)) })
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.lookup_replacements_delete_rule))
            }
        }
    }
}

/** Shows what each group makes of a sample text. */
@Composable
private fun ReplacementTest(groups: List<List<TextReplacement>>) {
    var sample by remember { mutableStateOf("") }
    HorizontalDivider()
    OutlinedTextField(
        value = sample,
        onValueChange = { sample = it },
        label = { Text(stringResource(R.string.lookup_replacements_test)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = doneClearsFocus(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (sample.isNotEmpty()) {
        LookupSettingsViewModel.replacementPreview(sample, groups).forEachIndexed { index, result ->
            Text(
                stringResource(R.string.lookup_replacements_test_result, index + 1, result),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
