package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordingSettingsRepositoryTest {

    @Test
    fun defaultSettings() = runTest {
        val dataStore = MemoryDataStore()
        val repository = RecordingSettingsRepository(dataStore)

        val settings = repository.settings.first()
        assertThat(settings.recordingEnabled).isFalse()
        assertThat(settings.defaultEditor).isTrue()
        assertThat(settings.limits.historyMillis).isEqualTo(300_000L)
        assertThat(settings.limits.waitingMillis).isEqualTo(300_000L)
        assertThat(settings.limits.editorMillis).isEqualTo(120_000L)
        assertThat(settings.minSilenceMillis).isEqualTo(1500L)
        assertThat(settings.silenceMarginMillis).isEqualTo(150L)
    }

    @Test
    fun updateRecordingEnabled() = runTest {
        val dataStore = MemoryDataStore()
        val repository = RecordingSettingsRepository(dataStore)

        repository.setRecordingEnabled(true)
        assertThat(repository.settings.first().recordingEnabled).isTrue()

        repository.setRecordingEnabled(false)
        assertThat(repository.settings.first().recordingEnabled).isFalse()
    }

    @Test
    fun updateDefaultEditor() = runTest {
        val dataStore = MemoryDataStore()
        val repository = RecordingSettingsRepository(dataStore)

        repository.setDefaultEditor(false)
        assertThat(repository.settings.first().defaultEditor).isFalse()

        repository.setDefaultEditor(true)
        assertThat(repository.settings.first().defaultEditor).isTrue()
    }

    @Test
    fun updateLimits() = runTest {
        val dataStore = MemoryDataStore()
        val repository = RecordingSettingsRepository(dataStore)

        val newLimits = RecordingLimits(historyMillis = 60_000L, waitingMillis = 120_000L, editorMillis = 180_000L)
        repository.setLimits(newLimits)

        val settings = repository.settings.first()
        assertThat(settings.limits.historyMillis).isEqualTo(60_000L)
        assertThat(settings.limits.waitingMillis).isEqualTo(120_000L)
        assertThat(settings.limits.editorMillis).isEqualTo(180_000L)
    }

    @Test
    fun updateSilenceParameters() = runTest {
        val dataStore = MemoryDataStore()
        val repository = RecordingSettingsRepository(dataStore)

        repository.setMinSilenceMillis(2000L)
        repository.setSilenceMarginMillis(200L)

        val settings = repository.settings.first()
        assertThat(settings.minSilenceMillis).isEqualTo(2000L)
        assertThat(settings.silenceMarginMillis).isEqualTo(200L)
    }
}
