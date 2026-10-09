package com.vpr.screenlate.backup

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.theme.AccentDefaults
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private val BACKUP_TYPES = arrayOf("application/zip", "application/octet-stream", "*/*")

/** Saves settings (and optionally dictionaries) to a file the user picks, and restores chosen sections from one. */
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    var includeDictionaries by rememberSaveable { mutableStateOf(false) }
    val createPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.create(uri, includeDictionaries)
    }
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    val working = state is BackupState.Working
    SettingsScaffold(stringResource(R.string.backup_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.backup_create)) {
                Hint(stringResource(R.string.backup_create_hint))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !working) { includeDictionaries = !includeDictionaries },
                ) {
                    Checkbox(checked = includeDictionaries, onCheckedChange = { includeDictionaries = it }, enabled = !working, colors = AccentDefaults.checkboxColors())
                    Column {
                        Text(stringResource(R.string.backup_include_dictionaries))
                        Hint(stringResource(R.string.backup_include_dictionaries_hint))
                    }
                }
                val current = state
                when {
                    current is BackupState.Working && !current.restoring -> Progress(stringResource(R.string.backup_creating), current.progress)
                    current is BackupState.Created -> {
                        val size = Formatter.formatShortFileSize(LocalContext.current, current.bytes)
                        Text(
                            if (current.dictionaries > 0) {
                                pluralStringResource(R.plurals.backup_created_dictionaries, current.dictionaries, size, current.dictionaries)
                            } else {
                                stringResource(R.string.backup_created, size)
                            },
                        )
                    }
                    current is BackupState.Failed && current.reason == BackupFailure.WRITE ->
                        Text(stringResource(R.string.backup_write_failed), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { createPicker.launch("screenlate-backup-${LocalDate.now()}.zip") },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                    colors = AccentDefaults.buttonColors(),
                ) { Text(stringResource(R.string.backup_create_button), textAlign = TextAlign.Center) }
            }
            SectionCard(title = stringResource(R.string.backup_restore)) {
                when (val current = state) {
                    is BackupState.Working -> if (current.restoring) {
                        Progress(stringResource(R.string.backup_restoring), current.progress)
                    } else {
                        Hint(stringResource(R.string.backup_restore_hint))
                    }
                    is BackupState.Loaded -> Checklist(current, checked, viewModel)
                    is BackupState.Restored -> {
                        Summary(current.summary)
                        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_close), textAlign = TextAlign.Center)
                        }
                    }
                    else -> {
                        Hint(stringResource(R.string.backup_restore_hint))
                        if (current is BackupState.Failed && current.reason != BackupFailure.WRITE) {
                            Text(
                                stringResource(
                                    when (current.reason) {
                                        BackupFailure.NOT_BACKUP -> R.string.backup_not_backup
                                        BackupFailure.NEWER_VERSION -> R.string.backup_newer
                                        else -> R.string.backup_read_failed
                                    },
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(
                            onClick = { openPicker.launch(BACKUP_TYPES) },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.backup_choose), textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(label: String, progress: Float?) {
    Text(label)
    if (progress == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
    }
}

@Composable
private fun Checklist(state: BackupState.Loaded, checked: Set<BackupSection>, viewModel: BackupViewModel) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.manifest.createdAt))
    Text(stringResource(R.string.backup_loaded, date, state.manifest.appVersion), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.backup_sections), style = MaterialTheme.typography.labelLarge)
    state.manifest.sections.forEach { section ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggle(section) },
        ) {
            Checkbox(checked = section in checked, onCheckedChange = { viewModel.toggle(section) }, colors = AccentDefaults.checkboxColors())
            Column {
                Text(stringResource(sectionTitle(section)))
                Hint(
                    when (section) {
                        BackupSection.DICTIONARY_FILES -> pluralStringResource(
                            R.plurals.backup_section_dictionary_files_hint,
                            state.dictionaryFiles.size,
                            state.dictionaryFiles.size,
                        )
                        BackupSection.DICTIONARY_LIST -> pluralStringResource(
                            R.plurals.backup_section_dictionary_list_hint,
                            state.dictionaries,
                            state.dictionaries,
                        )
                        else -> stringResource(sectionHint(section))
                    },
                )
            }
        }
    }
    Hint(stringResource(R.string.backup_replace_warning))
    Button(onClick = viewModel::restore, enabled = checked.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
        Text(stringResource(R.string.backup_restore_button), textAlign = TextAlign.Center)
    }
    TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun Summary(summary: RestoreSummary) {
    Text(stringResource(R.string.backup_restored), style = MaterialTheme.typography.titleSmall)
    summary.settings.forEach { SummaryLine(stringResource(sectionTitle(it))) }
    summary.fonts?.let { if (it > 0) SummaryLine(pluralStringResource(R.plurals.backup_summary_fonts, it, it)) }
    summary.dictionaryFiles?.let { SummaryLine(pluralStringResource(R.plurals.backup_summary_dictionaries, it, it)) }
    if (summary.listApplied) SummaryLine(stringResource(R.string.backup_section_dictionary_list))
    if (summary.failed.isNotEmpty()) {
        SummaryLine(stringResource(R.string.backup_summary_failed, summary.failed.joinToString(", ")), warning = true)
    }
    if (summary.missing.isNotEmpty()) {
        SummaryLine(stringResource(R.string.backup_summary_missing, summary.missing.joinToString(", ")), warning = true)
    }
    if (summary.keptOn.isNotEmpty()) {
        SummaryLine(stringResource(R.string.dictionaries_kept_on, summary.keptOn.joinToString(", ")), warning = true)
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

private fun sectionTitle(section: BackupSection): Int = when (section) {
    BackupSection.GENERAL -> R.string.backup_section_general
    BackupSection.BUBBLE -> R.string.backup_section_bubble
    BackupSection.LOOKUP -> R.string.backup_section_lookup
    BackupSection.POPUP -> R.string.backup_section_popup
    BackupSection.ANKI -> R.string.backup_section_anki
    BackupSection.AUDIO -> R.string.backup_section_audio
    BackupSection.TRANSLATION -> R.string.backup_section_translation
    BackupSection.DICTIONARY_LIST -> R.string.backup_section_dictionary_list
    BackupSection.DICTIONARY_FILES -> R.string.backup_section_dictionary_files
}

private fun sectionHint(section: BackupSection): Int = when (section) {
    BackupSection.GENERAL -> R.string.backup_section_general_hint
    BackupSection.BUBBLE -> R.string.backup_section_bubble_hint
    BackupSection.LOOKUP -> R.string.backup_section_lookup_hint
    BackupSection.POPUP -> R.string.backup_section_popup_hint
    BackupSection.ANKI -> R.string.backup_section_anki_hint
    BackupSection.AUDIO -> R.string.backup_section_audio_hint
    BackupSection.TRANSLATION -> R.string.backup_section_translation_hint
    BackupSection.DICTIONARY_LIST, BackupSection.DICTIONARY_FILES -> R.string.backup_section_general_hint
}
