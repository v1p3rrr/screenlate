package com.vpr.screenlate.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.InvalidPhotoException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
    private lateinit var history: FakeHistory
    private lateinit var preferences: CameraPreferences
    private var recognitionCalls = 0
    private val recognition = object : CameraRecognition {
        override val state = MutableStateFlow<CameraRecognitionState?>(null)
        override val blocked = MutableStateFlow<CameraReadinessBlock?>(null)
        override fun select(language: com.vpr.screenlate.core.common.Language) {}
        override fun recognize(document: PhotoDocument) { recognitionCalls++ }
        override fun retry(document: PhotoDocument) {}
        override fun dismissBlock() {}
    }

    private val exportIds = mutableListOf<Long>()
    private var exportWait: CompletableDeferred<Unit>? = null
    private var exportFailure: Exception? = null
    private val exports = com.vpr.screenlate.core.photos.PhotoExports { id ->
        exportIds += id
        exportWait?.await()
        exportFailure?.let { throw it }
    }

    @Test
    fun exportKeepsTheRequestedPhotoAndDoesNotDisableLookupOrRecognizeAgain() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.onForeground()
        model.showPhoto(1, viewport())
        val gate = CompletableDeferred<Unit>()
        exportWait = gate
        model.exportPhoto()
        model.exportPhoto()
        runCurrent()
        assertThat(sources.source.value).isInstanceOf(LookupSource.Photo::class.java)
        assertThat(model.state.value.exporting).isTrue()
        documents.current.value = photo(2)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(exportIds).containsExactly(1L)
        assertThat(model.state.value.exporting).isFalse()
        assertThat(model.events.first()).isEqualTo(CameraEvent.EXPORTED)
        assertThat(recognitionCalls).isEqualTo(0)
    }

    @Test
    fun exportFailurePreservesThePhotoAndAllowsAnExplicitRetry() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        exportFailure = IOException("test")
        model.exportPhoto()
        advanceUntilIdle()
        assertThat(model.state.value.photo!!.id).isEqualTo(1)
        assertThat(model.state.value.exporting).isFalse()
        assertThat(model.events.first()).isEqualTo(CameraEvent.EXPORT_FAILED)
        exportFailure = null
        model.exportPhoto()
        advanceUntilIdle()
        assertThat(model.events.first()).isEqualTo(CameraEvent.EXPORTED)
    }

    private val imported = mutableListOf<Pair<String, File>>()
    private var importFailure: Exception? = null
    private val imports = com.vpr.screenlate.core.photos.PhotoImports { uri, target ->
        imported += uri to target
        importFailure?.let { throw it }
    }
    private lateinit var shared: SharedImages

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        shared = SharedImages()
        documents = Documents()
        sources = LookupSources()
        history = FakeHistory(documents.current)
        preferences = cameraPreferences()
        model = CameraViewModel(documents, sources, SavedStateHandle(), CameraSession(), recognition, exports, history,
            preferences, imports, shared)
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
        model = CameraViewModel(storeWithFailure, sources, SavedStateHandle(), CameraSession(), recognition, exports,
            history, preferences, imports, shared)
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
        assertThat(model.events.first()).isEqualTo(CameraEvent.CAPTURE)
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
    fun anImportOpensAsANewPhotoAndIsRecognized() = runTest {
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        assertThat(imported.single().first).isEqualTo("content://picked")
        assertThat(documents.replaced).isEqualTo(1)
        assertThat(model.state.value.photo?.id).isEqualTo(2)
        assertThat(model.state.value.live).isFalse()
        assertThat(model.state.value.busy).isFalse()
        assertThat(recognitionCalls).isEqualTo(1)
        assertThat(history.saved).isEmpty()
    }

    @Test
    fun anImportOverTheLiveCameraOfAPhotoReplacesIt() = runTest {
        documents.current.value = photo().copy(saved = true)
        advanceUntilIdle()
        model.newPhoto()
        model.importImage("content://picked")
        advanceUntilIdle()
        assertThat(model.state.value.askingImport).isFalse()
        assertThat(model.state.value.photo?.id).isEqualTo(2)
        assertThat(model.state.value.takingNew).isFalse()
    }

    @Test
    fun anUnsavedPhotoIsAskedAboutAfterThePickAndCancelKeepsIt() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        assertThat(model.state.value.askingImport).isTrue()
        assertThat(model.state.value.busy).isFalse()
        assertThat(documents.replaced).isEqualTo(0)
        model.answerImport(null)
        advanceUntilIdle()
        assertThat(model.state.value.askingImport).isFalse()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(documents.discarded).containsExactly(imported.single().second)
        assertThat(recognitionCalls).isEqualTo(0)
    }

    @Test
    fun deletingTheUnsavedPhotoOpensTheImport() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        model.answerImport(false)
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(2)
        assertThat(history.saved).isEmpty()
        assertThat(recognitionCalls).isEqualTo(1)
    }

    @Test
    fun savingTheUnsavedPhotoKeepsItInTheHistoryAndOpensTheImport() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        model.answerImport(true)
        advanceUntilIdle()
        assertThat(history.saved).containsExactly(1L)
        assertThat(model.state.value.photo?.id).isEqualTo(2)
    }

    @Test
    fun aFailedSaveKeepsThePhotoAndDropsTheImport() = runTest {
        documents.current.value = photo()
        history.saveFails = true
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        model.answerImport(true)
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(documents.replaced).isEqualTo(0)
        assertThat(documents.discarded).containsExactly(imported.single().second)
        assertThat(model.events.first()).isEqualTo(CameraEvent.SAVE_FAILED)
    }

    @Test
    fun automaticSavingImportsWithoutAQuestionIntoTheHistory() = runTest {
        preferences.setAutoSave(true)
        documents.current.value = photo().copy(saved = true)
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        assertThat(model.state.value.askingImport).isFalse()
        assertThat(history.saved).containsExactly(2L)
        assertThat(model.state.value.photo?.saved).isTrue()
    }

    @Test
    fun aPhotoThatAutomaticSavingFailedToSaveAsksBeforeAnImport() = runTest {
        preferences.setAutoSave(true)
        history.saveFails = true
        advanceUntilIdle()
        model.capture {}
        advanceUntilIdle()
        assertThat(model.state.value.photo?.saved).isFalse()
        model.importImage("content://picked")
        advanceUntilIdle()
        assertThat(model.state.value.askingImport).isTrue()
        assertThat(model.state.value.photo?.id).isEqualTo(2)
    }

    @Test
    fun anImageThatCannotBeReadKeepsThePhotoWithAMessage() = runTest {
        documents.current.value = photo()
        importFailure = InvalidPhotoException()
        advanceUntilIdle()
        model.importImage("content://picked")
        advanceUntilIdle()
        assertThat(model.state.value.askingImport).isFalse()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(model.state.value.busy).isFalse()
        assertThat(documents.discarded).containsExactly(imported.single().second)
        assertThat(model.events.first()).isEqualTo(CameraEvent.IMPORT)
    }

    @Test
    fun aSharedImageWaitsUntilThePhotoIsLoaded() = runTest {
        val load = CompletableDeferred<Unit>()
        val slowStore = object : PhotoDocuments by documents {
            override val current = flow {
                load.await()
                emitAll(documents.current)
            }
        }
        model = CameraViewModel(slowStore, sources, SavedStateHandle(), CameraSession(), recognition, exports, history,
            preferences, imports, shared)
        store.put("camera", model)
        shared.offer("content://shared")
        advanceUntilIdle()
        assertThat(imported).isEmpty()
        load.complete(Unit)
        advanceUntilIdle()
        assertThat(imported.single().first).isEqualTo("content://shared")
        assertThat(model.state.value.photo?.id).isEqualTo(2)
    }

    @Test
    fun manualSavingLeavesACaptureOutOfTheHistory() = runTest {
        advanceUntilIdle()
        model.capture {}
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(2)
        assertThat(history.saved).isEmpty()
    }

    @Test
    fun automaticSavingAddsACaptureToTheHistoryAfterRecognitionStarts() = runTest {
        preferences.setAutoSave(true)
        advanceUntilIdle()
        model.capture {}
        advanceUntilIdle()
        assertThat(recognitionCalls).isEqualTo(1)
        assertThat(history.saved).containsExactly(2L)
        assertThat(model.state.value.photo?.saved).isTrue()
    }

    @Test
    fun closingAPhotoInTheHistoryKeepsItThereWithoutAMessage() = runTest {
        documents.current.value = photo().copy(saved = true)
        advanceUntilIdle()
        model.closePhoto()
        advanceUntilIdle()
        assertThat(model.state.value.live).isTrue()
        assertThat(history.removed).isEmpty()
        assertThat(withTimeoutOrNull(1_000) { model.events.first() }).isNull()
    }

    @Test
    fun closingWithSaveKeepsThePhotoInTheHistory() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.closePhoto(save = true)
        advanceUntilIdle()
        assertThat(history.saved).containsExactly(1L)
        assertThat(model.state.value.live).isTrue()
        assertThat(withTimeoutOrNull(1_000) { model.events.first() }).isNull()
    }

    @Test
    fun aFailedSaveKeepsThePhotoOpen() = runTest {
        documents.current.value = photo()
        history.saveFails = true
        advanceUntilIdle()
        model.closePhoto(save = true)
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(model.state.value.busy).isFalse()
        assertThat(model.events.first()).isEqualTo(CameraEvent.SAVE_FAILED)
    }

    @Test
    fun savingToTheHistoryMarksThePhotoAndSaysSo() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.saveToHistory()
        advanceUntilIdle()
        assertThat(model.state.value.photo?.saved).isTrue()
        assertThat(model.events.first()).isEqualTo(CameraEvent.SAVED)
        model.saveToHistory()
        advanceUntilIdle()
        assertThat(history.saved).containsExactly(1L)
    }

    @Test
    fun aPhotoOpenedFromTheHistoryReplacesTheLiveCamera() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.newPhoto()
        assertThat(model.state.value.live).isTrue()
        history.open(3)
        advanceUntilIdle()
        assertThat(model.state.value.live).isFalse()
        assertThat(model.state.value.photo?.id).isEqualTo(3)
    }

    @Test
    fun aPhotoDeletedWithTheHistoryLeavesTheLiveCamera() = runTest {
        documents.current.value = photo().copy(saved = true)
        advanceUntilIdle()
        model.newPhoto()
        history.clear()
        advanceUntilIdle()
        assertThat(model.state.value.photo).isNull()
        assertThat(model.state.value.takingNew).isFalse()
        assertThat(model.state.value.live).isTrue()
        assertThat(withTimeoutOrNull(1_000) { model.events.first() }).isNull()
    }

    @Test
    fun theButtonNextToTheShutterShowsTheLatestHistoryPhoto() = runTest {
        backgroundScope.launch { model.latest.collect {} }
        advanceUntilIdle()
        assertThat(model.latest.value).isNull()
        history.entries.value = listOf(entry(5), entry(4))
        advanceUntilIdle()
        assertThat(model.latest.value?.id).isEqualTo(5)
    }

    @Test
    fun onlyAPhotoOutOfTheHistoryAsksBeforeClosing() {
        assertThat(closingAsks(null)).isFalse()
        assertThat(closingAsks(photo())).isTrue()
        assertThat(closingAsks(photo().copy(saved = true))).isFalse()
    }

    @Test
    fun failedDeletionPreservesThePhoto() = runTest {
        documents.current.value = photo()
        documents.deleteFails = true
        advanceUntilIdle()
        model.closePhoto()
        advanceUntilIdle()
        assertThat(model.state.value.photo?.id).isEqualTo(1)
        assertThat(model.events.first()).isEqualTo(CameraEvent.DELETE)
    }

    @Test
    fun deletingThePhotoOpensAnInactivePreview() = runTest {
        documents.current.value = photo()
        advanceUntilIdle()
        model.onForeground()
        model.showPhoto(1, viewport())
        model.closePhoto()
        advanceUntilIdle()
        assertThat(model.state.value.live).isTrue()
        assertThat(model.events.first()).isEqualTo(CameraEvent.DELETED)
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
    }

    private inner class Documents : PhotoDocuments {
        override val current = MutableStateFlow<PhotoDocument?>(null)
        var allocated = 0
        var replaced = 0
        var deleteFails = false
        val discarded = mutableListOf<File>()
        override suspend fun pinImage(documentId: Long): com.vpr.screenlate.core.photos.PhotoImage? = null
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
