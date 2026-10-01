package com.vpr.screenlate.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.audio.AudioCandidate
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.anki.label
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.components.WithTooltip
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.theme.AccentDefaults

/** Auto-play, volume, the audio sources in priority order, and a test of every source with a sample word. */
@Composable
fun AudioSettingsSection(viewModel: AudioSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audio = settings ?: return
    var editing by remember { mutableStateOf<Pair<Int?, AudioSource>?>(null) }

    SectionCard(title = stringResource(R.string.audio_title)) {
        SwitchRow(stringResource(R.string.audio_auto_play), audio.autoPlay, viewModel::setAutoPlay)
        VolumeRow(audio.volume, viewModel::setVolume)
        HorizontalDivider()
        Text(stringResource(R.string.audio_sources), style = MaterialTheme.typography.labelLarge)
        Hint(stringResource(R.string.audio_sources_hint))
        audio.sources.forEachIndexed { index, source ->
            SourceRow(
                index = index,
                source = source,
                isLast = index == audio.sources.lastIndex,
                onMove = { viewModel.move(index, it) },
                onEdit = { editing = index to source },
                onDelete = { viewModel.remove(index) },
            )
        }
        OutlinedButton(
            onClick = { editing = null to AudioSource(AudioSourceType.JAPANESE_POD_101) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.audio_add_source), textAlign = TextAlign.Center) }
        TextButton(onClick = viewModel::resetSources) { Text(stringResource(R.string.audio_reset_sources)) }
        HorizontalDivider()
        TestPanel(viewModel)
    }

    editing?.let { (index, source) ->
        SourceDialog(
            initial = source,
            viewModel = viewModel,
            onDismiss = {
                editing = null
                viewModel.clearDialogTest()
            },
            onSave = {
                viewModel.save(index, it)
                editing = null
                viewModel.clearDialogTest()
            },
        )
    }
}

@Composable
private fun SourceRow(
    index: Int,
    source: AudioSource,
    isLast: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
        ) {
            Text("${index + 1}. ${stringResource(source.type.label)}", style = MaterialTheme.typography.bodyLarge)
            if (source.type.hasUrl) {
                Text(
                    source.url.ifEmpty { stringResource(R.string.audio_url_missing) },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (source.url.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source.type == AudioSourceType.TEXT_TO_SPEECH) Hint(stringResource(R.string.audio_tts_hint))
        }
        WithTooltip(stringResource(R.string.action_move_up)) {
            TextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text("↑") }
        }
        WithTooltip(stringResource(R.string.action_move_down)) {
            TextButton(onClick = { onMove(1) }, enabled = !isLast) { Text("↓") }
        }
        TooltipIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = onDelete)
    }
}

/** Plays what each source has for a sample word. */
@Composable
private fun TestPanel(viewModel: AudioSettingsViewModel) {
    val word by viewModel.testWord.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val played by viewModel.played.collectAsStateWithLifecycle()
    Text(stringResource(R.string.audio_test_title), style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = word,
            onValueChange = { viewModel.testWord.value = it },
            label = { Text(stringResource(R.string.audio_test_word)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = doneClearsFocus(),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = viewModel::testAll) { Text(stringResource(R.string.audio_test)) }
    }
    tests.forEachIndexed { index, test ->
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                "${index + 1}. ${stringResource(test.source.type.label)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                test.loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentDefaults.progress)
                test.error != null -> {
                    Text(
                        stringResource(R.string.audio_test_failed, audioErrorText(test.error, test.source)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (localNetworkMissing(LocalContext.current, test.source.url)) LocalNetworkAllowButton()
                }
                test.candidates.isEmpty() -> Hint(stringResource(R.string.audio_test_none))
                else -> test.candidates.forEach { candidate ->
                    CandidateRow(candidate, played.candidateId == candidate.id && played.sourceIndex == index, played.missing) {
                        viewModel.play(candidate, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: AudioCandidate, playedNow: Boolean, missing: Boolean, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 2.dp),
    ) {
        Text("▶", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Text(
            candidate.name.ifEmpty { hostOf(candidate.url).ifEmpty { stringResource(candidate.source.type.label) } },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (playedNow && missing) {
            Text(
                stringResource(if (candidate.isSpeech) R.string.audio_test_no_voice else R.string.audio_test_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Adds or edits a source; "Test" asks the source for the test word and plays its first clip. */
@Composable
private fun SourceDialog(
    initial: AudioSource,
    viewModel: AudioSettingsViewModel,
    onDismiss: () -> Unit,
    onSave: (AudioSource) -> Unit,
) {
    val askLocalNetwork = rememberLocalNetworkAsk()
    var type by remember { mutableStateOf(initial.type) }
    var url by remember { mutableStateOf(initial.url) }
    var menu by remember { mutableStateOf(false) }
    val test by viewModel.dialogTest.collectAsStateWithLifecycle()
    val played by viewModel.played.collectAsStateWithLifecycle()
    val source = AudioSource(type, if (type.hasUrl) url.trim() else "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_source_dialog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(type.label), textAlign = TextAlign.Center)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        AudioSourceType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.label)) },
                                onClick = {
                                    menu = false
                                    type = option
                                    viewModel.clearDialogTest()
                                },
                            )
                        }
                    }
                }
                if (type.hasUrl) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.audio_url_template)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = doneClearsFocus(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(
                        stringResource(
                            if (type == AudioSourceType.CUSTOM_JSON) R.string.audio_url_json_hint else R.string.audio_url_hint,
                        ),
                    )
                }
                if (type == AudioSourceType.TEXT_TO_SPEECH) Hint(stringResource(R.string.audio_tts_hint))
                test?.let { result ->
                    Text(
                        when {
                            result.loading -> stringResource(R.string.audio_test_running)
                            result.error != null -> stringResource(R.string.audio_test_failed, audioErrorText(result.error, result.source))
                            result.candidates.isEmpty() -> stringResource(R.string.audio_test_none)
                            played.sourceIndex == -1 && played.missing -> stringResource(
                                if (type == AudioSourceType.TEXT_TO_SPEECH) R.string.audio_test_no_voice else R.string.audio_test_none,
                            )
                            else -> stringResource(R.string.audio_test_found, result.candidates.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(source) }, enabled = !type.hasUrl || url.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    askLocalNetwork(source.url)
                    viewModel.testInDialog(source)
                }, enabled = !type.hasUrl || url.isNotBlank()) {
                    Text(stringResource(R.string.audio_test))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** The slider moves freely; the setting is written when the finger is lifted. */
@Composable
private fun VolumeRow(volume: Int, onChange: (Int) -> Unit) {
    var value by remember(volume) { mutableStateOf(volume.toFloat()) }
    Column {
        Text(stringResource(R.string.audio_volume, value.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = 0f..100f,
            colors = AccentDefaults.sliderColors(),
        )
    }
}

private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
