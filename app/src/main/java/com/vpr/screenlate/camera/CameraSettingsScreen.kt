package com.vpr.screenlate.camera

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.HistoryLimits
import com.vpr.screenlate.core.photos.HistorySettings
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoHistory
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.ui.components.ConfirmDelete
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.components.formContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUsage(val count: Int, val bytes: Long)

/** How photos are read: [highAccuracy] reads them by tiles; the [engines] are those of screen recognition. */
data class CameraReading(val highAccuracy: Boolean, val engines: OcrEngines)

/**
 * New limits that wait for the user's answer, since they delete the [excess] oldest photos of the history; [close]:
 * they were typed before leaving the page, which closes once they are stored.
 */
data class PendingLimits(val limits: HistoryLimits, val excess: Int, val close: Boolean = false)

/** [CLOSE]: the limits typed before leaving are stored, and the page may close. */
enum class CameraSettingsEvent { SAVE_FAILED, CHANGE_FAILED, CLOSE }

@HiltViewModel
class CameraSettingsViewModel @Inject constructor(
    private val history: PhotoHistory,
    private val documents: PhotoDocuments,
    private val preferences: CameraPreferences,
    overlaySettings: OverlaySettingsRepository,
) : ViewModel() {
    val settings: StateFlow<HistorySettings?> = preferences.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val reading: StateFlow<CameraReading?> = combine(preferences.highAccuracy, overlaySettings.settings) { highAccuracy, overlay ->
        CameraReading(highAccuracy, overlay.ocrEngines)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val usage: StateFlow<HistoryUsage?> = history.entries
        .map { entries -> HistoryUsage(entries.size, entries.sumOf { it.bytes }) }
        .catch { e ->
            Log.w(TAG, "Reading the history failed: " + e.javaClass.simpleName)
            emit(HistoryUsage(0, 0))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutablePending = MutableStateFlow<PendingLimits?>(null)
    val pending = mutablePending.asStateFlow()

    /** Grows when a typed limit is not taken, so that its field shows the stored one again. */
    private val mutableResets = MutableStateFlow(0)
    val resets = mutableResets.asStateFlow()
    private val messages = Channel<CameraSettingsEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun setHighAccuracy(on: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setHighAccuracy(on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // As with other settings, the switch then shows the stored value.
                Log.w(TAG, "Changing the accuracy failed: " + e.javaClass.simpleName)
            }
        }
    }

    /** Turning automatic saving on also saves the current photo. */
    fun setAutoSave(on: Boolean) {
        viewModelScope.launch {
            if (!change { preferences.setAutoSave(on) } || !on) return@launch
            try {
                val photo = documents.current.first()
                if (photo != null && !photo.saved && !history.save(photo.id, preferences.history.first().limits)) {
                    messages.send(CameraSettingsEvent.SAVE_FAILED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving the current photo failed: " + e.javaClass.simpleName)
                messages.send(CameraSettingsEvent.SAVE_FAILED)
            }
        }
    }

    /** Stores [limits] at once, or asks first when they would delete photos from the history. */
    fun setLimits(limits: HistoryLimits) = submit(limits, close = false)

    /**
     * Stores [limits], typed but not stored yet, before the page closes ([CameraSettingsEvent.CLOSE]). Limits that
     * would delete photos ask first; the page closes after Delete and stays after Cancel.
     */
    fun close(limits: HistoryLimits) = submit(limits, close = true)

    private fun submit(limits: HistoryLimits, close: Boolean) {
        if (mutablePending.value != null) return
        // A field stores its limit when it loses focus, also when the page closes and this model is cleared right
        // after, so the store goes on without it. A limit that would delete photos is dropped then: no page asks.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                var asking = false
                val stored = change {
                    if (limits != preferences.history.first().limits) {
                        val excess = history.excess(limits)
                        asking = excess > 0
                        if (asking) {
                            mutablePending.value = PendingLimits(limits, excess, close)
                        } else {
                            preferences.setLimits(limits)
                        }
                    }
                }
                if (!stored) mutableResets.update() else if (close && !asking) messages.send(CameraSettingsEvent.CLOSE)
            }
        }
    }

    fun confirmLimits() {
        val pending = mutablePending.value ?: return
        mutablePending.value = null
        viewModelScope.launch {
            val stored = change {
                preferences.setLimits(pending.limits)
                history.trim(pending.limits)
            }
            if (!stored) mutableResets.update() else if (pending.close) messages.send(CameraSettingsEvent.CLOSE)
        }
    }

    fun cancelLimits() {
        if (mutablePending.value == null) return
        mutablePending.value = null
        mutableResets.update()
    }

    fun clearHistory() {
        viewModelScope.launch { change { history.clear() } }
    }

    /** Returns false after reporting a failure. */
    private suspend fun change(block: suspend () -> Unit): Boolean {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Changing the history failed: " + e.javaClass.simpleName)
            messages.send(CameraSettingsEvent.CHANGE_FAILED)
            return false
        }
    }

    private fun MutableStateFlow<Int>.update() {
        value += 1
    }

    private companion object { const val TAG = "CameraSettings" }
}

/**
 * Camera settings: how photos are read, then the photo history's saving mode, its limits and its size. [onOpenEngines]
 * opens screen recognition, whose engines photos share.
 */
@Composable
fun CameraSettingsScreen(onBack: () -> Unit, onOpenEngines: () -> Unit, viewModel: CameraSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val resets by viewModel.resets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var confirmingClear by remember { mutableStateOf(false) }
    // A limit being typed is stored when its field loses focus, or by leaving the page.
    var typedCount by remember { mutableStateOf<Int?>(null) }
    var typedSize by remember { mutableStateOf<Int?>(null) }
    val typing = typedCount != null || typedSize != null
    val leave = {
        val limits = settings?.limits
        if (limits == null || !typing) {
            onBack()
        } else {
            viewModel.close(limits.copy(count = typedCount ?: limits.count, megabytes = typedSize ?: limits.megabytes))
        }
    }
    BackHandler(enabled = typing, onBack = leave)
    val closePage by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            val text = when (event) {
                CameraSettingsEvent.SAVE_FAILED -> R.string.camera_save_history_failed
                CameraSettingsEvent.CHANGE_FAILED -> R.string.history_change_failed
                CameraSettingsEvent.CLOSE -> {
                    closePage()
                    return@collect
                }
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    pending?.let { limits ->
        ConfirmDelete(
            stringResource(R.string.history_trim_title),
            pluralStringResource(R.plurals.history_trim_text, limits.excess, limits.excess),
            onDelete = viewModel::confirmLimits,
            onDismiss = viewModel::cancelLimits,
        )
    }
    if (confirmingClear) {
        ConfirmDelete(
            stringResource(R.string.history_clear_title),
            stringResource(R.string.history_clear_text),
            onDelete = viewModel::clearHistory,
            onDismiss = { confirmingClear = false },
        )
    }
    SettingsScaffold(stringResource(R.string.camera_title), leave) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(
            Modifier.fillMaxSize().formContent(padding, focusManager).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            reading?.let { ReadingCard(it, viewModel::setHighAccuracy, onOpenEngines) }
            SectionCard(stringResource(R.string.camera_history)) {
                SwitchRow(
                    stringResource(R.string.history_auto_save), current.autoSave, viewModel::setAutoSave,
                    hint = stringResource(R.string.history_auto_save_hint),
                )
                LimitField(current.limits.count, resets, stringResource(R.string.history_count_limit), { typedCount = it }) {
                    viewModel.setLimits(current.limits.copy(count = it))
                }
                LimitField(current.limits.megabytes, resets, stringResource(R.string.history_size_limit), { typedSize = it }) {
                    viewModel.setLimits(current.limits.copy(megabytes = it))
                }
                usage?.let { used ->
                    Text(
                        if (used.count == 0) {
                            stringResource(R.string.history_empty)
                        } else {
                            pluralStringResource(R.plurals.history_photos, used.count, used.count) + " · " +
                                Formatter.formatShortFileSize(context, used.bytes)
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(onClick = { confirmingClear = true }, enabled = used.count > 0) {
                        Text(stringResource(R.string.history_clear))
                    }
                }
            }
        }
    }
}

/** High accuracy and the engines, which are changed on the page of screen recognition. */
@Composable
private fun ReadingCard(reading: CameraReading, onHighAccuracy: (Boolean) -> Unit, onOpenEngines: () -> Unit) {
    SectionCard(stringResource(R.string.camera_recognition)) {
        SwitchRow(
            stringResource(R.string.camera_high_accuracy), reading.highAccuracy, onHighAccuracy,
            hint = stringResource(R.string.camera_high_accuracy_hint),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenEngines).padding(vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bubble_ocr_engines))
                Hint(
                    stringResource(
                        when (reading.engines) {
                            OcrEngines.BOTH -> R.string.bubble_ocr_engines_both
                            OcrEngines.CLOUD -> R.string.bubble_ocr_engines_cloud
                            OcrEngines.DEVICE -> R.string.bubble_ocr_engines_device
                        },
                    ),
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A limit typed as a number; it is stored when the field loses focus, an empty field meaning 0 (no limit). [onTyped]
 * gets the typed limit until then, and null once it is stored or dropped. A new [resets] drops the typed limit, also
 * while the field keeps the focus, as after a question asked on leaving the page.
 */
@Composable
internal fun LimitField(value: Int, resets: Int, label: String, onTyped: (Int?) -> Unit, onCommit: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!focused) text = value.toString()
    }
    LaunchedEffect(resets) {
        text = value.toString()
        if (focused) onTyped(null)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter { it in '0'..'9' }.take(LIMIT_DIGITS)
            if (focused) onTyped(text.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.history_no_limit)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = doneClearsFocus(),
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (focused && !it.isFocused) {
                val limit = text.toIntOrNull() ?: 0
                text = limit.toString()
                onCommit(limit)
                onTyped(null)
            }
            focused = it.isFocused
        },
    )
}

private const val LIMIT_DIGITS = 6
