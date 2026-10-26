package com.vpr.screenlate.camera

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.photos.CameraPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoHistoryViewModelTest {
    private val store = ViewModelStore()
    private val documents = CurrentPhoto()
    private val history = FakeHistory(documents.current)
    private lateinit var preferences: CameraPreferences
    private lateinit var model: PhotoHistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        preferences = cameraPreferences()
        history.entries.value = listOf(entry(3), entry(2))
        model = PhotoHistoryViewModel(history, documents, preferences, LanguageProfiles(MemoryStore()))
        store.put("history", model)
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun aPhotoOpensAtOnceWhenTheCurrentOneIsInTheHistory() = runTest {
        documents.current.value = photo(1).copy(saved = true)
        advanceUntilIdle()
        model.tap(2)
        advanceUntilIdle()
        assertThat(model.state.value.asking).isNull()
        assertThat(history.opened).containsExactly(2L)
        assertThat(model.events.first()).isEqualTo(PhotoHistoryEvent.OPENED)
    }

    @Test
    fun anUnsavedCurrentPhotoAsksFirstAndCancelKeepsIt() = runTest {
        documents.current.value = photo(1)
        advanceUntilIdle()
        model.tap(2)
        advanceUntilIdle()
        assertThat(model.state.value.asking).isEqualTo(2)
        model.answer(null)
        advanceUntilIdle()
        assertThat(model.state.value.asking).isNull()
        assertThat(history.opened).isEmpty()
        assertThat(documents.current.value?.id).isEqualTo(1)
    }

    @Test
    fun saveToHistorySavesTheCurrentPhotoBeforeOpening() = runTest {
        documents.current.value = photo(1)
        advanceUntilIdle()
        model.tap(2)
        advanceUntilIdle()
        model.answer(true)
        advanceUntilIdle()
        assertThat(history.saved).containsExactly(1L)
        assertThat(history.opened).containsExactly(2L)
    }

    @Test
    fun deleteOpensWithoutSaving() = runTest {
        documents.current.value = photo(1)
        advanceUntilIdle()
        model.tap(2)
        advanceUntilIdle()
        model.answer(false)
        advanceUntilIdle()
        assertThat(history.saved).isEmpty()
        assertThat(history.opened).containsExactly(2L)
    }

    @Test
    fun aFailedSaveOpensNothing() = runTest {
        documents.current.value = photo(1)
        history.saveFails = true
        advanceUntilIdle()
        model.tap(2)
        advanceUntilIdle()
        model.answer(true)
        advanceUntilIdle()
        assertThat(history.opened).isEmpty()
        assertThat(model.events.first()).isEqualTo(PhotoHistoryEvent.SAVE_FAILED)
    }

    @Test
    fun automaticSavingNeverAsks() = runTest {
        preferences.setAutoSave(true)
        documents.current.value = photo(1)
        advanceUntilIdle()
        model.tap(2)
        advanceUntilIdle()
        assertThat(model.state.value.asking).isNull()
        assertThat(history.opened).containsExactly(2L)
    }

    @Test
    fun whileSelectingATapTogglesAndDeleteRemovesTheSelection() = runTest {
        advanceUntilIdle()
        model.toggle(3)
        model.toggle(2)
        model.tap(2)
        assertThat(model.state.value.selected).containsExactly(3L)
        model.deleteSelected()
        advanceUntilIdle()
        assertThat(history.removed).containsExactly(setOf(3L))
        assertThat(model.state.value.selected).isEmpty()
        assertThat(model.state.value.entries?.map { it.id }).containsExactly(2L)
        assertThat(history.opened).isEmpty()
    }

    @Test
    fun photosThatLeaveTheHistoryLeaveTheSelection() = runTest {
        advanceUntilIdle()
        model.toggle(3)
        model.toggle(2)
        history.entries.value = listOf(entry(2))
        advanceUntilIdle()
        assertThat(model.state.value.selected).containsExactly(2L)
    }
}
