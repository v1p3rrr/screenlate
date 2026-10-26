package com.vpr.screenlate.camera

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.HistoryLimits
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
class CameraSettingsViewModelTest {
    private val models = ViewModelStore()
    private val documents = CurrentPhoto()
    private val history = FakeHistory(documents.current)
    private val store = MemoryStore()
    private lateinit var preferences: CameraPreferences
    private lateinit var model: CameraSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        preferences = cameraPreferences(store)
        model = CameraSettingsViewModel(history, documents, preferences)
        models.put("settings", model)
    }

    @After
    fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun turningAutomaticSavingOnAlsoSavesTheCurrentPhoto() = runTest {
        documents.current.value = photo(1)
        model.setAutoSave(true)
        advanceUntilIdle()
        assertThat(model.settings.value?.autoSave).isTrue()
        assertThat(history.saved).containsExactly(1L)
        model.setAutoSave(false)
        model.setAutoSave(true)
        advanceUntilIdle()
        assertThat(history.saved).containsExactly(1L)
    }

    @Test
    fun turningAutomaticSavingOffSavesNothing() = runTest {
        documents.current.value = photo(1)
        model.setAutoSave(false)
        advanceUntilIdle()
        assertThat(history.saved).isEmpty()
    }

    @Test
    fun aFailedWriteOfAutomaticSavingReportsItAndSavesNothing() = runTest {
        documents.current.value = photo(1)
        store.failWrites = true
        model.setAutoSave(true)
        advanceUntilIdle()
        assertThat(model.events.first()).isEqualTo(CameraSettingsEvent.CHANGE_FAILED)
        assertThat(model.settings.value?.autoSave).isFalse()
        assertThat(model.resets.value).isEqualTo(1)
        assertThat(history.saved).isEmpty()
    }

    @Test
    fun aLimitWithinTheHistoryIsStoredAtOnce() = runTest {
        model.setLimits(HistoryLimits(count = 10))
        advanceUntilIdle()
        assertThat(model.pending.value).isNull()
        assertThat(preferences.history.first().limits).isEqualTo(HistoryLimits(count = 10))
        assertThat(history.trimmed).isEmpty()
    }

    @Test
    fun aLimitThatDeletesPhotosAsksAndDeleteTrims() = runTest {
        history.excess = 3
        val limits = HistoryLimits(megabytes = 50)
        model.setLimits(limits)
        advanceUntilIdle()
        assertThat(model.pending.value).isEqualTo(PendingLimits(limits, 3))
        assertThat(preferences.history.first().limits).isEqualTo(HistoryLimits())
        model.confirmLimits()
        model.cancelLimits()
        advanceUntilIdle()
        assertThat(model.pending.value).isNull()
        assertThat(preferences.history.first().limits).isEqualTo(limits)
        assertThat(history.trimmed).containsExactly(limits)
        assertThat(model.resets.value).isEqualTo(0)
    }

    @Test
    fun cancelKeepsTheOldLimitAndResetsTheField() = runTest {
        history.excess = 2
        model.setLimits(HistoryLimits(count = 1))
        advanceUntilIdle()
        model.cancelLimits()
        advanceUntilIdle()
        assertThat(model.pending.value).isNull()
        assertThat(model.resets.value).isEqualTo(1)
        assertThat(preferences.history.first().limits).isEqualTo(HistoryLimits())
        assertThat(history.trimmed).isEmpty()
    }

    @Test
    fun usageCountsThePhotosAndTheirSize() = runTest {
        history.entries.value = listOf(entry(2, bytes = 3_000), entry(1, bytes = 2_000))
        advanceUntilIdle()
        assertThat(model.usage.value).isEqualTo(HistoryUsage(2, 5_000))
        model.clearHistory()
        advanceUntilIdle()
        assertThat(history.cleared).isEqualTo(1)
        assertThat(model.usage.value).isEqualTo(HistoryUsage(0, 0))
    }

    @Test
    fun turningAutomaticSavingOnAfterTheCurrentPhotoWasClearedSavesNothing() = runTest {
        documents.current.value = photo(1).copy(saved = true)
        model.clearHistory()
        advanceUntilIdle()
        assertThat(documents.current.value).isNull()
        model.setAutoSave(true)
        advanceUntilIdle()
        assertThat(history.saved).isEmpty()
    }
}
