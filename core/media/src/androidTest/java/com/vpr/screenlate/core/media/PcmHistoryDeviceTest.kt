package com.vpr.screenlate.core.media

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Properties
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcmHistoryDeviceTest {
    @Test fun disjointFiveMinuteRingAndTwelveMinuteContextRemainBounded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "pcm-test-${UUID.randomUUID()}")
        val history = PcmHistory(directory, chunkFrames = 48_000, maxBytes = 128L * 1024 * 1024, maxFiles = 1100)
        val frame = ByteArray(96_000) { (it % 251).toByte() }
        val beforePss = Debug.getPss()
        val beforeFds = File("/proc/self/fd").list()?.size ?: -1
        val started = SystemClock.elapsedRealtime()
        var maximumMicros = 0L
        var maximumBytes = 0L
        var maximumFiles = 0
        try {
            // Simulated source time: 30 minutes of writes, not 30 minutes of wall-clock capture.
            for (second in 0 until 1800) {
                val ring = AudioRange(maxOf(0, second - 299) * 48_000L, (second + 1) * 48_000L)
                val protected = if (second >= 300) listOf(AudioRange(0, 720 * 48_000L)) else emptyList()
                history.retain(protected + ring)
                val writeStart = SystemClock.elapsedRealtimeNanos()
                history.append(frame)
                maximumMicros = maxOf(maximumMicros, (SystemClock.elapsedRealtimeNanos() - writeStart) / 1000)
                maximumBytes = maxOf(maximumBytes, history.bytes)
                maximumFiles = maxOf(maximumFiles, history.files)
                assertThat(history.bytes).isAtMost(1020L * frame.size)
                assertThat(history.files).isAtMost(1020)
            }
            history.snapshot(AudioRange(0, 48_000)).use { snapshot ->
                assertThat(snapshot.openStream().use { it.readBytes() }).isEqualTo(frame)
            }
            val metrics = Properties().apply {
                setProperty("simulatedSeconds", "1800")
                setProperty("elapsedMillis", (SystemClock.elapsedRealtime() - started).toString())
                setProperty("logicalWrittenBytes", (1800L * frame.size).toString())
                setProperty("maximumRetainedBytes", maximumBytes.toString())
                setProperty("maximumFiles", maximumFiles.toString())
                setProperty("maximumAppendMicros", maximumMicros.toString())
                setProperty("pssBeforeKb", beforePss.toString())
                setProperty("pssAfterKb", Debug.getPss().toString())
                setProperty("fdsBefore", beforeFds.toString())
                setProperty("fdsAfter", (File("/proc/self/fd").list()?.size ?: -1).toString())
            }
            File(context.filesDir, "pcm-benchmark.properties").outputStream().use { metrics.store(it, "Synthetic PCM storage benchmark") }
        } finally {
            history.close()
            assertThat(history.bytes).isEqualTo(0)
            assertThat(directory.listFiles()?.size).isEqualTo(0)
            directory.delete()
        }
    }
}
