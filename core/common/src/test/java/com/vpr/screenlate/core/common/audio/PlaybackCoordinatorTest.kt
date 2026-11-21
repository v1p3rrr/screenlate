package com.vpr.screenlate.core.common.audio

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {
    @Test
    fun replacementStopsOldOutputAndLateReleaseCannotDropTheReplacement() = runTest {
        val playback = PlaybackCoordinator()
        var playing = true
        val old = checkNotNull(playback.acquire { playing = false; true })
        val replacement = checkNotNull(playback.acquire { true })
        assertThat(playing).isFalse()
        assertThat(old.isCurrent).isFalse()
        old.close()
        assertThat(replacement.isCurrent).isTrue()
    }

    @Test
    fun unacknowledgedStopDeclinesOutputAndCaptureUntilTheOwnerReleases() = runTest {
        val playback = PlaybackCoordinator()
        var released = false
        checkNotNull(playback.acquire { released })
        assertThat(playback.acquire { true }).isNull()
        assertThat(playback.beginCapture()).isNull()
        assertThat(playback.requiresPrivateSpeech).isFalse()
        released = true
        val next = checkNotNull(playback.acquire { true })
        assertThat(next.isCurrent).isTrue()
    }

    @Test
    fun pendingAndRunningCaptureHoldsDoNotRestoreLiveSpeechPrematurely() = runTest {
        val playback = PlaybackCoordinator()
        val stopping = CompletableDeferred<Boolean>()
        playback.acquire { stopping.await() }
        val permissionRequest = async { playback.beginCapture() }
        runCurrent()
        assertThat(playback.requiresPrivateSpeech).isTrue()
        stopping.complete(true)
        val pending = checkNotNull(permissionRequest.await())
        val input = checkNotNull(playback.beginCapture())
        pending.close()
        pending.close()
        assertThat(playback.requiresPrivateSpeech).isTrue()
        input.close()
        assertThat(playback.requiresPrivateSpeech).isFalse()
    }

    @Test
    fun cancellationDuringStopCannotGrantOrLeakCaptureProtection() = runTest {
        val playback = PlaybackCoordinator()
        val stopping = CompletableDeferred<Boolean>()
        playback.acquire { stopping.await() }
        val request = async { playback.beginCapture() }
        runCurrent()
        request.cancel()
        stopping.complete(true)
        request.cancelAndJoin()
        assertThat(playback.requiresPrivateSpeech).isFalse()
        assertThat(playback.acquire { true }?.isCurrent).isTrue()
    }
}
