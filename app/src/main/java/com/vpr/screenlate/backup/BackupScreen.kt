package com.vpr.screenlate.backup

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.AccentDefaults
import com.vpr.screenlate.yomitan.YomitanImportCard
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlin.math.roundToInt

private val BACKUP_TYPES = arrayOf("application/zip", "application/octet-stream", "*/*")

/**
 * Saves the chosen settings, fonts and dictionaries to a file the user picks, restores chosen parts from one, and
 * imports from Yomitan. [showYomitan] opens the page at the Yomitan card.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onOpenDictionaries: () -> Unit,
    showYomitan: Boolean = false,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val createChoice by viewModel.createChoice.collectAsStateWithLifecycle()
    val createDraft by viewModel.createDraft.collectAsStateWithLifecycle()
    val restoreChoice by viewModel.restoreChoice.collectAsStateWithLifecycle()
    val createPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.create(uri)
    }
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    val scroll = rememberScrollState()
    var yomitanTop by remember { mutableStateOf<Int?>(null) }
    var yomitanShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showYomitan, yomitanTop) {
        val top = yomitanTop ?: return@LaunchedEffect
        if (showYomitan && !yomitanShown) {
            scroll.scrollTo(top)
            yomitanShown = true
        }
    }
    val working = state is BackupState.Working
    SettingsScaffold(stringResource(R.string.backup_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.backup_create)) {
                Hint(stringResource(R.string.backup_create_hint))
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
                    onClick = viewModel::openCreateChoice,
                    enabled = !working && files != null && createChoice != null,
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
                    is BackupState.Loaded -> Checklist(current, restoreChoice, viewModel)
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
            YomitanImportCard(
                onOpenDictionaries = onOpenDictionaries,
                modifier = Modifier.onGloballyPositioned { yomitanTop = it.positionInParent().y.roundToInt() },
            )
        }
    }
    val available = files
    val draft = createDraft
    if (available != null && draft != null) {
        CreateChoiceDialog(
            files = available,
            choice = draft,
            onChange = viewModel::changeCreateDraft,
            onNext = {
                viewModel.acceptCreateChoice()
                createPicker.launch("screenlate-backup-${LocalDate.now()}.zip")
            },
            onDismiss = viewModel::closeCreateChoice,
        )
    }
}

/** The parts of a new backup, chosen after "Create backup…": the list scrolls, the buttons stay in view. */
@Composable
private fun CreateChoiceDialog(
    files: BackupFiles,
    choice: BackupChoice,
    onChange: (BackupChoice) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sections = choice.sections(BackupSection.entries, files.fonts.map { it.key }, files.dictionaries.map { it.key })
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.backup_parts_save)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceList(
                    sections = BackupSection.entries,
                    choice = choice,
                    fonts = files.fonts,
                    dictionaries = files.dictionaries,
                    listed = files.listed,
                    enabled = true,
                    onChange = onChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onNext, enabled = sections.isNotEmpty()) { Text(stringResource(R.string.action_next)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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
private fun Checklist(state: BackupState.Loaded, choice: BackupChoice, viewModel: BackupViewModel) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.manifest.createdAt))
    Text(stringResource(R.string.backup_loaded, date, state.manifest.appVersion), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.backup_sections), style = MaterialTheme.typography.labelLarge)
    ChoiceList(
        sections = state.sections,
        choice = choice,
        fonts = state.fonts,
        dictionaries = state.dictionaryFiles,
        listed = state.dictionaries,
        enabled = true,
        onChange = viewModel::changeRestoreChoice,
    )
    Hint(stringResource(R.string.backup_replace_warning))
    val sections = choice.sections(state.sections, state.fonts.map { it.key }, state.dictionaryFiles.map { it.key })
    Button(onClick = viewModel::restore, enabled = sections.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
        Text(stringResource(R.string.backup_restore_button), textAlign = TextAlign.Center)
    }
    TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_cancel)) }
}

/**
 * The parts to save or restore: a box per section; fonts and dictionaries have a box for all and expand into a box
 * per file. Items without files are left out.
 *
 * @param listed the dictionaries in the dictionary list.
 */
@Composable
private fun ChoiceList(
    sections: List<BackupSection>,
    choice: BackupChoice,
    fonts: List<BackupFile>,
    dictionaries: List<BackupFile>,
    listed: Int,
    enabled: Boolean,
    onChange: (BackupChoice) -> Unit,
) {
    sections.forEach { section ->
        when (section) {
            BackupSection.FONTS -> if (fonts.isNotEmpty()) {
                FileItem(section, fonts, choice.fonts, enabled) { onChange(choice.copy(fonts = it)) }
            }
            BackupSection.DICTIONARY_FILES -> if (dictionaries.isNotEmpty()) {
                FileItem(section, dictionaries, choice.dictionaries, enabled) { onChange(choice.copy(dictionaries = it)) }
            }
            else -> CheckRow(
                state = ToggleableState(section !in choice.off),
                title = stringResource(sectionTitle(section)),
                hint = if (section == BackupSection.DICTIONARY_LIST) {
                    pluralStringResource(R.plurals.backup_section_dictionary_list_hint, listed, listed)
                } else {
                    stringResource(sectionHint(section))
                },
                enabled = enabled,
                onClick = { onChange(choice.toggle(section)) },
            )
        }
    }
}

@Composable
private fun FileItem(section: BackupSection, files: List<BackupFile>, choice: FileChoice, enabled: Boolean, onChange: (FileChoice) -> Unit) {
    var expanded by rememberSaveable(section) { mutableStateOf(false) }
    val keys = files.map { it.key }
    val chosen = files.filter { choice.isOn(it.key) }
    val context = LocalContext.current
    val hint = if (chosen.isNotEmpty() && chosen.all { it.bytes != null }) {
        stringResource(R.string.backup_files_chosen, chosen.size, files.size, Formatter.formatShortFileSize(context, chosen.sumOf { it.bytes ?: 0 }))
    } else {
        stringResource(R.string.backup_files_chosen_count, chosen.size, files.size)
    }
    CheckRow(
        state = choice.state(keys),
        title = stringResource(sectionTitle(section)),
        hint = hint,
        enabled = enabled,
        onClick = { onChange(choice.toggleAll(keys)) },
        trailing = {
            val label = stringResource(if (expanded) R.string.backup_files_hide else R.string.backup_files_show)
            TooltipIconButton(label, onClick = { expanded = !expanded }) {
                Icon(painterResource(R.drawable.ic_chevron_right), label, Modifier.rotate(if (expanded) -90f else 90f))
            }
        },
    )
    if (expanded) {
        Column(Modifier.padding(start = 32.dp)) {
            files.forEach { file ->
                CheckRow(
                    state = ToggleableState(choice.isOn(file.key)),
                    title = file.name,
                    hint = file.bytes?.let { Formatter.formatShortFileSize(context, it) },
                    enabled = enabled,
                    onClick = { onChange(choice.toggle(file.key, keys)) },
                )
            }
        }
    }
}

@Composable
private fun CheckRow(
    state: ToggleableState,
    title: String,
    hint: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        TriStateCheckbox(state = state, onClick = onClick, enabled = enabled, colors = AccentDefaults.checkboxColors())
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            hint?.let { Hint(it) }
        }
        Box { trailing() }
    }
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
    BackupSection.FONTS -> R.string.backup_section_fonts
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
    BackupSection.DICTIONARY_LIST, BackupSection.DICTIONARY_FILES, BackupSection.FONTS -> R.string.backup_section_general_hint
}
