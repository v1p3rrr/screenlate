package com.vpr.screenlate.anki

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiCheck
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiProblem
import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.anki.CheckOutcome
import com.vpr.screenlate.core.anki.message
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.languages.LanguageCard
import com.vpr.screenlate.languages.furiganaMarkerHints
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.BackButton
import com.vpr.screenlate.ui.components.LabelWithInfo
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.components.formContent
import com.vpr.screenlate.ui.components.pending
import com.vpr.screenlate.ui.theme.AccentDefaults

/** Deck, note type, field templates and duplicate handling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiSettingsScreen(onBack: () -> Unit, viewModel: AnkiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profiles by viewModel.shown.profiles.collectAsStateWithLifecycle()
    val shown by viewModel.shown.language.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    val focusManager = LocalFocusManager.current
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anki_title)) },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = { SectionResetButton(SettingsSection.ANKI, shown, profiles.several, onReset = viewModel::resetSettings) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Nearly the whole page belongs to a language, so the language's card only switches it.
            if (profiles.several) {
                LanguageCard(profiles, shown, { language ->
                    focusManager.clearFocus()
                    viewModel.show(language)
                }, hint = stringResource(R.string.anki_languages_hint))
            }
            val check by viewModel.check.collectAsStateWithLifecycle()
            ConnectionCard(
                availability = state.availability,
                check = check,
                onCheck = viewModel::checkConnection,
                onGrant = { permission.launch(AnkiDroid.PERMISSION) },
            )
            when (state.availability) {
                AnkiAvailability.NOT_INSTALLED, AnkiAvailability.NO_PERMISSION -> Unit
                // Drawn from the last answer or the saved setup until AnkiDroid answers, dimmed and without taps.
                AnkiAvailability.READY -> Column(
                    modifier = Modifier.pending(state.refreshing),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AnkiProfileEditors(state.language, state.settings.modelId) { NoteSettings(state, viewModel) }
                }
                null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = AccentDefaults.progress,
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Whether AnkiDroid can be reached, with the way to fix it and the check of the whole setup. */
@Composable
private fun ConnectionCard(availability: AnkiAvailability?, check: ConnectionCheck, onCheck: () -> Unit, onGrant: () -> Unit) {
    SectionCard(title = stringResource(R.string.anki_connection)) {
        when (availability) {
            AnkiAvailability.NOT_INSTALLED -> Text(stringResource(R.string.anki_not_installed))
            AnkiAvailability.NO_PERMISSION -> {
                Text(stringResource(R.string.anki_permission_explanation))
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
                    Text(stringResource(R.string.anki_grant_permission), textAlign = TextAlign.Center)
                }
            }
            AnkiAvailability.READY, null -> Unit
        }
        OutlinedButton(onClick = onCheck, enabled = !check.running, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (check.running) R.string.anki_checking else R.string.anki_check), textAlign = TextAlign.Center)
        }
        check.result?.let { CheckList(it, Modifier.pending(check.running)) }
    }
}

@Composable
private fun CheckList(check: AnkiCheck, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckRow(
            check.installed,
            stringResource(if (check.installed == CheckOutcome.PASSED) R.string.anki_check_installed else R.string.anki_check_not_installed),
        )
        when (check.access) {
            CheckOutcome.PASSED -> CheckRow(check.access, stringResource(R.string.anki_check_access))
            CheckOutcome.FAILED -> CheckRow(
                check.access,
                stringResource(R.string.anki_check_no_access),
                stringResource(R.string.anki_check_fix_access),
            )
            else -> CheckRow(check.access, notChecked(R.string.anki_check_access_item))
        }
        val deck = check.deckName.orEmpty()
        when (check.deck) {
            CheckOutcome.PASSED -> CheckRow(check.deck, stringResource(R.string.anki_check_deck_found, deck))
            CheckOutcome.FAILED -> CheckRow(
                check.deck,
                stringResource(R.string.anki_check_deck_missing, deck),
                stringResource(R.string.anki_problem_fix_deck),
            )
            CheckOutcome.NOT_CHOSEN -> CheckRow(
                check.deck,
                stringResource(R.string.anki_check_deck_not_chosen),
                stringResource(R.string.anki_check_fix_choose),
            )
            CheckOutcome.NO_ANSWER -> CheckRow(check.deck, noAnswer(R.string.anki_deck), stringResource(R.string.anki_check_fix_no_answer))
            CheckOutcome.NOT_CHECKED -> CheckRow(check.deck, notChecked(R.string.anki_deck))
        }
        val model = check.modelName.orEmpty()
        when (check.model) {
            CheckOutcome.PASSED -> CheckRow(check.model, stringResource(R.string.anki_check_model_found, model))
            CheckOutcome.FAILED -> CheckRow(
                check.model,
                stringResource(R.string.anki_check_model_missing, model),
                stringResource(R.string.anki_problem_fix_model),
            )
            CheckOutcome.NOT_CHOSEN -> CheckRow(
                check.model,
                stringResource(R.string.anki_check_model_not_chosen),
                stringResource(R.string.anki_check_fix_choose),
            )
            CheckOutcome.NO_ANSWER -> CheckRow(check.model, noAnswer(R.string.anki_model), stringResource(R.string.anki_check_fix_no_answer))
            CheckOutcome.NOT_CHECKED -> CheckRow(check.model, notChecked(R.string.anki_model))
        }
        when (check.fields) {
            CheckOutcome.PASSED -> CheckRow(check.fields, stringResource(R.string.anki_check_fields_match))
            CheckOutcome.FAILED -> CheckRow(
                check.fields,
                stringResource(R.string.anki_check_fields_differ),
                *listOfNotNull(
                    check.missingFields.takeIf { it.isNotEmpty() }
                        ?.let { stringResource(R.string.anki_check_fields_missing, it.joinToString(", ")) },
                    check.newFields.takeIf { it.isNotEmpty() }
                        ?.let { stringResource(R.string.anki_check_fields_new, it.joinToString(", ")) },
                    stringResource(R.string.anki_check_fix_fields),
                ).toTypedArray(),
            )
            else -> CheckRow(check.fields, notChecked(R.string.anki_fields))
        }
    }
}

@Composable
private fun notChecked(item: Int) = stringResource(R.string.anki_check_not_checked, stringResource(item))

@Composable
private fun noAnswer(item: Int) = stringResource(R.string.anki_check_no_answer, stringResource(item))

/** One item of the check: a check mark when it passed, a cross with the fix under it, a dash when not checked. */
@Composable
private fun CheckRow(outcome: CheckOutcome, text: String, vararg hints: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (icon, tint) = when (outcome) {
            CheckOutcome.PASSED -> R.drawable.ic_check to MaterialTheme.colorScheme.primary
            CheckOutcome.FAILED, CheckOutcome.NOT_CHOSEN, CheckOutcome.NO_ANSWER -> R.drawable.ic_close to MaterialTheme.colorScheme.error
            CheckOutcome.NOT_CHECKED -> R.drawable.ic_remove to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            hints.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AnkiProfileEditors(language: Language, modelId: Long?, content: @Composable () -> Unit) {
    // A focused editor keeps a local draft; another profile or note type must start with its own saved values.
    key(language, modelId) { content() }
}

@Composable
private fun NoteSettings(state: AnkiScreenState, viewModel: AnkiSettingsViewModel) {
    val settings = state.settings
    (state.status as? AnkiStatus.Broken)?.problem?.let { problem ->
        SectionCard(title = stringResource(R.string.anki_problem_title)) {
            Text(stringResource(problem.message), color = MaterialTheme.colorScheme.error)
            Text(
                stringResource(
                    when (problem) {
                        AnkiProblem.MODEL_MISSING -> R.string.anki_problem_fix_model
                        AnkiProblem.DECK_MISSING -> R.string.anki_problem_fix_deck
                        else -> R.string.anki_problem_fix_fields
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (problem == AnkiProblem.FIELDS_CHANGED) {
                Button(onClick = viewModel::updateFieldList, modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
                    Text(stringResource(R.string.anki_update_fields), textAlign = TextAlign.Center)
                }
            }
        }
    }
    SectionCard(title = stringResource(R.string.anki_note)) {
        val translation by viewModel.translationInNotes.collectAsStateWithLifecycle()
        translation?.let { on ->
            SwitchRow(
                label = stringResource(R.string.translation_anki),
                checked = on,
                onChange = viewModel::setTranslationInNotes,
                hint = stringResource(R.string.translation_anki_hint),
            )
        }
        Picker(
            label = stringResource(R.string.anki_deck),
            value = settings.deckName,
            options = state.decks,
            name = { it.name },
            onSelect = viewModel::selectDeck,
        )
        Picker(
            label = stringResource(R.string.anki_model),
            value = settings.modelName,
            options = state.models,
            name = { it.name },
            onSelect = viewModel::selectModel,
        )
        EditableText(
            key = "tags",
            initial = settings.tags,
            label = stringResource(R.string.anki_tags),
            onChange = viewModel::setTags,
        )
    }

    if (state.fieldNames.isNotEmpty()) {
        SectionCard(title = stringResource(R.string.anki_fields)) {
            val hint = stringResource(R.string.anki_fields_hint)
            val rightToLeftHint = stringResource(R.string.anki_fields_hint_rtl)
            LabelWithInfo(
                stringResource(R.string.anki_fields_how),
                if (state.language.support.rightToLeft) "$hint\n\n$rightToLeftHint" else hint,
            )
            OutlinedButton(onClick = viewModel::suggestTemplates, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.anki_suggest_templates), textAlign = TextAlign.Center)
            }
            val overwrite = settings.duplicateCheck && settings.duplicateBehavior == DuplicateBehavior.OVERWRITE
            state.fieldNames.forEach { field ->
                TemplateField(
                    field = field,
                    template = settings.fields[field].orEmpty(),
                    markers = state.markers,
                    hints = state.language.furiganaMarkerHints,
                    addMarker = { text, marker ->
                        FieldTemplate.withMarker(text, marker, state.language, firstField = field == state.fieldNames.first())
                    },
                    onChange = { viewModel.setFieldTemplate(field, it) },
                )
                if (overwrite) {
                    OverwriteModePicker(
                        mode = settings.overwriteModes[field] ?: OverwriteMode.COALESCE,
                        onSelect = { viewModel.setOverwriteMode(field, it) },
                    )
                }
            }
        }
    }

    SectionCard(title = stringResource(R.string.anki_duplicates)) {
        SwitchRow(stringResource(R.string.anki_duplicate_check), settings.duplicateCheck, viewModel::setDuplicateCheck)
        if (settings.duplicateCheck) {
            Text(stringResource(R.string.anki_duplicate_scope), style = MaterialTheme.typography.labelLarge)
            Segments(
                options = DuplicateScope.entries,
                selected = settings.duplicateScope,
                label = {
                    stringResource(
                        when (it) {
                            DuplicateScope.COLLECTION -> R.string.anki_scope_collection
                            DuplicateScope.DECK -> R.string.anki_scope_deck
                            DuplicateScope.DECK_ROOT -> R.string.anki_scope_deck_root
                        },
                    )
                },
                onSelect = viewModel::setDuplicateScope,
            )
            SwitchRow(stringResource(R.string.anki_duplicate_all_models), settings.duplicateAllModels, viewModel::setDuplicateAllModels)
            Text(stringResource(R.string.anki_duplicate_behavior), style = MaterialTheme.typography.labelLarge)
            Segments(
                options = DuplicateBehavior.entries,
                selected = settings.duplicateBehavior,
                label = {
                    stringResource(
                        when (it) {
                            DuplicateBehavior.PREVENT -> R.string.anki_behavior_prevent
                            DuplicateBehavior.OVERWRITE -> R.string.anki_behavior_overwrite
                            DuplicateBehavior.NEW -> R.string.anki_behavior_new
                        },
                    )
                },
                onSelect = viewModel::setDuplicateBehavior,
            )
        }
    }
}

/** How this field changes when a duplicate overwrites an existing note. */
@Composable
private fun OverwriteModePicker(mode: OverwriteMode, onSelect: (OverwriteMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                stringResource(R.string.anki_overwrite_mode, stringResource(overwriteModeLabel(mode))),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OverwriteMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(overwriteModeLabel(option))) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private fun overwriteModeLabel(mode: OverwriteMode): Int = when (mode) {
    OverwriteMode.COALESCE -> R.string.anki_overwrite_coalesce
    OverwriteMode.COALESCE_NEW -> R.string.anki_overwrite_coalesce_new
    OverwriteMode.OVERWRITE -> R.string.anki_overwrite_overwrite
    OverwriteMode.SKIP -> R.string.anki_overwrite_skip
    OverwriteMode.APPEND -> R.string.anki_overwrite_append
    OverwriteMode.PREPEND -> R.string.anki_overwrite_prepend
}

@Composable
private fun <T> Picker(label: String, value: String?, options: List<T>, name: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    value ?: stringResource(R.string.anki_choose),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(name(option)) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A field's template with a + menu of [markers]; [hints] puts a second line under some of them. [addMarker] gives the
 * template with a picked marker added (in a right-to-left span for such a language's text).
 */
@Composable
internal fun TemplateField(
    field: String,
    template: String,
    markers: List<String>,
    hints: Map<String, Int> = emptyMap(),
    addMarker: (template: String, marker: String) -> String = { text, marker -> "$text{$marker}" },
    onChange: (String) -> Unit,
) {
    var text by remember(field) { mutableStateOf(template) }
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Suggested templates or a note type switch change the template from outside; typing must not be overwritten,
    // and a change that arrived while typing shows once the field loses focus.
    LaunchedEffect(template, focused) {
        if (!focused && template != text) text = template
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(field) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        trailingIcon = {
            Box {
                TooltipIconButton(R.drawable.ic_add, stringResource(R.string.anki_insert_marker), onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    markers.forEach { marker ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("{$marker}", fontFamily = FontFamily.Monospace)
                                    hints[marker]?.let { hint ->
                                        Text(
                                            stringResource(hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                menu = false
                                text = addMarker(text, marker)
                                onChange(text)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun EditableText(key: String, initial: String, label: String, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    // A reset changes the value from outside; typing is not overwritten.
    LaunchedEffect(initial, focused) {
        if (!focused && initial != text) text = initial
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = doneClearsFocus(),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
    )
}
