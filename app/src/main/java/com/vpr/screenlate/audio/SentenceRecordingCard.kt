package com.vpr.screenlate.audio

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.media.RecordingLimits
import com.vpr.screenlate.core.media.RecordingSettings
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.TooltipIconButton

/**
 * Settings card for Sentence Audio Recording on Android 10+ (Q).
 * On earlier Android versions, displays an explanation that the feature requires Android 10+.
 */
@Composable
fun SentenceRecordingCard(viewModel: AudioSettingsViewModel, onOpenAnkiSettings: (() -> Unit)? = null) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        SectionCard(title = stringResource(R.string.sentence_recording_card_title)) {
            Hint(stringResource(R.string.sentence_recording_unsupported_hint))
        }
        return
    }

    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingSettings by viewModel.recordingSettingsState.collectAsStateWithLifecycle()
    val hasSentenceAudioMarker by viewModel.hasSentenceAudioMarker.collectAsStateWithLifecycle()
    var showSilenceDialog by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.sentence_recording_card_title)) {
        SwitchRow(
            label = stringResource(R.string.sentence_recording_toggle),
            checked = isRecording,
            onChange = { enabled ->
                if (!viewModel.setRecordingEnabled(enabled) && enabled) {
                    Toast.makeText(context, R.string.home_bubble_stopped, Toast.LENGTH_SHORT).show()
                }
            },
            hint = stringResource(R.string.sentence_recording_toggle_hint),
            info = stringResource(R.string.sentence_recording_info_body),
        )

        SwitchRow(
            label = stringResource(R.string.sentence_audio_default_editor),
            checked = recordingSettings.defaultEditor,
            onChange = viewModel::setDefaultAudioEditor,
            hint = stringResource(R.string.sentence_audio_default_editor_hint),
        )

        if (!hasSentenceAudioMarker) {
            Hint(
                text = stringResource(R.string.sentence_recording_missing_marker_hint),
                modifier = Modifier.padding(vertical = 4.dp),
            )
            if (onOpenAnkiSettings != null) {
                TextButton(
                    onClick = onOpenAnkiSettings,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.anki_title))
                }
            }
        }

        Text(
            text = stringResource(R.string.sentence_recording_limits_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Hint(stringResource(R.string.sentence_recording_limits_hint))

        DurationLimitRow(
            label = stringResource(R.string.sentence_recording_limit_history),
            millis = recordingSettings.limits.historyMillis,
            onChange = { viewModel.setLimits(recordingSettings.limits.copy(historyMillis = it)) },
        )
        DurationLimitRow(
            label = stringResource(R.string.sentence_recording_limit_waiting),
            millis = recordingSettings.limits.waitingMillis,
            onChange = { viewModel.setLimits(recordingSettings.limits.copy(waitingMillis = it)) },
        )
        DurationLimitRow(
            label = stringResource(R.string.sentence_recording_limit_editor),
            millis = recordingSettings.limits.editorMillis,
            onChange = { viewModel.setLimits(recordingSettings.limits.copy(editorMillis = it)) },
        )

        OutlinedButton(
            onClick = { showSilenceDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sentence_recording_silence_params))
        }
    }

    if (showSilenceDialog) {
        SilenceParamsDialog(
            minSilenceMillis = recordingSettings.minSilenceMillis,
            edgeMarginMillis = recordingSettings.silenceMarginMillis,
            onSave = { minSec, edgeMs ->
                viewModel.setMinSilenceMillis(minSec)
                viewModel.setSilenceMarginMillis(edgeMs)
                showSilenceDialog = false
            },
            onDismiss = { showSilenceDialog = false },
        )
    }
}

@Composable
private fun DurationLimitRow(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
) {
    val minLimit = 30_000L
    val maxLimit = 600_000L
    val step = 30_000L

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TooltipIconButton(
            icon = R.drawable.ic_remove,
            tooltip = stringResource(R.string.sentence_recording_duration_decrease),
            onClick = { if (millis > minLimit) onChange(millis - step) },
            enabled = millis > minLimit,
        )
        Text(
            text = formatDuration(millis),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TooltipIconButton(
            icon = R.drawable.ic_add,
            tooltip = stringResource(R.string.sentence_recording_duration_increase),
            onClick = { if (millis < maxLimit) onChange(millis + step) },
            enabled = millis < maxLimit,
        )
    }
}

@Composable
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> stringResource(R.string.sentence_recording_duration_minutes_seconds, minutes, seconds)
        minutes > 0 -> stringResource(R.string.sentence_recording_duration_minutes, minutes)
        else -> stringResource(R.string.sentence_recording_duration_seconds, seconds)
    }
}

@Composable
private fun SilenceParamsDialog(
    minSilenceMillis: Long,
    edgeMarginMillis: Long,
    onSave: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var minSecText by remember { mutableStateOf((minSilenceMillis / 1000f).toString()) }
    var edgeMsText by remember { mutableStateOf(edgeMarginMillis.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sentence_recording_silence_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minSecText,
                    onValueChange = { minSecText = it },
                    label = { Text(stringResource(R.string.sentence_recording_min_pause)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = edgeMsText,
                    onValueChange = { edgeMsText = it },
                    label = { Text(stringResource(R.string.sentence_recording_edge_margin)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minSec = minSecText.toFloatOrNull() ?: (minSilenceMillis / 1000f)
                    val edgeMs = edgeMsText.toLongOrNull() ?: edgeMarginMillis
                    onSave(
                        (minSec * 1000).toLong().coerceIn(RecordingSettings.MIN_SILENCE_RANGE),
                        edgeMs.coerceIn(RecordingSettings.SILENCE_MARGIN_RANGE),
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
