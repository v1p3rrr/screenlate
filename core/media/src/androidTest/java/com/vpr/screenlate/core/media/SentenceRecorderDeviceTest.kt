package com.vpr.screenlate.core.media

import android.content.Context
import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import android.annotation.SuppressLint
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import java.io.File
import java.util.Properties
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
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic PCM; preview plays silence through AudioTrack. No projection, microphone or collection access. */
@RunWith(AndroidJUnit4::class)
class SentenceRecorderDeviceTest {
    @Test
    @SuppressLint("SetJavaScriptEnabled") // Local inline fixture only.
    fun productionPreviewDeliversProgressAndCompletionToWebViewOnMain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (frames in listOf(24_000, 240, 1)) {
            val ready = CompletableDeferred<Unit>()
            val result = CompletableDeferred<String>()
            val page = withContext(Dispatchers.Main) {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) { ready.complete(Unit) }
                    }
                    loadData("<html><body>Preview fixture</body></html>", "text/html", "UTF-8")
                }
            }
            val store = object : DataStore<Preferences> {
                override val data = MutableStateFlow(emptyPreferences())
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    transform(data.value).also { data.value = it }
            }
            // Uses the production constructor, real AudioTrack, and its production Main callback executor.
            val recorder = SentenceRecorder(context, RecordingSettingsRepository(store), PlaybackCoordinator())
            val source = SilentSource(frames)
            try {
                withTimeout(10_000) { ready.await() }
                recorder.start(source)
                withTimeout(5000) {
                    var available = false
                    while (!available) {
                        recorder.snapshot(AudioRange(0, frames.toLong()))?.use { available = true }
                        if (!available) delay(1)
                    }
                }
                recorder.startPreview(AudioEdit(AudioRange(0, frames.toLong())), onProgress = { frame ->
                    if (Looper.myLooper() !== Looper.getMainLooper()) {
                        result.completeExceptionally(AssertionError("Progress was not delivered on Main"))
                    } else page.evaluateJavascript("window.previewFrame = $frame", null)
                }, onComplete = {
                    if (Looper.myLooper() !== Looper.getMainLooper()) {
                        result.completeExceptionally(AssertionError("Completion was not delivered on Main"))
                    } else page.evaluateJavascript("window.previewFrame") { result.complete(it) }
                }, onError = { result.completeExceptionally(AssertionError("Preview failed for $frames frames: $it; position ${recorder.previewPosition}")) })
                assertThat(withTimeout(10_000) { result.await() }).isEqualTo(frames.toString())
                assertThat(recorder.isPreviewPlaying).isFalse()
                assertThat(recorder.previewPosition).isEqualTo(frames.toLong())
            } finally {
                recorder.close()
                withContext(Dispatchers.Main) { page.destroy() }
            }
        }
    }

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
                withTimeout(5000) {
                    while (withContext(Dispatchers.IO) { directory.listFiles()?.isNotEmpty() == true }) delay(1)
                }
                assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Stopped::class.java)
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

    @Test
    fun repeatedRecordStopCloseCyclesLeaveNoPcmAndStableDescriptors() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "session-cycles-${UUID.randomUUID()}")
        val descriptors = mutableListOf<Int>()
        val pss = mutableListOf<Long>()
        try {
            repeat(20) {
                val store = object : DataStore<Preferences> {
                    override val data = MutableStateFlow(emptyPreferences())
                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        transform(data.value).also { data.value = it }
                }
                val recorder = SentenceRecorder(directory, RecordingSettingsRepository(store))
                try {
                    assertThat(recorder.start(SyntheticSource())).isTrue()
                    withTimeout(5_000) {
                        while (true) {
                            val complete = recorder.snapshot(AudioRange(0, 100))?.use { snapshot ->
                                snapshot.openStream().use { input -> input.readBytes().size == 200 }
                            } == true
                            if (complete) break
                            delay(1)
                        }
                    }
                } finally {
                    recorder.stop()
                    recorder.close()
                }
                withTimeout(5_000) {
                    while (withContext(Dispatchers.IO) { directory.listFiles()?.isNotEmpty() == true }) delay(1)
                }
                descriptors += File("/proc/self/fd").list()?.size ?: -1
                pss += Debug.getPss()
            }
            assertThat(descriptors.takeLast(5).max()).isAtMost(descriptors.first() + 4)
            val metrics = Properties().apply {
                setProperty("cycles", "20")
                setProperty("descriptors", descriptors.joinToString(","))
                setProperty("pssKiB", pss.joinToString(","))
                setProperty("audioFilesLeft", directory.walkTopDown().count { it.isFile }.toString())
            }
            File(context.filesDir, "recorder-cycle-metrics.properties").outputStream().use {
                metrics.store(it, "Repeated synthetic record/stop/close cycles")
            }
        } finally {
            directory.deleteRecursively()
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

    private class SilentSource(private val frames: Int) : AudioSource {
        override val sampleRate = 48000
        override val channelCount = 1
        private var remaining = frames * 2
        private val stopped = CountDownLatch(1)
        private var origin = 0L
        override fun start(): Boolean { origin = SystemClock.elapsedRealtimeNanos(); return true }
        override fun timestampNanos(): Long = origin
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
