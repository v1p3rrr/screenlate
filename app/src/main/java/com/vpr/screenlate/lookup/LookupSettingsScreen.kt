package com.vpr.screenlate.lookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.TextReplacements
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.TextReplacement
import com.vpr.screenlate.languages.LanguageCard
import com.vpr.screenlate.languages.characterEntryStrings
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.components.formContent
import com.vpr.screenlate.ui.theme.AccentDefaults

private val MAX_RESULT_OPTIONS = listOf(16, 32, 64, 128, 0)

/**
 * Scan length, number of results, romaji and single character entries; the language-bound ones per turned-on language.
 * The script of a language written in several is chosen on the Languages screen.
 */
@Composable
fun LookupSettingsScreen(onBack: () -> Unit, viewModel: LookupSettingsViewModel = hiltViewModel()) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val profiles by viewModel.shown.profiles.collectAsStateWithLifecycle()
    val shown by viewModel.shown.language.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showReplacements by remember { mutableStateOf(false) }
    SettingsScaffold(
        stringResource(R.string.lookup_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.LOOKUP, shown, profiles.several) },
    ) { padding ->
        val settings = loaded ?: return@SettingsScaffold
        if (showReplacements) {
            TextReplacementsDialog(
                rules = settings.textReplacements,
                onUpdate = viewModel::setTextReplacements,
                onDismiss = { showReplacements = false },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val maxResults: @Composable ColumnScope.() -> Unit = { MaxResults(settings.maxResults, viewModel::setMaxResults) }
            // Scan length, single character entries, merge variants and text replacements belong to the shown language.
            val ownRows: @Composable ColumnScope.() -> Unit = {
                ScanLengthRow(settings.scanLength, viewModel::setScanLength)
            }
            val support = shown.support
            val characterRows: @Composable ColumnScope.() -> Unit = {
                if (support.hasCharacterEntries) {
                    val strings = shown.characterEntryStrings
                    SwitchRow(
                        stringResource(strings.label),
                        settings.singleKanji,
                        viewModel::setSingleKanji,
                        hint = stringResource(strings.hint),
                    )
                    SwitchRow(
                        stringResource(R.string.lookup_merge_variants),
                        settings.mergeVariants,
                        viewModel::setMergeVariants,
                        hint = stringResource(R.string.lookup_merge_variants_hint),
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
                TextReplacementsRow(
                    rules = settings.textReplacements,
                    onClick = { showReplacements = true },
                )
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

@Composable
private fun TextReplacementsRow(rules: List<TextReplacement>, onClick: () -> Unit) {
    val active = rules.count { it.enabled }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.lookup_text_replacements), style = MaterialTheme.typography.bodyLarge)
            Hint(
                if (rules.isEmpty()) {
                    stringResource(R.string.lookup_text_replacements_hint)
                } else {
                    stringResource(R.string.lookup_text_replacements_count, active, rules.size)
                },
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextReplacementsDialog(
    rules: List<TextReplacement>,
    onUpdate: (List<TextReplacement>) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var sample by remember { mutableStateOf("") }

    if (adding || editingIndex != null) {
        val current = editingIndex?.let { rules.getOrNull(it) }
        EditReplacementDialog(
            initial = current,
            onSave = { updated ->
                val idx = editingIndex
                val next = if (idx != null && idx in rules.indices) {
                    rules.toMutableList().apply { this[idx] = updated }
                } else {
                    rules + updated
                }
                onUpdate(next)
                adding = false
                editingIndex = null
            },
            onDismiss = {
                adding = false
                editingIndex = null
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lookup_text_replacements)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hint(stringResource(R.string.lookup_text_replacements_hint))
                if (rules.isEmpty()) {
                    Text(
                        stringResource(R.string.lookup_text_replacements_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    rules.forEachIndexed { index, rule ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingIndex = index }
                                .padding(vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "${rule.pattern} → ${rule.replacement}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                val flags = buildList {
                                    if (rule.regex) add(stringResource(R.string.lookup_text_replacement_regex))
                                    if (rule.ignoreCase) add("Aa")
                                }
                                if (flags.isNotEmpty()) {
                                    Text(
                                        text = flags.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    onUpdate(rules.toMutableList().apply { this[index] = rule.copy(enabled = enabled) })
                                },
                                colors = AccentDefaults.switchColors(),
                            )
                            TooltipIconButton(
                                R.drawable.ic_delete,
                                stringResource(R.string.action_delete),
                                onClick = {
                                    onUpdate(rules.toMutableList().apply { removeAt(index) })
                                },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.lookup_text_replacement_add))
                }
                if (rules.isNotEmpty()) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = sample,
                        onValueChange = { sample = it },
                        label = { Text(stringResource(R.string.lookup_text_replacement_test)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (sample.isNotEmpty()) {
                        val preview = remember(sample, rules) { TextReplacements.apply(sample, rules) }
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun EditReplacementDialog(
    initial: TextReplacement?,
    onSave: (TextReplacement) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by remember(initial) { mutableStateOf(initial?.pattern.orEmpty()) }
    var replacement by remember(initial) { mutableStateOf(initial?.replacement.orEmpty()) }
    var regex by remember(initial) { mutableStateOf(initial?.regex ?: false) }
    var ignoreCase by remember(initial) { mutableStateOf(initial?.ignoreCase ?: false) }
    val regexError = remember(pattern, regex) {
        if (!regex || pattern.isEmpty()) null else runCatching { Regex(pattern) }.exceptionOrNull()?.message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.lookup_text_replacement_add else R.string.lookup_text_replacement_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.lookup_text_replacement_find)) },
                    isError = regexError != null,
                    supportingText = regexError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text(stringResource(R.string.lookup_text_replacement_replace)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { regex = !regex },
                ) {
                    Checkbox(
                        checked = regex,
                        onCheckedChange = { regex = it },
                        colors = AccentDefaults.checkboxColors(),
                    )
                    Text(stringResource(R.string.lookup_text_replacement_regex), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ignoreCase = !ignoreCase },
                ) {
                    Checkbox(
                        checked = ignoreCase,
                        onCheckedChange = { ignoreCase = it },
                        colors = AccentDefaults.checkboxColors(),
                    )
                    Text(stringResource(R.string.lookup_text_replacement_ignore_case), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        TextReplacement(
                            pattern = pattern,
                            replacement = replacement,
                            regex = regex,
                            ignoreCase = ignoreCase,
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
                enabled = pattern.isNotEmpty() && regexError == null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
