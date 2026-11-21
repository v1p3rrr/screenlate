package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.MemoryDataStore
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerTest {
    @get:Rule val folder = TemporaryFolder()
    private val speechRequest = Pronunciation.Speech("fixture", Language.ENGLISH)

    @Test
    fun veryShortLiveSpeechCanCompleteBeforeSubmissionReturns() = runTest {
        val playback = PlaybackCoordinator()
        val speech = FakeSpeech().apply { preparation = CompletableDeferred(); completeImmediately = true }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = AudioPlayer(folder.newFolder(), AudioSettingsRepository(MemoryDataStore()), playback,
            speech, { FakeOutput() }, dispatcher, dispatcher)
        val request = async { player.play(speechRequest) }
        runCurrent()
        speech.preparation!!.complete(true)
        assertThat(request.await()).isTrue()
        assertThat(speech.liveStarts).isEqualTo(1)
        assertThat(playback.acquire { true }?.isCurrent).isTrue()
    }

    @Test
    fun interruptedPrivateSpeechDeletesItsFileAndNeverFallsBackToLiveSpeech() = runTest {
        val playback = PlaybackCoordinator()
        val capture = checkNotNull(playback.beginCapture())
        val speech = FakeSpeech()
        val output = FakeOutput()
        val directory = folder.newFolder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = AudioPlayer(directory, AudioSettingsRepository(MemoryDataStore()), playback, speech, { output }, dispatcher, dispatcher)
        assertThat(player.play(speechRequest)).isTrue()
        assertThat(directory.listFiles()!!.size).isEqualTo(1)
        output.ready()
        assertThat(output.starts).isEqualTo(1)
        assertThat(player.stop()).isTrue()
        assertThat(output.closed).isTrue()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        assertThat(speech.liveStarts).isEqualTo(0)
        capture.close()
    }

    @Test
    fun replacementWhileSpeechIsPreparingCannotStartALateUtterance() = runTest {
        val playback = PlaybackCoordinator()
        val speech = FakeSpeech().apply { preparation = CompletableDeferred() }
        val directory = folder.newFolder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = AudioPlayer(directory, AudioSettingsRepository(MemoryDataStore()), playback, speech, { FakeOutput() }, dispatcher, dispatcher)
        val old = async { player.play(speechRequest) }
        runCurrent()
        val preview = checkNotNull(playback.acquire { true })
        speech.preparation!!.complete(true)
        assertThat(old.await()).isFalse()
        assertThat(speech.liveStarts).isEqualTo(0)
        assertThat(preview.isCurrent).isTrue()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        preview.close()
    }

    @Test
    fun cancelledSynthesisCleansItsFileAndDoesNotStopTheNextOutput() = runTest {
        val playback = PlaybackCoordinator()
        val capture = checkNotNull(playback.beginCapture())
        val speech = FakeSpeech().apply { synthesis = CompletableDeferred() }
        val directory = folder.newFolder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = AudioPlayer(directory, AudioSettingsRepository(MemoryDataStore()), playback, speech, { FakeOutput() }, dispatcher, dispatcher)
        val request = async { player.play(speechRequest) }
        runCurrent()
        assertThat(directory.listFiles()!!.size).isEqualTo(1)
        request.cancelAndJoin()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        val next = checkNotNull(playback.acquire { true })
        speech.synthesis!!.complete(true)
        runCurrent()
        assertThat(next.isCurrent).isTrue()
        assertThat(speech.liveStarts).isEqualTo(0)
        next.close()
        capture.close()
    }

    @Test
    fun latePreparedCallbackCannotPlayOrDeleteADownloadedClip() = runTest {
        val playback = PlaybackCoordinator()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val output = FakeOutput()
        val clip = folder.newFile("downloaded.wav").apply { writeBytes(byteArrayOf(1, 2)) }
        val player = AudioPlayer(folder.newFolder(), AudioSettingsRepository(MemoryDataStore()), playback,
            FakeSpeech(), { output }, dispatcher, dispatcher)
        assertThat(player.play(Pronunciation.Clip(AudioClip(clip, "fixture", "wav")))).isTrue()
        val next = checkNotNull(playback.acquire { true })
        output.ready()
        output.finished()
        assertThat(output.starts).isEqualTo(0)
        assertThat(output.closed).isTrue()
        assertThat(clip.exists()).isTrue()
        assertThat(next.isCurrent).isTrue()
        next.close()
    }

    @Test
    fun failedStopRetainsItsFileAndDeclinesCaptureThenRetryCleansIt() = runTest {
        val playback = PlaybackCoordinator()
        val capture = checkNotNull(playback.beginCapture())
        val speech = FakeSpeech().apply { synthesis = CompletableDeferred(); canStop = false }
        val directory = folder.newFolder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = AudioPlayer(directory, AudioSettingsRepository(MemoryDataStore()), playback, speech, { FakeOutput() }, dispatcher, dispatcher)
        val request = async { player.play(speechRequest) }
        runCurrent()
        assertThat(playback.beginCapture()).isNull()
        assertThat(request.await()).isFalse()
        assertThat(directory.listFiles()!!.size).isEqualTo(1)
        assertThat(playback.acquire { true }).isNull()
        speech.canStop = true
        assertThat(player.stop()).isTrue()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        assertThat(playback.acquire { true }?.isCurrent).isTrue()
        capture.close()
    }

    @Test
    fun completionAndAsyncOutputFailureReleasePrivateFiles() = runTest {
        val playback = PlaybackCoordinator()
        val capture = checkNotNull(playback.beginCapture())
        val directory = folder.newFolder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val outputs = mutableListOf<FakeOutput>()
        val player = AudioPlayer(directory, AudioSettingsRepository(MemoryDataStore()), playback, FakeSpeech(),
            { FakeOutput().also { outputs += it } }, dispatcher, dispatcher)
        assertThat(player.play(speechRequest)).isTrue()
        outputs[0].ready()
        outputs[0].finished()
        runCurrent()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        assertThat(player.play(speechRequest)).isTrue()
        outputs[1].finished() // The adapter reports a prepare/play error through the same terminal callback.
        runCurrent()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        assertThat(outputs.all { it.closed }).isTrue()
        capture.close()
    }

    @Test
    fun synthesisTimeoutDeletesTheFileAndEmptySynthesisDoesNotStartOutput() = runTest {
        val playback = PlaybackCoordinator()
        val capture = checkNotNull(playback.beginCapture())
        val speech = FakeSpeech().apply { synthesis = CompletableDeferred() }
        val directory = folder.newFolder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val output = FakeOutput()
        val player = AudioPlayer(directory, AudioSettingsRepository(MemoryDataStore()), playback, speech, { output }, dispatcher, dispatcher)
        val request = async { player.play(speechRequest) }
        advanceTimeBy(5001)
        assertThat(request.await()).isFalse()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        speech.synthesis = null
        speech.empty = true
        assertThat(player.play(speechRequest)).isFalse()
        assertThat(output.starts).isEqualTo(0)
        assertThat(speech.liveStarts).isEqualTo(0)
        assertThat(directory.listFiles()!!.toList()).isEmpty()
        capture.close()
    }

    private class FakeOutput : FileAudioOutput {
        var ready: () -> Unit = {}
        var finished: () -> Unit = {}
        var starts = 0
        var closed = false
        override fun prepare(file: File, volume: Float, ready: () -> Unit, finished: () -> Unit) {
            this.ready = ready
            this.finished = finished
        }
        override fun start() { starts++; check(!closed) }
        override fun close() { closed = true }
    }

    private class FakeSpeech : SpeechOutput {
        var preparation: CompletableDeferred<Boolean>? = null
        var synthesis: CompletableDeferred<Boolean>? = null
        var empty = false
        var liveStarts = 0
        var canStop = true
        var completeImmediately = false
        override suspend fun prepare(language: Language, canStart: () -> Boolean) =
            (preparation?.await() ?: true) && canStart()
        override suspend fun synthesize(text: String, file: File, canStart: () -> Boolean): Boolean {
            if (!empty) file.writeBytes(byteArrayOf(1, 2, 3, 4))
            return (synthesis?.await() ?: true) && canStart()
        }
        override suspend fun speak(text: String, volume: Float, canStart: () -> Boolean, finished: () -> Unit): Boolean {
            if (!canStart()) return false
            liveStarts++
            if (completeImmediately) finished()
            return true
        }
        override suspend fun stop() = canStop
    }
}
