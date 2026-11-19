package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean

class SentenceRecorderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun startStopLifecycle() = runTest {
        val root = temporary.newFolder()
        val dataStore = MemoryDataStore()
        val settings = RecordingSettingsRepository(dataStore)
        val recorder = SentenceRecorder(root, settings)

        assertThat(recorder.isRecording).isFalse()
        assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Idle)

        val stoppedNotified = AtomicBoolean(false)
        recorder.setOnStoppedListener { stoppedNotified.set(true) }

        val source = FakeAudioSource(sampleRate = 48000, blockFrames = 4800)
        val started = recorder.start(source)
        assertThat(started).isTrue()
        assertThat(recorder.isRecording).isTrue()
        assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Recording::class.java)

        source.emitFrames(48000)
        val scanId = recorder.acquireScan()
        assertThat(scanId).isNotNull()

        recorder.stop()
        assertThat(recorder.isRecording).isFalse()
        assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Stopped::class.java)
        assertThat(stoppedNotified.get()).isTrue()

        // After stop, new scan cannot be acquired
        assertThat(recorder.acquireScan()).isNull()

        recorder.close()
        assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Idle)
    }

    @Test
    fun failedStart() = runTest {
        val root = temporary.newFolder()
        val dataStore = MemoryDataStore()
        val settings = RecordingSettingsRepository(dataStore)
        val recorder = SentenceRecorder(root, settings)

        val failingSource = FakeAudioSource(sampleRate = 48000, failOnStart = true)
        val started = recorder.start(failingSource)
        assertThat(started).isFalse()
        assertThat(recorder.isRecording).isFalse()
        assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Failed::class.java)

        recorder.close()
    }
}
