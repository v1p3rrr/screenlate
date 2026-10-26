package com.vpr.screenlate.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.HistoryEntry
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoExports
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoHistory
import com.vpr.screenlate.core.photos.PhotoViewport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
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

data class CameraState(
    val loaded: Boolean = false,
    val photo: PhotoDocument? = null,
    val takingNew: Boolean = false,
    val busy: Boolean = false,
    val loadFailed: Boolean = false,
    val exporting: Boolean = false,
) {
    val live: Boolean get() = loaded && (photo == null || takingNew)
}

enum class CameraEvent { CAPTURE, DELETE, DELETED, LOAD, EXPORT_FAILED, EXPORTED, SAVED, SAVE_FAILED }

/**
 * Whether closing [photo], or opening another one in its place, asks first: it would be deleted, and the user saves
 * photos by hand. With automatic saving every new photo is in the history.
 */
internal fun closingAsks(photo: PhotoDocument?, autoSave: Boolean): Boolean = photo != null && !photo.saved && !autoSave

/** Owns a camera page's operations; the document itself belongs to PhotoDocuments, not the page. */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documents: PhotoDocuments,
    sources: LookupSources,
    private val savedState: SavedStateHandle,
    val session: CameraSession,
    val recognition: CameraRecognition,
    private val exports: PhotoExports,
    private val history: PhotoHistory,
    private val preferences: CameraPreferences,
) : ViewModel() {
    private val lookup = CameraLookup(sources)
    private val mutableState = MutableStateFlow(CameraState(takingNew = savedState[NEW_PHOTO] ?: false))
    val state = mutableState.asStateFlow()
    private val messages = Channel<CameraEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var viewport: Pair<Long, PhotoViewport>? = null
    private var obscured = false

    /** The latest photo of the history, shown on the button next to the shutter. */
    val latest: StateFlow<HistoryEntry?> = history.entries
        .map { it.firstOrNull() }
        .catch { e ->
            Log.w(TAG, "Reading the history failed: " + e.javaClass.simpleName)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val autoSave: StateFlow<Boolean> = preferences.history.map { it.autoSave }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setObscured(value: Boolean) {
        if (obscured == value) return
        obscured = value
        publish()
    }

    init { observePhoto() }

    fun retryLoading() {
        if (!state.value.loadFailed) return
        mutableState.value = state.value.copy(loadFailed = false)
        observePhoto()
    }

    private fun observePhoto() {
        viewModelScope.launch {
            try {
                documents.current.collect { photo ->
                    val previous = state.value.photo
                    // Another photo opened from the history shows instead of the live camera; a photo deleted with
                    // the history leaves the live camera.
                    val replaced = previous != null && photo?.id != previous.id
                    if (replaced) {
                        savedState[NEW_PHOTO] = false
                        viewport = null
                    }
                    mutableState.value = state.value.copy(
                        loaded = true, photo = photo, loadFailed = false, takingNew = state.value.takingNew && !replaced,
                    )
                    publish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Loading photo failed: " + e.javaClass.simpleName)
                mutableState.value = state.value.copy(loaded = false, loadFailed = true)
                publish()
                messages.send(CameraEvent.LOAD)
                // Do not treat a failed load as an empty store and replace its contents implicitly.
            }
        }
    }

    fun onForeground() {
        publish()
        lookup.onForeground()
    }

    fun onBackground() = lookup.onBackground()

    fun newPhoto() {
        if (state.value.busy || state.value.photo == null) return
        savedState[NEW_PHOTO] = true
        mutableState.value = state.value.copy(takingNew = true)
        publish()
    }

    fun backToPhoto() {
        if (state.value.busy) return
        savedState[NEW_PHOTO] = false
        mutableState.value = state.value.copy(takingNew = false)
        publish()
    }

    /** Only a successfully decoded, currently displayed image supplies the bubble's viewport. */
    fun showPhoto(documentId: Long, value: PhotoViewport?) {
        if (state.value.photo?.id != documentId) return
        viewport = value?.let { documentId to it }
        publish()
    }

    fun capture(write: suspend (File) -> Unit) {
        if (!state.value.live || state.value.busy) return
        mutableState.value = state.value.copy(busy = true)
        publish()
        viewModelScope.launch {
            var capture: File? = null
            try {
                capture = documents.newCaptureFile()
                write(capture)
                coroutineContext.ensureActive()
                val photo = documents.replaceCurrent(capture)
                viewport = null
                savedState[NEW_PHOTO] = false
                mutableState.value = state.value.copy(photo = photo, takingNew = false)
                recognition.recognize(photo)
                if (preferences.history.first().autoSave) save(photo.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Capture failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.CAPTURE)
            } finally {
                withContext(NonCancellable) {
                    capture?.let { file ->
                        try {
                            documents.discard(file)
                        } catch (e: Exception) {
                            Log.w(TAG, "Discarding capture failed: " + e.javaClass.simpleName)
                        }
                    }
                }
                mutableState.value = state.value.copy(busy = false)
                publish()
            }
        }
    }

    /**
     * Closes the photo and opens the live camera. A photo in the history stays there; any other is deleted, or first
     * saved when [save] is set. The screen asks before closing a photo that would be deleted ([closingAsks]).
     */
    fun closePhoto(save: Boolean = false) {
        val photo = state.value.photo ?: return
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true)
        publish()
        viewModelScope.launch {
            try {
                if (save && !save(photo.id)) return@launch
                documents.clearCurrent()
                viewport = null
                savedState[NEW_PHOTO] = false
                mutableState.value = state.value.copy(photo = null, takingNew = false)
                if (!save && !photo.saved) messages.send(CameraEvent.DELETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Closing photo failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.DELETE)
            } finally {
                mutableState.value = state.value.copy(busy = false)
                publish()
            }
        }
    }

    fun saveToHistory() {
        val photo = state.value.photo ?: return
        if (state.value.busy || photo.saved) return
        viewModelScope.launch { if (save(photo.id)) messages.send(CameraEvent.SAVED) }
    }

    /** Adds the photo to the history within its limits; false, with a message, when that failed. */
    private suspend fun save(documentId: Long): Boolean = try {
        history.save(documentId, preferences.history.first().limits).also { saved ->
            if (!saved) messages.send(CameraEvent.SAVE_FAILED)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Saving photo to the history failed: " + e.javaClass.simpleName)
        messages.send(CameraEvent.SAVE_FAILED)
        false
    }

    fun exportPhoto() {
        val photo = state.value.photo ?: return
        if (state.value.exporting || state.value.busy) return
        mutableState.value = state.value.copy(exporting = true)
        viewModelScope.launch {
            try {
                exports.save(photo.id)
                messages.send(CameraEvent.EXPORTED)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.w(TAG, "Photo export failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.EXPORT_FAILED)
            } finally { mutableState.value = state.value.copy(exporting = false) }
        }
    }

    private fun publish() {
        val current = state.value
        val photo = current.photo
        val view = viewport?.takeIf { it.first == photo?.id }?.second
        lookup.show(
            if (current.loaded && !current.live && !current.busy && !obscured && photo != null && view != null) {
                CameraView.Photo(photo, view)
            } else {
                CameraView.Nothing
            },
        )
    }

    override fun onCleared() {
        lookup.onBackground()
    }

    private companion object {
        const val NEW_PHOTO = "camera_new_photo"
        const val TAG = "Camera"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
