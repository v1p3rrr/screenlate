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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.ui.components.SectionCard

/** Deck, note type, field templates, duplicate handling and audio sources. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiSettingsScreen(onBack: () -> Unit, viewModel: AnkiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anki_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.availability) {
                AnkiAvailability.NOT_INSTALLED -> SectionCard(title = stringResource(R.string.anki_connection)) {
                    Text(stringResource(R.string.anki_not_installed))
                }
                AnkiAvailability.NO_PERMISSION -> SectionCard(title = stringResource(R.string.anki_connection)) {
                    Text(stringResource(R.string.anki_permission_explanation))
                    Button(onClick = { permission.launch(AnkiDroid.PERMISSION) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.anki_grant_permission))
                    }
                }
                AnkiAvailability.READY -> NoteSettings(state, viewModel)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            AudioSettingsCard(state, viewModel)
        }
    }
}

@Composable
private fun NoteSettings(state: AnkiScreenState, viewModel: AnkiSettingsViewModel) {
    val settings = state.settings
    SectionCard(title = stringResource(R.string.anki_note)) {
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
            Text(
                stringResource(R.string.anki_fields_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.fieldNames.forEach { field ->
                TemplateField(
                    field = field,
                    template = settings.fields[field].orEmpty(),
                    markers = state.markers,
                    onChange = { viewModel.setFieldTemplate(field, it) },
                )
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

@Composable
private fun AudioSettingsCard(state: AnkiScreenState, viewModel: AnkiSettingsViewModel) {
    SectionCard(title = stringResource(R.string.audio_title)) {
        SwitchRow(stringResource(R.string.audio_auto_play), state.audio.autoPlay, viewModel::setAutoPlay)
        Text(
            stringResource(R.string.audio_sources_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.audio.sources.forEachIndexed { index, source ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}. ${stringResource(audioSourceLabel(source.type))}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.moveAudioSource(index, -1) }, enabled = index > 0) { Text("↑") }
                    TextButton(
                        onClick = { viewModel.moveAudioSource(index, 1) },
                        enabled = index < state.audio.sources.lastIndex,
                    ) { Text("↓") }
                    IconButton(onClick = { viewModel.removeAudioSource(index) }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                    }
                }
                if (source.type != AudioSourceType.JAPANESE_POD_101) {
                    EditableText(
                        key = "audio-$index-${source.type}",
                        initial = source.url,
                        label = stringResource(R.string.audio_url_template),
                        onChange = { viewModel.setAudioSourceUrl(index, it) },
                    )
                }
            }
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.audio_add_source))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AudioSourceType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(audioSourceLabel(type))) },
                        onClick = {
                            menu = false
                            viewModel.addAudioSource(type)
                        },
                    )
                }
            }
        }
    }
}

private fun audioSourceLabel(type: AudioSourceType): Int = when (type) {
    AudioSourceType.JAPANESE_POD_101 -> R.string.audio_source_jpod
    AudioSourceType.URL -> R.string.audio_source_url
    AudioSourceType.CUSTOM_JSON -> R.string.audio_source_json
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

@Composable
private fun TemplateField(field: String, template: String, markers: List<String>, onChange: (String) -> Unit) {
    var text by remember(field) { mutableStateOf(template) }
    var menu by remember { mutableStateOf(false) }
    // Templates guessed for a newly selected note type may arrive after the field is shown.
    LaunchedEffect(template) {
        if (text.isEmpty() && template.isNotEmpty()) text = template
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(field) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.anki_insert_marker))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    markers.forEach { marker ->
                        DropdownMenuItem(
                            text = { Text("{$marker}", fontFamily = FontFamily.Monospace) },
                            onClick = {
                                menu = false
                                text += "{$marker}"
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
private fun EditableText(key: String, initial: String, label: String, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
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
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
