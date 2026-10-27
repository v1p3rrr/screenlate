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
import com.vpr.screenlate.core.photos.PhotoImports
import com.vpr.screenlate.core.photos.PhotoTooLargeException
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property askingImport an imported image waits until the user decides about the current photo, which is not in the
 * history ([closingAsks]).
 */
data class CameraState(
    val loaded: Boolean = false,
    val photo: PhotoDocument? = null,
    val takingNew: Boolean = false,
    val busy: Boolean = false,
    val loadFailed: Boolean = false,
    val exporting: Boolean = false,
    val askingImport: Boolean = false,
) {
    val live: Boolean get() = loaded && (photo == null || takingNew)
}

enum class CameraEvent { CAPTURE, IMPORT, IMPORT_TOO_LARGE, DELETE, DELETED, LOAD, EXPORT_FAILED, EXPORTED, SAVED, SAVE_FAILED }

/**
 * Whether closing [photo], or opening another one in its place, asks first: it is not in the history and would be
 * deleted. With automatic saving that happens only when saving it failed.
 */
internal fun closingAsks(photo: PhotoDocument?): Boolean = photo != null && !photo.saved

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
    private val imports: PhotoImports,
    shared: SharedImages,
) : ViewModel() {
    private val lookup = CameraLookup(sources)
    private val mutableState = MutableStateFlow(CameraState(takingNew = savedState[NEW_PHOTO] ?: false))
    val state = mutableState.asStateFlow()
    private val messages = Channel<CameraEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var viewport: Pair<Long, PhotoViewport>? = null
    private var obscured = false

    /** The copy of an imported image while [CameraState.askingImport]; if it is lost, the store deletes it at the next start. */
    private var pendingImport: File? = null
    private val picked = Channel<String>(Channel.CONFLATED)

    /** The latest photo of the history, shown on the button next to the shutter. */
    val latest: StateFlow<HistoryEntry?> = history.entries
        .map { it.firstOrNull() }
        .catch { e ->
            Log.w(TAG, "Reading the history failed: " + e.javaClass.simpleName)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setObscured(value: Boolean) {
        if (obscured == value) return
        obscured = value
        publish()
    }

    init {
        observePhoto()
        viewModelScope.launch {
            // A picker result can arrive before the photo is loaded, in a page recreated after the process died.
            merge(picked.receiveAsFlow(), shared.requests).collect { uri ->
                state.first { it.loaded && !it.busy && !it.askingImport }
                startImport(uri)
            }
        }
    }

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
                adopt(capture)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Capture failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.CAPTURE)
            } finally {
                discard(capture)
                mutableState.value = state.value.copy(busy = false)
                publish()
            }
        }
    }

    /**
     * Copies the image at [uri], a photo picker result, and opens it as a new photo, as a capture would, once the page
     * is free. An unsaved current photo that would be deleted is asked about first ([CameraState.askingImport]).
     * Images shared to the app ([SharedImages]) take the same way.
     */
    fun importImage(uri: String) {
        picked.trySend(uri)
    }

    private fun startImport(uri: String) {
        mutableState.value = state.value.copy(busy = true)
        publish()
        viewModelScope.launch {
            var copy: File? = null
            try {
                copy = documents.newCaptureFile()
                imports.copy(uri, copy)
                coroutineContext.ensureActive()
                if (closingAsks(state.value.photo)) {
                    pendingImport = copy
                    copy = null
                    mutableState.value = state.value.copy(askingImport = true)
                } else {
                    adopt(copy)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PhotoTooLargeException) {
                messages.send(CameraEvent.IMPORT_TOO_LARGE)
            } catch (e: Exception) {
                Log.w(TAG, "Import failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.IMPORT)
            } finally {
                discard(copy)
                mutableState.value = state.value.copy(busy = false)
                publish()
            }
        }
    }

    /** The answer about the current photo before an import: save it, delete it ([save] false), or cancel ([save] null). */
    fun answerImport(save: Boolean?) {
        val copy = pendingImport ?: return
        pendingImport = null
        mutableState.value = state.value.copy(askingImport = false, busy = true)
        publish()
        viewModelScope.launch {
            try {
                val photo = state.value.photo
                if (save == null || save && photo != null && !save(photo.id)) return@launch
                adopt(copy)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.IMPORT)
            } finally {
                discard(copy)
                mutableState.value = state.value.copy(busy = false)
                publish()
            }
        }
    }

    /** Makes [file] the current photo, recognizes it and, with automatic saving, adds it to the history. */
    private suspend fun adopt(file: File) {
        val photo = documents.replaceCurrent(file)
        viewport = null
        savedState[NEW_PHOTO] = false
        mutableState.value = state.value.copy(photo = photo, takingNew = false)
        recognition.recognize(photo)
        if (preferences.history.first().autoSave) save(photo.id)
    }

    /** Deletes a capture or an imported copy left by a failure or a cancel; one that became the photo is moved already. */
    private suspend fun discard(file: File?) {
        if (file == null) return
        withContext(NonCancellable) {
            try {
                documents.discard(file)
            } catch (e: Exception) {
                Log.w(TAG, "Discarding capture failed: " + e.javaClass.simpleName)
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
