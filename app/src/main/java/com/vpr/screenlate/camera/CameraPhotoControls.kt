package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.geometry.Box as PhotoBox
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoViewport
import com.vpr.screenlate.core.photos.PhotoWork
import com.vpr.screenlate.overlay.ui.Flags
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.LocalEInk
import java.text.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vpr.screenlate.overlay.R as OverlayR

/** A top-bar action; [inMenu] keeps it in the overflow menu even when there is room for its icon. */
internal data class CameraAction(
    @param:DrawableRes val icon: Int,
    val label: String,
    val enabled: Boolean = true,
    val inMenu: Boolean = false,
    val run: () -> Unit,
)

internal fun cameraActionSlots(width: Float, title: Float, density: Float): Int =
    ((width - minOf(title, width * 0.45f) - 32 * density) / (48 * density)).toInt().coerceAtLeast(1)

/**
 * Uses measured title/available width; actions enter overflow only when their touch targets no longer fit, besides
 * those kept in the menu. [navigation] is the icon before the title, such as the way back to the photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraToolbar(actions: List<CameraAction>, navigation: CameraAction? = null) {
    val title = stringResource(R.string.camera_title)
    val density = LocalDensity.current
    val textWidth = rememberTextMeasurer().measure(AnnotatedString(title), style = MaterialTheme.typography.titleLarge, maxLines = 1).size.width
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(density) { (if (navigation != null) maxWidth - 48.dp else maxWidth).toPx() }
        val slots = cameraActionSlots(width, textWidth.toFloat(), density.density)
        val icons = actions.filterNot { it.inMenu }
        val kept = actions.filter { it.inMenu }
        val overflow = icons.size + (if (kept.isEmpty()) 0 else 1) > slots
        val shown = if (overflow) icons.take(slots - 1) else icons
        val menuActions = icons.drop(shown.size) + kept
        var menu by remember { mutableStateOf(false) }
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                navigation?.let { TooltipIconButton(it.icon, it.label, it.run, enabled = it.enabled) }
            },
            actions = {
                shown.forEach { action -> TooltipIconButton(action.icon, action.label, action.run, enabled = action.enabled) }
                if (menuActions.isNotEmpty()) {
                    Box {
                        TooltipIconButton(R.drawable.ic_more_vert, stringResource(R.string.camera_more_actions), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                            menuActions.forEach { action ->
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

/** The status line's parts; [CameraStatus.Recognized.language] is set only when it differs from the selected one. */
internal sealed interface CameraStatus {
    data class Recognizing(val done: Int, val total: Int) : CameraStatus
    data class Recognized(val language: Language?, val done: Int, val total: Int, val at: Long, val pending: CameraPending?) : CameraStatus
    data class Unrecognized(val pending: CameraPending?) : CameraStatus
}

internal fun cameraStatus(document: PhotoDocument, state: CameraRecognitionState): CameraStatus {
    val work = (state.work as? PhotoWork.Running)?.takeIf { it.documentId == document.id }
    if (work != null) return CameraStatus.Recognizing(work.completed, work.total)
    val pending = state.pendingReason(document)
    val recognition = (document.text as? PhotoText.Recognized)?.recognition ?: return CameraStatus.Unrecognized(pending)
    val done = recognition.tiles.size
    return CameraStatus.Recognized(recognition.language.takeIf { it != state.language }, done, done + recognition.failed.size,
        recognition.recognizedAt, pending)
}

@Composable
private fun cameraStatusText(status: CameraStatus): String {
    val parts = mutableListOf<String>()
    @Composable fun regions(done: Int, total: Int) {
        // A photo recognized as one piece has nothing to count.
        if (total > 1) parts += pluralStringResource(R.plurals.camera_status_regions, total, done, total)
    }
    @Composable fun pending(reason: CameraPending?) {
        when (reason) {
            CameraPending.LANGUAGE -> parts += stringResource(R.string.camera_pending_language)
            CameraPending.SETTINGS -> parts += stringResource(R.string.camera_pending)
            null -> Unit
        }
    }
    when (status) {
        is CameraStatus.Recognizing -> {
            parts += stringResource(R.string.camera_status_recognizing)
            regions(status.done, status.total)
        }
        is CameraStatus.Recognized -> {
            parts += status.language?.let { stringResource(R.string.camera_recognized, it.displayName()) }
                ?: stringResource(R.string.camera_status_recognized)
            regions(status.done, status.total)
            parts += DateUtils.formatSameDayTime(status.at, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()
            pending(status.pending)
        }
        is CameraStatus.Unrecognized -> {
            parts += stringResource(R.string.camera_unrecognized)
            pending(status.pending)
        }
    }
    return parts.joinToString(" · ")
}

/**
 * The photo's recognition controls above the viewer: the language chip, the warning and Recognize on the first row,
 * the status on the second, the progress along the bottom edge. A pending change tints the strip, or frames it on e-ink.
 */
@Composable
internal fun CameraStatusStrip(
    document: PhotoDocument,
    state: CameraRecognitionState,
    warning: Boolean,
    enabled: Boolean,
    onSelect: (Language) -> Unit,
    onManageLanguages: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onRecognize: () -> Unit,
    onWarning: () -> Unit,
) {
    val status = cameraStatus(document, state)
    val pending = state.pendingReason(document) != null
    val eInk = LocalEInk.current
    val colors = MaterialTheme.colorScheme
    val tinted = pending && !eInk
    Surface(
        color = if (tinted) colors.tertiaryContainer else colors.surfaceContainer,
        contentColor = if (tinted) colors.onTertiaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth().then(if (pending && eInk) Modifier.border(2.dp, colors.onSurface) else Modifier),
    ) {
        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { CameraLanguageChip(state, onSelect, onManageLanguages, onMenu) }
                if (warning) {
                    TooltipIconButton(R.drawable.ic_warning, stringResource(R.string.camera_incomplete), onWarning)
                }
                if (status !is CameraStatus.Recognizing) {
                    val label = stringResource(R.string.camera_recognize_short)
                    val description = stringResource(R.string.camera_recognize)
                    val button = Modifier.semantics { contentDescription = description }
                    if (pending || document.text !is PhotoText.Recognized) {
                        Button(onClick = onRecognize, enabled = enabled, modifier = button) { Text(label, maxLines = 1) }
                    } else {
                        TextButton(onClick = onRecognize, enabled = enabled, modifier = button) { Text(label, maxLines = 1) }
                    }
                }
            }
            Text(
                cameraStatusText(status),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (pending && eInk) FontWeight.Bold else null,
                color = if (tinted) colors.onTertiaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (status is CameraStatus.Recognizing) {
                    if (status.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { status.done.toFloat() / status.total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The photo language as a chip with its flag; tapping opens a menu of the turned-on languages. [overlay] draws it over
 * the live camera. [onMenu] reports the menu opening and closing, since an open menu covers the photo.
 */
@Composable
internal fun CameraLanguageChip(
    state: CameraRecognitionState,
    onSelect: (Language) -> Unit,
    onManageLanguages: () -> Unit,
    onMenu: (Boolean) -> Unit,
    overlay: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { onMenu(expanded) }
    DisposableEffect(Unit) { onDispose { onMenu(false) } }
    val eInk = LocalEInk.current
    val name = state.language.displayName()
    val description = stringResource(R.string.camera_language) + ": " + name
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { ChipFlag(state.language, state.flagCountry(state.language)) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), null, Modifier.size(18.dp)) },
            colors = if (overlay && !eInk) {
                AssistChipDefaults.assistChipColors(
                    containerColor = Color.Black.copy(alpha = 0.55f), labelColor = Color.White,
                    trailingIconContentColor = Color.White,
                )
            } else if (overlay) {
                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
            } else {
                AssistChipDefaults.assistChipColors()
            },
            border = if (overlay && !eInk) null else AssistChipDefaults.assistChipBorder(enabled = true),
            modifier = Modifier.semantics { contentDescription = description },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 480.dp)) {
            Text(
                stringResource(R.string.camera_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.languages.forEach { language ->
                val note = when (state.gaps[language]) {
                    LanguageGap.DICTIONARY -> stringResource(R.string.camera_language_no_dictionary)
                    LanguageGap.MODEL -> stringResource(R.string.camera_language_no_model)
                    null -> if (language == state.active) stringResource(R.string.languages_active) else null
                }
                DropdownMenuItem(
                    text = {
                        Column(Modifier.widthIn(max = 260.dp)) {
                            Text(language.displayName())
                            note?.let {
                                Text(
                                    it, style = MaterialTheme.typography.bodySmall,
                                    color = if (state.gaps[language] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = { ChipFlag(language, state.flagCountry(language), 24) },
                    trailingIcon = if (language == state.language) {
                        { Icon(painterResource(R.drawable.ic_check), null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.camera_manage_languages)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings), null) },
                onClick = {
                    expanded = false
                    onManageLanguages()
                },
            )
        }
    }
}

/** A language's round flag, or its code on e-ink or without a flag. */
@Composable
private fun ChipFlag(language: Language, country: String, sizeDp: Int = 18) {
    val flag = Flags.drawable(country)
    if (LocalEInk.current || flag == null) {
        Text(Flags.code(language), style = MaterialTheme.typography.labelSmall)
    } else {
        Image(painterResource(flag), contentDescription = null, modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
    }
}

internal fun cameraHasWarning(document: PhotoDocument?, work: PhotoWork?): Boolean {
    if (document == null) return false
    val running = (work as? PhotoWork.Running)?.takeIf { it.documentId == document.id }
    val incomplete = document.attempt?.let { it.id != running?.attemptId && (!it.finished || it.remaining.isNotEmpty()) } == true
    return incomplete || document.attemptUnreadable || document.text == PhotoText.Unreadable ||
        (document.text as? PhotoText.Recognized)?.recognition?.failed?.isNotEmpty() == true ||
        (work is PhotoWork.Failed && work.documentId == document.id)
}

/** The regions of [document] that are missing from its text: the unfinished attempt's, else the last result's. */
internal fun cameraMissingRegions(document: PhotoDocument): List<PhotoBox> =
    document.attempt?.remaining?.map { it.box }
        ?: (document.text as? PhotoText.Recognized)?.recognition?.failed?.map { it.box }
        ?: emptyList()

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
    val regions = cameraMissingRegions(document)
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(painterResource(R.drawable.ic_warning), null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.camera_incomplete)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attempt != null && !attempt.finished && attempt.id != work?.attemptId) Text(stringResource(R.string.camera_interrupted))
                if (missing > 0) {
                    Text(stringResource(R.string.camera_partial, missing, total) + " " + stringResource(R.string.camera_partial_words))
                }
                if (total == 0 || failure != null || document.attemptUnreadable || document.text == PhotoText.Unreadable)
                    Text(stringResource(R.string.camera_recognition_failed))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (regions.isNotEmpty()) RegionMap(document, regions, Modifier.width(96.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        causes.groupingBy { it }.eachCount().forEach { (cause, count) ->
                            val text = stringResource(when (cause) {
                                FailureCause.OFFLINE -> OverlayR.string.overlay_ocr_error_offline
                                FailureCause.NETWORK -> OverlayR.string.overlay_ocr_error_network
                                FailureCause.REFUSED -> OverlayR.string.overlay_ocr_error_refused
                                FailureCause.SERVER -> OverlayR.string.overlay_ocr_error_other
                                FailureCause.DEVICE, FailureCause.OTHER -> OverlayR.string.overlay_error_ocr
                            })
                            Text("$count · $text", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
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

/** A small picture of the photo with the [regions] that are missing from its text marked on it. */
@Composable
private fun RegionMap(document: PhotoDocument, regions: List<PhotoBox>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val widthPx = with(density) { 96.dp.toPx() }
    var bitmap by remember(document.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(document.id) {
        val sample = displaySampleSize(widthPx / document.width)
        bitmap = withContext(Dispatchers.IO) { runCatching { decodeForDisplay(document, sample) }.getOrNull() }
    }
    val error = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.aspectRatio(document.width.toFloat() / document.height)) {
        val viewport = PhotoViewport.fit(document.width, document.height, PhotoBox(0f, 0f, size.width, size.height))
        bitmap?.let { image ->
            val composed = image.asImageBitmap()
            withTransform({ transform(imageToViewer(document, viewport, 0f, 0f)) }) {
                drawImage(composed, dstSize = IntSize(document.rawWidth, document.rawHeight))
            }
        }
        drawRect(outline, style = Stroke(1.dp.toPx()))
        regions.forEach { region ->
            val box = viewport.toScreen(region)
            drawRect(error.copy(alpha = 0.3f), Offset(box.left, box.top), Size(box.width, box.height))
            drawRect(error, Offset(box.left, box.top), Size(box.width, box.height), style = Stroke(1.5.dp.toPx()))
        }
    }
}
