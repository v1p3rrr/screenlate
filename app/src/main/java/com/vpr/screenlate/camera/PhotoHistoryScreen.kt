package com.vpr.screenlate.camera

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.HistoryEntry
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoHistory
import com.vpr.screenlate.ui.components.BackButton
import com.vpr.screenlate.ui.components.ConfirmDelete
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.LocalEInk
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.DateFormat
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property entries null until the history is read.
 * @property asking a photo to open once the user decides about the current one, which is not in the history.
 */
data class PhotoHistoryState(
    val entries: List<HistoryEntry>? = null,
    val selected: Set<Long> = emptySet(),
    val asking: Long? = null,
    val busy: Boolean = false,
)

enum class PhotoHistoryEvent { OPENED, OPEN_FAILED, SAVE_FAILED, DELETE_FAILED }

/** The history page: opening a saved photo as the current one, and selecting photos to delete. */
@HiltViewModel
class PhotoHistoryViewModel @Inject constructor(
    private val history: PhotoHistory,
    documents: PhotoDocuments,
    private val preferences: CameraPreferences,
    profiles: LanguageProfiles,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhotoHistoryState())
    val state = mutableState.asStateFlow()
    private val messages = Channel<PhotoHistoryEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val current: StateFlow<PhotoDocument?> = documents.current
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val languages: StateFlow<LanguageProfilesState?> = profiles.state.map<LanguageProfilesState, LanguageProfilesState?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            history.entries
                .catch { e ->
                    Log.w(TAG, "Reading the history failed: " + e.javaClass.simpleName)
                    emit(emptyList())
                }
                .collect { entries ->
                    val ids = entries.mapTo(HashSet()) { it.id }
                    mutableState.value = state.value.copy(entries = entries, selected = state.value.selected.filterTo(HashSet()) { it in ids })
                }
        }
    }

    /** A tap: toggles the photo while selecting, otherwise opens it, asking first about an unsaved current photo. */
    fun tap(id: Long) {
        val value = state.value
        if (value.busy) return
        if (value.selected.isNotEmpty()) return toggle(id)
        viewModelScope.launch {
            val photo = current.value
            if (photo?.id != id && closingAsks(photo, preferences.history.first().autoSave)) {
                mutableState.value = state.value.copy(asking = id)
            } else {
                open(id, saveCurrent = false)
            }
        }
    }

    fun toggle(id: Long) {
        val selected = state.value.selected
        mutableState.value = state.value.copy(selected = if (id in selected) selected - id else selected + id)
    }

    fun clearSelection() {
        mutableState.value = state.value.copy(selected = emptySet())
    }

    /** The answer about the current photo: save it first, delete it ([save] false), or cancel ([save] null). */
    fun answer(save: Boolean?) {
        val id = state.value.asking ?: return
        mutableState.value = state.value.copy(asking = null)
        if (save != null) viewModelScope.launch { open(id, saveCurrent = save) }
    }

    fun deleteSelected() {
        val ids = state.value.selected
        if (ids.isEmpty() || state.value.busy) return
        mutableState.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                history.remove(ids)
                mutableState.value = state.value.copy(selected = emptySet())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Deleting from the history failed: " + e.javaClass.simpleName)
                messages.send(PhotoHistoryEvent.DELETE_FAILED)
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }

    private suspend fun open(id: Long, saveCurrent: Boolean) {
        mutableState.value = state.value.copy(busy = true)
        try {
            val photo = current.value
            if (saveCurrent && photo != null && !history.save(photo.id, preferences.history.first().limits)) {
                messages.send(PhotoHistoryEvent.SAVE_FAILED)
                return
            }
            messages.send(if (history.open(id) != null) PhotoHistoryEvent.OPENED else PhotoHistoryEvent.OPEN_FAILED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Opening a history photo failed: " + e.javaClass.simpleName)
            messages.send(PhotoHistoryEvent.OPEN_FAILED)
        } finally {
            mutableState.value = state.value.copy(busy = false)
        }
    }

    private companion object { const val TAG = "PhotoHistory" }
}

/** The saved photos, newest first; opening one returns to the Camera page with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoHistoryScreen(onBack: () -> Unit, viewModel: PhotoHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = state.selected.isNotEmpty()
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                PhotoHistoryEvent.OPENED -> onBack()
                PhotoHistoryEvent.OPEN_FAILED -> snackbar.showSnackbar(resources.getString(R.string.camera_photo_unreadable))
                PhotoHistoryEvent.SAVE_FAILED -> snackbar.showSnackbar(resources.getString(R.string.camera_save_history_failed))
                PhotoHistoryEvent.DELETE_FAILED -> snackbar.showSnackbar(resources.getString(R.string.history_change_failed))
            }
        }
    }
    BackHandler(selecting) { viewModel.clearSelection() }
    if (confirmingDelete) {
        val count = state.selected.size
        ConfirmDelete(
            stringResource(R.string.history_delete_title),
            pluralStringResource(R.plurals.history_delete_text, count, count),
            onDelete = viewModel::deleteSelected,
            onDismiss = { confirmingDelete = false },
        )
    }
    if (state.asking != null) {
        UnsavedPhotoDialog(onSave = { viewModel.answer(true) }, onDelete = { viewModel.answer(false) }, onDismiss = { viewModel.answer(null) })
    }
    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.history_selected, state.selected.size, state.selected.size)) },
                    navigationIcon = {
                        TooltipIconButton(R.drawable.ic_close, stringResource(R.string.history_clear_selection), onClick = viewModel::clearSelection)
                    },
                    actions = {
                        TooltipIconButton(
                            R.drawable.ic_delete, stringResource(R.string.action_delete),
                            onClick = { confirmingDelete = true }, enabled = !state.busy,
                        )
                    },
                )
            } else {
                TopAppBar(title = { Text(stringResource(R.string.camera_history)) }, navigationIcon = { BackButton(onBack) })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val entries = state.entries
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                entries == null -> CircularProgressIndicator()
                entries.isEmpty() -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_history), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.history_empty_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryTile(
                            entry,
                            selected = entry.id in state.selected,
                            country = entry.language?.let { language -> languages?.flagCountry(language) },
                            onClick = { viewModel.tap(entry.id) },
                            onLongClick = { viewModel.toggle(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTile(entry: HistoryEntry, selected: Boolean, country: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val date = DateUtils.formatSameDayTime(entry.createdAt, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()
    val description = listOfNotNull(date, entry.language?.displayName()).joinToString(", ")
    val image = rememberThumbnail(entry.thumbnail)
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(R.drawable.ic_image), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                date, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val language = entry.language
            if (language != null && country != null) ChipFlag(language, country, sizeDp = 16)
        }
    }
}

/**
 * Asks about a current photo that is not in the history before it is closed or another photo takes its place:
 * Save to history keeps it there, Delete deletes it. Both run before [onDismiss], which alone means Cancel.
 */
@Composable
internal fun UnsavedPhotoDialog(onSave: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.camera_unsaved), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = {
                onSave()
                onDismiss()
            }) { Text(stringResource(R.string.camera_save_history)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) { Text(stringResource(R.string.action_delete)) }
        },
    )
}

/** The round button right of the shutter: the latest history photo, or the history icon. */
@Composable
internal fun HistoryButton(thumbnail: File?, enabled: Boolean, onClick: () -> Unit) {
    val eInk = LocalEInk.current
    val label = stringResource(R.string.camera_history)
    val image = rememberThumbnail(thumbnail.takeUnless { eInk })
    Box(
        Modifier.size(HISTORY_BUTTON).clip(CircleShape)
            .background(if (eInk) Color.White else Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .border(2.dp, if (eInk) Color.Black else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_history), null, tint = if (eInk) Color.Black else Color.White)
        }
    }
}

/** A history thumbnail decoded off the main thread; null while it loads or when there is none. */
@Composable
private fun rememberThumbnail(file: File?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file?.path) {
        value = withContext(Dispatchers.IO) {
            // The last history list seen before the photo was deleted can still name its thumbnail.
            file?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()
        }
    }
    return image
}

internal val HISTORY_BUTTON = 52.dp
private val TILE_MIN_WIDTH = 104.dp
