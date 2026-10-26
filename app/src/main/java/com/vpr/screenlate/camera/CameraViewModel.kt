package com.vpr.screenlate.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoExports
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoViewport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

enum class CameraEvent { CAPTURE, DELETE, DELETED, LOAD, EXPORT_FAILED, EXPORTED }

/** Owns a camera page's operations; the document itself belongs to PhotoDocuments, not the page. */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documents: PhotoDocuments,
    sources: LookupSources,
    private val savedState: SavedStateHandle,
    val session: CameraSession,
    val recognition: CameraRecognition,
    private val exports: PhotoExports,
) : ViewModel() {
    private val lookup = CameraLookup(sources)
    private val mutableState = MutableStateFlow(CameraState(takingNew = savedState[NEW_PHOTO] ?: false))
    val state = mutableState.asStateFlow()
    private val messages = Channel<CameraEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var viewport: Pair<Long, PhotoViewport>? = null
    private var obscured = false

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
                    mutableState.value = state.value.copy(loaded = true, photo = photo, loadFailed = false)
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

    fun deletePhoto() {
        if (state.value.busy || state.value.photo == null) return
        mutableState.value = state.value.copy(busy = true)
        publish()
        viewModelScope.launch {
            try {
                documents.clearCurrent()
                viewport = null
                savedState[NEW_PHOTO] = false
                mutableState.value = state.value.copy(photo = null, takingNew = false)
                messages.send(CameraEvent.DELETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Deleting photo failed: " + e.javaClass.simpleName)
                messages.send(CameraEvent.DELETE)
            } finally {
                mutableState.value = state.value.copy(busy = false)
                publish()
            }
        }
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
    }
}
