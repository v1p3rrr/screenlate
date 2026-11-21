package com.vpr.screenlate.core.media

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic PCM only: no projection, microphone, playback or app collection is used. */
@RunWith(AndroidJUnit4::class)
class SentenceRecorderDeviceTest {
    @Test
    fun mainCallerCanRecordScanReadAndStopWithoutRunningSourceWorkOnMain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "session-test-${UUID.randomUUID()}")
        val time = AtomicLong(10_000L)
        val source = SyntheticSource()
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val recorder = SentenceRecorder(directory, RecordingSettingsRepository(store), { time.get() }, { time.get() * 1_000_000L })
        try {
            withContext(Dispatchers.Main) {
                assertThat(recorder.start(source)).isTrue()
                withTimeout(5000) {
                    var ready = false
                    while (!ready) {
                        recorder.snapshot(AudioRange(0, 100))?.use { ready = true }
                        if (!ready) delay(1)
                    }
                }
                time.set(10_100)
                val scan = checkNotNull(recorder.acquireScan())
                val shown = checkNotNull(recorder.openEditor(scan))
                assertThat(shown.source).isEqualTo(AudioRange(0, 100))
                recorder.snapshot(checkNotNull(shown.source))!!.use { lease ->
                    recorder.stop()
                    assertThat(recorder.isCaptureActive).isFalse()
                    val bytes = withContext(Dispatchers.IO) { lease.openStream().use { it.readBytes() } }
                    assertThat(bytes).isEqualTo(ByteArray(200) { it.toByte() })
                }
                recorder.releaseContext(scan)
                recorder.close()
            }
            assertThat(source.released.get()).isTrue()
        } finally {
            recorder.close()
            directory.listFiles()?.forEach { child ->
                assertThat(child.listFiles()?.isEmpty() ?: true).isTrue()
                child.delete()
            }
            directory.delete()
        }
    }

    private class SyntheticSource : AudioSource {
        override val sampleRate = 1000
        override val channelCount = 1
        private val emitted = AtomicBoolean(false)
        private val stopped = CountDownLatch(1)
        val released = AtomicBoolean(false)

        override fun start(): Boolean { assertOffMain(); return true }
        override fun timestampNanos(): Long { assertOffMain(); return 10_000_000_000L }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            assertOffMain()
            if (emitted.compareAndSet(false, true)) {
                for (i in 0 until 200) buffer[offset + i] = i.toByte()
                return 200
            }
            stopped.await()
            return -1
        }
        override fun stop() { assertOffMain(); stopped.countDown() }
        override fun close() { assertOffMain(); released.set(true) }
        private fun assertOffMain() { check(Looper.myLooper() !== Looper.getMainLooper()) }
    }
}
