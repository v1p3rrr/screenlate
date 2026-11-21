package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.os.SystemClock
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.audio.Pronunciation
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import com.vpr.screenlate.core.media.AudioSource
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Real MediaPlayer/AudioTrack through the two production facades; synthetic silence, no collection. */
@RunWith(AndroidJUnit4::class)
class OwnPlaybackDeviceTest {
    @Test
    fun pronunciationAndPreviewShareOutputWithoutStoppingCapture() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playback = PlaybackCoordinator()
        val recorder = SentenceRecorder(context, RecordingSettingsRepository(Store()), playback)
        val pronunciation = AudioPlayer(context, AudioSettingsRepository(Store()), playback)
        val clip = File(context.cacheDir, "playback-${UUID.randomUUID()}.wav")
        try {
            withContext(Dispatchers.IO) { writeSilence(clip) }
            assertThat(recorder.start(SilentSource())).isTrue()
            val range = AudioRange(0, 48000)
            withTimeout(5000) {
                while (recorder.waveformSummary(range) == null) delay(1)
            }
            val interruptedOnMain = CompletableDeferred<Boolean>()
            recorder.startPreview(AudioEdit(range), startFrame = 0, onStopped = {
                interruptedOnMain.complete(Looper.myLooper() === Looper.getMainLooper())
            })
            withTimeout(5000) { while (recorder.previewPosition <= 0) delay(1) }
            assertThat(recorder.isPreviewPlaying).isTrue()
            assertThat(pronunciation.play(Pronunciation.Clip(AudioClip(clip, "fixture", "wav")))).isTrue()
            assertThat(recorder.isPreviewPlaying).isFalse()
            assertThat(withTimeout(5000) { interruptedOnMain.await() }).isTrue()
            assertThat(recorder.isRecording).isTrue()
            recorder.seekPreview(0)
            assertThat(recorder.isPreviewPlaying).isFalse()
            recorder.startPreview(AudioEdit(range), startFrame = 0)
            withTimeout(5000) { while (recorder.previewPosition <= 0) delay(1) }
            assertThat(recorder.isRecording).isTrue()
            assertThat(playback.requiresPrivateSpeech).isTrue()
        } finally {
            pronunciation.stop()
            recorder.close()
            withContext(Dispatchers.IO) { clip.delete() }
        }
        assertThat(playback.requiresPrivateSpeech).isFalse()
    }

    private fun writeSilence(file: File) {
        val bytes = 16000 * 2 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + bytes)
            .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(bytes).array()
        file.outputStream().use { output ->
            output.write(header)
            val zeroes = ByteArray(4096)
            var remaining = bytes
            while (remaining > 0) {
                val count = minOf(remaining, zeroes.size)
                output.write(zeroes, 0, count)
                remaining -= count
            }
        }
    }

    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private class SilentSource : AudioSource {
        override val sampleRate = 48000
        override val channelCount = 1
        private var remaining = 48000 * 2
        private val stopped = CountDownLatch(1)
        private var origin = 0L
        override fun start(): Boolean { origin = SystemClock.elapsedRealtimeNanos(); return true }
        override fun timestampNanos() = origin
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) { stopped.await(); return -1 }
            val count = minOf(length, remaining)
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
        override fun stop() { stopped.countDown() }
        override fun close() { stopped.countDown() }
    }
}
