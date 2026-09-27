package com.vpr.screenlate.yomitan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold

private val JSON_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

/** Imports from Yomitan: one profile of its settings export, and its dictionary collection export. */
@Composable
fun YomitanImportScreen(
    onBack: () -> Unit,
    onOpenDictionaries: () -> Unit,
    viewModel: YomitanImportViewModel = hiltViewModel(),
    collection: CollectionImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }
    val collectionState by collection.state.collectAsStateWithLifecycle()
    val collectionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) collection.load(uri)
    }
    SettingsScaffold(stringResource(R.string.yomitan_import_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.yomitan_import_settings)) {
                when (val current = state) {
                    SettingsImportState.Idle, SettingsImportState.NotSettings -> {
                        Text(stringResource(R.string.yomitan_import_settings_hint))
                        if (current == SettingsImportState.NotSettings) {
                            Text(stringResource(R.string.yomitan_import_not_settings), color = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = { settingsPicker.launch(JSON_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.yomitan_import_choose_settings))
                        }
                    }
                    SettingsImportState.Reading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    is SettingsImportState.Loaded -> ProfileChoice(current, viewModel)
                    is SettingsImportState.Done -> {
                        Summary(current)
                        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.yomitan_import_another))
                        }
                    }
                }
            }
            SectionCard(title = stringResource(R.string.yomitan_import_dictionaries)) {
                when (val current = collectionState) {
                    CollectionState.Idle, CollectionState.NotCollection -> {
                        Text(stringResource(R.string.yomitan_import_dictionaries_hint))
                        if (current == CollectionState.NotCollection) {
                            Text(stringResource(R.string.yomitan_collection_not_collection), color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(onClick = { collectionPicker.launch(JSON_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.dictionaries_import_yomitan_backup))
                        }
                    }
                    CollectionState.Scanning -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    is CollectionState.Listed -> CollectionChecklist(current, collection)
                    is CollectionState.Queued -> {
                        Text(stringResource(R.string.yomitan_collection_queued, current.count))
                        Button(onClick = onOpenDictionaries, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.home_dictionaries_open))
                        }
                        TextButton(onClick = collection::reset) { Text(stringResource(R.string.yomitan_import_another)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionChecklist(state: CollectionState.Listed, viewModel: CollectionImportViewModel) {
    Text(stringResource(R.string.yomitan_collection_choose), style = MaterialTheme.typography.labelLarge)
    state.items.forEach { item ->
        val dictionary = item.dictionary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggle(dictionary.title) },
        ) {
            Checkbox(checked = item.checked, onCheckedChange = { viewModel.toggle(dictionary.title) })
            Column {
                Text(dictionary.title)
                Hint(
                    listOfNotNull(
                        stringResource(R.string.yomitan_collection_entries, dictionary.entries),
                        stringResource(R.string.yomitan_collection_media, dictionary.media).takeIf { dictionary.media > 0 },
                        stringResource(R.string.yomitan_collection_installed).takeIf { item.installed },
                    ).joinToString(" · "),
                )
            }
        }
    }
    Hint(stringResource(R.string.yomitan_collection_hint))
    Button(
        onClick = viewModel::import,
        enabled = state.items.any { it.checked },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.yomitan_collection_import, state.items.count { it.checked })) }
}

@Composable
private fun ProfileChoice(state: SettingsImportState.Loaded, viewModel: YomitanImportViewModel) {
    Text(stringResource(R.string.yomitan_import_profile), style = MaterialTheme.typography.labelLarge)
    state.settings.profiles.forEachIndexed { index, profile ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.selectProfile(index) },
        ) {
            RadioButton(selected = index == state.profile, onClick = { viewModel.selectProfile(index) })
            Text(
                if (index == state.settings.currentProfile) {
                    stringResource(R.string.yomitan_import_profile_current, profile.name)
                } else {
                    profile.name
                },
            )
        }
    }
    Text(stringResource(R.string.yomitan_import_sections), style = MaterialTheme.typography.labelLarge)
    YomitanSection.entries.forEach { section ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggleSection(section) },
        ) {
            Checkbox(checked = section in state.sections, onCheckedChange = { viewModel.toggleSection(section) })
            Column {
                Text(stringResource(sectionTitle(section)))
                Hint(stringResource(sectionHint(section)))
            }
        }
    }
    Button(
        onClick = viewModel::apply,
        enabled = !state.applying && state.sections.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.yomitan_import_apply)) }
}

@Composable
private fun Summary(done: SettingsImportState.Done) {
    val summary = done.summary
    Text(stringResource(R.string.yomitan_import_done, done.profile), style = MaterialTheme.typography.titleSmall)
    summary.dictionaries?.let { outcome ->
        SummaryLine(stringResource(R.string.yomitan_summary_dictionaries, outcome.matched))
        if (outcome.missing.isNotEmpty()) {
            SummaryLine(stringResource(R.string.yomitan_summary_dictionaries_missing, outcome.missing.joinToString(", ")), warning = true)
        }
        outcome.sortDictionary?.let { SummaryLine(stringResource(R.string.yomitan_summary_sort, it)) }
        outcome.sortMissing?.let { SummaryLine(stringResource(R.string.yomitan_summary_sort_missing, it), warning = true) }
    }
    summary.anki?.let { outcome ->
        if (outcome.unavailable) SummaryLine(stringResource(R.string.yomitan_summary_anki_unavailable), warning = true)
        outcome.model?.let { SummaryLine(stringResource(R.string.yomitan_summary_anki_model, it)) }
        outcome.modelMissing?.let { SummaryLine(stringResource(R.string.yomitan_summary_anki_model_missing, it), warning = true) }
        outcome.deck?.let { SummaryLine(stringResource(R.string.yomitan_summary_anki_deck, it)) }
        outcome.deckMissing?.let { SummaryLine(stringResource(R.string.yomitan_summary_anki_deck_missing, it), warning = true) }
        if (outcome.droppedFields.isNotEmpty()) {
            SummaryLine(stringResource(R.string.yomitan_summary_anki_dropped, outcome.droppedFields.joinToString(", ")), warning = true)
        }
        if (outcome.savedModels.isNotEmpty()) {
            SummaryLine(stringResource(R.string.yomitan_summary_anki_saved, outcome.savedModels.joinToString(", ")))
        }
    }
    summary.audio?.let { outcome ->
        SummaryLine(
            if (outcome.disabledInYomitan) {
                stringResource(R.string.yomitan_summary_audio_off)
            } else {
                stringResource(R.string.yomitan_summary_audio, outcome.sources)
            },
        )
        if (outcome.unknown.isNotEmpty()) {
            SummaryLine(stringResource(R.string.yomitan_summary_audio_unknown, outcome.unknown.joinToString(", ")), warning = true)
        }
    }
    summary.lookup?.let { outcome ->
        SummaryLine(
            stringResource(
                R.string.yomitan_summary_lookup,
                outcome.scanLength?.toString() ?: "–",
                outcome.maxResults?.toString() ?: "–",
            ),
        )
        if (outcome.skippedReplacements > 0) {
            SummaryLine(stringResource(R.string.yomitan_summary_replacements_skipped, outcome.skippedReplacements))
        }
    }
}

@Composable
private fun SummaryLine(text: String, warning: Boolean = false) {
    Text(
        (if (warning) "⚠ " else "✓ ") + text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

private fun sectionTitle(section: YomitanSection): Int = when (section) {
    YomitanSection.DICTIONARIES -> R.string.yomitan_section_dictionaries
    YomitanSection.ANKI -> R.string.yomitan_section_anki
    YomitanSection.AUDIO -> R.string.yomitan_section_audio
    YomitanSection.LOOKUP -> R.string.yomitan_section_lookup
}

private fun sectionHint(section: YomitanSection): Int = when (section) {
    YomitanSection.DICTIONARIES -> R.string.yomitan_section_dictionaries_hint
    YomitanSection.ANKI -> R.string.yomitan_section_anki_hint
    YomitanSection.AUDIO -> R.string.yomitan_section_audio_hint
    YomitanSection.LOOKUP -> R.string.yomitan_section_lookup_hint
}
