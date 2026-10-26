package com.vpr.screenlate.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.photos.LookupSource
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoRecognition
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    @get:Rule val files = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var documents: Documents
    private lateinit var sources: LookupSources
    private lateinit var model: CameraViewModel
    private var recognitionCalls = 0
    private val recognition = object : CameraRecognition {
        override val state = MutableStateFlow<CameraRecognitionState?>(null)
        override val blocked = MutableStateFlow<CameraReadinessBlock?>(null)
        override fun select(language: com.vpr.screenlate.core.common.Language) {}
        override fun recognize(document: PhotoDocument) { recognitionCalls++ }
        override fun retry(document: PhotoDocument) {}
        override fun dismissBlock() {}
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        documents = Documents()
        sources = LookupSources()
        model = CameraViewModel(documents, sources, SavedStateHandle(), CameraSession(), recognition)
        store.put("camera", model)
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun failedLoadCannotBecomeAnEmptyStoreAndCanBeRetried() = runTest {
        var fail = true
        val storeWithFailure = object : PhotoDocuments by documents {
            override val current = flow {
                if (fail) throw IOException("test")
                emitAll(documents.current)
            }
        }
        model = CameraViewModel(storeWithFailure, sources, SavedStateHandle(), CameraSession(), recognition)
        store.put("camera", model)
        model.onForeground()
        advanceUntilIdle()
        assertThat(model.state.value.loadFailed).isTrue()
        assertThat(model.state.value.live).isFalse()
        model.capture { error("Must not capture over an unreadable store") }
        assertThat(documents.allocated).isEqualTo(0)
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
        fail = false
        documents.current.value = photo()
        model.retryLoading()
        advanceUntilIdle()
        assertThat(model.state.value.loaded).isTrue()
        assertThat(model.state.value.loadFailed).isFalse()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
    }

    @Test
    fun dialogsTemporarilyDisablePhotoTargetingWithoutScreenFallback() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.onForeground()
        model.showPhoto(1, viewport())
        assertThat(sources.source.value).isInstanceOf(LookupSource.Photo::class.java)
        model.setObscured(true)
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
        model.setObscured(false)
        assertThat(sources.source.value).isInstanceOf(LookupSource.Photo::class.java)
        assertThat(recognitionCalls).isEqualTo(0)
    }

    @Test
    fun liveCameraClosesPhotoTargetingAndBackRestoresIt() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.onForeground()
        model.showPhoto(1, viewport())
        assertThat(sources.source.value).isInstanceOf(LookupSource.Photo::class.java)
        model.newPhoto()
        assertThat(model.state.value.live).isTrue()
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
        model.backToPhoto()
        assertThat(sources.source.value).isInstanceOf(LookupSource.Photo::class.java)
        model.onBackground()
        assertThat(sources.source.value).isInstanceOf(LookupSource.Screen::class.java)
    }

    @Test
    fun lateViewportCannotAttachThePreviousPhoto() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.onForeground()
        documents.current.value = photo(2)
        advanceUntilIdle()
        model.showPhoto(1, viewport())
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
        model.showPhoto(2, viewport())
        assertThat((sources.source.value as LookupSource.Photo).documentId).isEqualTo(2)
    }

    @Test
    fun failedCapturePreservesThePhotoAndCleansStaging() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.newPhoto()
        model.capture { throw IOException("test") }
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(model.state.value.live).isTrue()
        assertThat(model.state.value.busy).isFalse()
        assertThat(documents.replaced).isEqualTo(0)
        assertThat(documents.discarded).hasSize(1)
        assertThat(recognitionCalls).isEqualTo(0)
        assertThat(model.events.first()).isEqualTo(CameraError.CAPTURE)
    }

    @Test
    fun repeatedShutterTapsCannotCaptureConcurrently() = runTest {
        advanceUntilIdle()
        val written = CompletableDeferred<Unit>()
        model.capture { written.await() }
        model.capture { error("second capture") }
        runCurrent()
        assertThat(documents.allocated).isEqualTo(1)
        assertThat(model.state.value.busy).isTrue()
        written.complete(Unit)
        advanceUntilIdle()
        assertThat(documents.replaced).isEqualTo(1)
        assertThat(model.state.value.live).isFalse()
        assertThat(model.state.value.photo?.id).isEqualTo(2)
        assertThat(recognitionCalls).isEqualTo(1)
    }

    @Test
    fun cancelledCaptureCleansStagingWithoutReplacement() = runTest {
        advanceUntilIdle()
        model.capture { throw CancellationException("cancelled") }
        advanceUntilIdle()
        assertThat(documents.replaced).isEqualTo(0)
        assertThat(documents.discarded).hasSize(1)
        assertThat(model.state.value.busy).isFalse()
    }

    @Test
    fun failedDeletionPreservesThePhoto() = runTest {
        documents.current.value = photo()
        documents.deleteFails = true
        advanceUntilIdle()
        model.deletePhoto()
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(model.events.first()).isEqualTo(CameraError.DELETE)
    }

    @Test
    fun deletingThePhotoOpensAnInactivePreview() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.onForeground()
        model.showPhoto(1, viewport())
        model.deletePhoto()
        advanceUntilIdle()
        assertThat(model.state.value.live).isTrue()
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
    }

    private inner class Documents : PhotoDocuments {
        override val current = MutableStateFlow<PhotoDocument?>(null)
        var allocated = 0
        var replaced = 0
        var deleteFails = false
        val discarded = mutableListOf<File>()
        override suspend fun newCaptureFile(): File = files.newFile().also { allocated++ }
        override suspend fun replaceCurrent(capture: File): PhotoDocument {
            replaced++
            return photo(2).also { current.value = it }
        }
        override suspend fun discard(capture: File) {
            discarded += capture
            capture.delete()
        }
        override suspend fun clearCurrent() {
            if (deleteFails) throw IOException("test")
            current.value = null
        }
        override suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument? =
            error("Not used by capture")
        override suspend fun saveAttempt(documentId: Long, attempt: com.vpr.screenlate.core.photos.PhotoAttempt, replace: Boolean): Boolean =
            error("Not used by capture")
    }
}
