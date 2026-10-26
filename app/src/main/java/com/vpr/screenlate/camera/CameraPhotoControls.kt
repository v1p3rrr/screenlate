package com.vpr.screenlate.camera

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoWork
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.overlay.R as OverlayR

internal data class CameraAction(@param:DrawableRes val icon: Int, val label: String, val enabled: Boolean = true, val run: () -> Unit)

internal fun cameraActionSlots(width: Float, title: Float, density: Float): Int =
    ((width - minOf(title, width * 0.45f) - 32 * density) / (48 * density)).toInt().coerceAtLeast(1)

/** Uses measured title/available width; actions enter overflow only when their touch targets no longer fit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraToolbar(actions: List<CameraAction>, warning: Boolean) {
    val title = stringResource(R.string.camera_title)
    val density = LocalDensity.current
    val textWidth = rememberTextMeasurer().measure(AnnotatedString(title), style = MaterialTheme.typography.titleLarge, maxLines = 1).size.width
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val slots = maxOf(if (warning) 2 else 1, cameraActionSlots(with(density) { maxWidth.toPx() }, textWidth.toFloat(), density.density))
        val overflow = actions.size > slots
        val shown = if (overflow) actions.take(slots - 1) else actions
        var menu by remember { mutableStateOf(false) }
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                shown.forEach { action -> TooltipIconButton(action.icon, action.label, action.run, enabled = action.enabled) }
                if (overflow) {
                    Box {
                        TooltipIconButton(R.drawable.ic_more_vert, stringResource(R.string.camera_more_actions), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                            actions.drop(shown.size).forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label, Modifier.widthIn(max = 280.dp)) }, enabled = action.enabled,
                                    leadingIcon = { Icon(painterResource(action.icon), null) },
                                    onClick = { menu = false; action.run() },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun CameraRecognitionBar(document: PhotoDocument?, state: CameraRecognitionState?, openLanguages: () -> Unit) {
    if (state == null) return
    val work = (state.work as? PhotoWork.Running)?.takeIf { it.documentId == document?.id }
    val recognized = (document?.text as? PhotoText.Recognized)?.recognition
    Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        TextButton(onClick = openLanguages) {
            Text(stringResource(R.string.camera_language) + ": " + state.language.displayName())
        }
        if (document != null) {
            val status = when {
                work != null -> stringResource(R.string.camera_recognizing,
                    (document.attempt?.language ?: state.language).displayName(), work.completed, work.total)
                recognized != null -> stringResource(R.string.camera_recognized, recognized.language.displayName())
                else -> stringResource(R.string.camera_unrecognized)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            Text(if (state.pending(document)) stringResource(R.string.camera_pending) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 12.dp), minLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (work != null) {
                if (work.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { work.completed.toFloat() / work.total }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun CameraLanguageDialog(state: CameraRecognitionState, select: (Language) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.camera_language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.languages.forEach { language ->
                    Row(Modifier.fillMaxWidth().selectable(state.language == language, role = Role.RadioButton,
                        onClick = { select(language); dismiss() }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.language == language, onClick = null)
                        Text(language.displayName(), Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

internal fun cameraHasWarning(document: PhotoDocument?, work: PhotoWork?): Boolean {
    if (document == null) return false
    val running = (work as? PhotoWork.Running)?.takeIf { it.documentId == document.id }
    val incomplete = document.attempt?.let { it.id != running?.attemptId && (!it.finished || it.remaining.isNotEmpty()) } == true
    return incomplete || document.attemptUnreadable || document.text == PhotoText.Unreadable ||
        (document.text as? PhotoText.Recognized)?.recognition?.failed?.isNotEmpty() == true ||
        (work is PhotoWork.Failed && work.documentId == document.id)
}

@Composable
internal fun CameraRecognitionWarning(document: PhotoDocument, state: CameraRecognitionState?, retry: () -> Unit,
    recognize: () -> Unit, dismiss: () -> Unit) {
    val attempt = document.attempt
    val previous = (document.text as? PhotoText.Recognized)?.recognition
    val work = (state?.work as? PhotoWork.Running)?.takeIf { it.documentId == document.id }
    val failure = (state?.work as? PhotoWork.Failed)?.takeIf { it.documentId == document.id }
    val missing = attempt?.remaining?.size ?: previous?.failed?.size ?: 0
    val total = attempt?.tiles?.size ?: ((previous?.tiles?.size ?: 0) + (previous?.failed?.size ?: 0))
    val canRetry = missing > 0 && (attempt != null || previous?.tiles?.isNotEmpty() == true)
    val causes = (attempt?.results?.mapNotNull { it.failure } ?: previous?.failed?.map { it.cause } ?: emptyList()) +
        listOfNotNull(failure?.cause)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.camera_incomplete)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attempt != null && !attempt.finished && attempt.id != work?.attemptId) Text(stringResource(R.string.camera_interrupted))
                if (missing > 0) Text(stringResource(R.string.camera_partial, missing, total))
                if (total == 0 || failure != null || document.attemptUnreadable || document.text == PhotoText.Unreadable)
                    Text(stringResource(R.string.camera_recognition_failed))
                causes.groupingBy { it }.eachCount().forEach { (cause, count) ->
                    val text = stringResource(when (cause) {
                        FailureCause.OFFLINE -> OverlayR.string.overlay_ocr_error_offline
                        FailureCause.NETWORK -> OverlayR.string.overlay_ocr_error_network
                        FailureCause.REFUSED -> OverlayR.string.overlay_ocr_error_refused
                        FailureCause.SERVER -> OverlayR.string.overlay_ocr_error_other
                        FailureCause.DEVICE, FailureCause.OTHER -> OverlayR.string.overlay_error_ocr
                    })
                    Text("$count · $text")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { dismiss(); if (canRetry) retry() else recognize() }, enabled = work == null) {
                Text(stringResource(if (canRetry) R.string.camera_retry_failed else R.string.camera_recognize))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
