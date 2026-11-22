package com.vpr.screenlate.core.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real codec/container tests in the isolated media package; no playback, capture or collection access. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class AudioExporterDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val rate = 48000

    @Test
    fun decodedHeadMiddleAndTailFollowSeveralCuts() {
        val sourceFrames = rate * 6
        val source = ByteArray(sourceFrames * 2)
        val frequencies = intArrayOf(220, 330, 440, 550, 660, 880)
        for (frame in 0 until sourceFrames) {
            val frequency = if (frame < 480 || frame >= sourceFrames - 480) 2203 else frequencies[frame / rate]
            val value = (sin(2 * PI * frequency * frame / rate) * 10000).toInt()
            source[frame * 2] = value.toByte()
            source[frame * 2 + 1] = (value shr 8).toByte()
        }
        val edit = AudioEdit(AudioRange(0, sourceFrames.toLong()),
            listOf(AudioRange(48000, 96000), AudioRange(144000, 192000)))
        val file = File.createTempFile("codec-test-", ".opus", context.cacheDir)
        try {
            val input = object : ByteArrayInputStream(source) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    super.read(buffer, offset, minOf(17, length))
            }
            AudioEditInputStream(input, edit).use { AudioExporter.exportOpus(it, edit, rate, file) }
            val decoded = decode(file)
            val expected = 192000
            assertThat(abs(decoded.size - expected)).isAtMost(960)
            for ((start, frequency) in listOf(4800 to 220, 52800 to 440, 100800 to 660, 177600 to 880)) {
                assertThat(amplitude(decoded, start, 4800, frequency)).isGreaterThan(0.15)
            }
            fun markerPeak(start: Int, end: Int): Double = (start..end step 120)
                .maxOf { amplitude(decoded, it, 240, 2203) }
            assertThat(markerPeak(0, 720)).isGreaterThan(0.1)
            assertThat(markerPeak(expected - 1440, minOf(decoded.size - 240, expected + 720))).isGreaterThan(0.1)
            Log.i("AudioExporterDeviceTest", "decoded frames=${decoded.size} expected=$expected bytes=${file.length()}")
        } finally { file.delete() }
    }

    @Test
    fun failedContainerConstructionDoesNotExhaustCodecResources() {
        val output = File.createTempFile("codec-retry-", ".opus", context.cacheDir)
        val edit = AudioEdit(AudioRange(0, 4800))
        fun encode(file: File) = ByteArrayInputStream(ByteArray(9600)).use {
            AudioExporter.exportOpus(it, edit, rate, file)
        }
        try {
            encode(output)
            val before = File("/proc/self/fd").list()?.size ?: 0
            repeat(16) {
                val missing = File(context.cacheDir, "absent-codec-directory/recording.opus")
                assertThrows(Exception::class.java) { encode(missing) }
            }
            encode(output)
            assertThat(output.length()).isGreaterThan(0)
            // Total descriptors include asynchronously evicted platform buffer-pool caches.
            val after = File("/proc/self/fd").list()?.size ?: 0
            Log.i("AudioExporterDeviceTest", "failed construction fdBefore=$before fdAfter=$after")
        } finally { output.delete() }
    }

    @Test
    fun maximumDurationStreamingExport() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("longAudioExport") == "true")
        val file = File.createTempFile("codec-long-", ".opus", context.cacheDir)
        val frames = rate * 30L * 60
        val start = SystemClock.elapsedRealtime()
        val baseline = Debug.getPss()
        var peak = baseline
        var nextSample = start
        var position = 0L
        val input = object : InputStream() {
            override fun read(): Int = error("Bulk reads only")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = minOf(length.toLong(), (frames - position) * 2).toInt()
                if (count == 0) return -1
                for (i in 0 until count step 2) {
                    val value = (sin(2 * PI * 440 * position++ / rate) * 10000).toInt()
                    buffer[offset + i] = value.toByte()
                    buffer[offset + i + 1] = (value shr 8).toByte()
                }
                return count
            }
        }
        try {
            val result = input.use {
                AudioExporter.exportOpus(it, AudioEdit(AudioRange(0, frames)), rate, file, checkActive = {
                    val now = SystemClock.elapsedRealtime()
                    if (now >= nextSample) { peak = maxOf(peak, Debug.getPss()); nextSample = now + 1000 }
                })
            }
            assertThat(result.isQuiet).isFalse()
            assertThat(position).isEqualTo(frames)
            assertThat(file.length()).isGreaterThan(0)
            Log.i("AudioExporterDeviceTest", "long frames=$frames millis=${SystemClock.elapsedRealtime() - start} " +
                "bytes=${file.length()} baselinePssKiB=$baseline peakPssKiB=$peak")
        } finally { file.delete() }
    }

    private fun amplitude(samples: ShortArray, start: Int, count: Int, frequency: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in start until minOf(samples.size, start + count)) {
            val angle = 2 * PI * frequency * i / rate
            real += samples[i] * cos(angle)
            imaginary += samples[i] * sin(angle)
        }
        return 2 * sqrt(real * real + imaginary * imaginary) / count / 32768
    }

    private fun decode(file: File): ShortArray {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
                .also { decoder = it }
            codec.configure(format, null, null, 0)
            codec.start()
            val output = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            val start = SystemClock.elapsedRealtime()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                check(SystemClock.elapsedRealtime() - start < 20000) { "Test decoding timed out" }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(0)
                    if (index >= 0) {
                        val input = checkNotNull(codec.getInputBuffer(index))
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        inputDone = size < 0
                        codec.queueInputBuffer(index, 0, maxOf(size, 0), if (inputDone) 0 else extractor.sampleTime,
                            if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!inputDone) extractor.advance()
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    assertThat(codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).isEqualTo(1)
                    assertThat(codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)).isEqualTo(rate)
                    if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        assertThat(codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)).isEqualTo(AudioFormat.ENCODING_PCM_16BIT)
                    }
                } else if (index >= 0) {
                    try {
                        val buffer = checkNotNull(codec.getOutputBuffer(index))
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buffer.get(bytes)
                        output.write(bytes)
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { codec.releaseOutputBuffer(index, false) }
                }
            }
            val bytes = output.toByteArray()
            return ShortArray(bytes.size / 2) { i -> ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort() }
        } finally {
            decoder?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }
}
