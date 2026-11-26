package com.vpr.screenlate.core.anki

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Locally generated Ogg Opus fixtures, independent of the codecs available in the test image. */
internal object OpusTestAudio {
    private const val RATE = 48000

    fun create(file: File, frequency: Int) {
        val name = when (frequency) {
            440 -> "sentence-440.opus"
            660 -> "sentence-660.opus"
            else -> throw IllegalArgumentException("No fixture for $frequency Hz")
        }
        InstrumentationRegistry.getInstrumentation().targetContext.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }

    fun assertDecodes(file: File, expectedFrequency: Int) {
        val samples = decode(file)
        assertThat(samples.size).isAtLeast(RATE / 2)
        var real = 0.0
        var imaginary = 0.0
        val start = RATE / 10
        val count = RATE / 5
        for (i in start until start + count) {
            val angle = 2 * PI * expectedFrequency * i / RATE
            real += samples[i] * cos(angle)
            imaginary += samples[i] * sin(angle)
        }
        val amplitude = 2 * sqrt(real * real + imaginary * imaginary) / count / 32768
        assertThat(amplitude).isGreaterThan(0.15)
    }

    /** Platform playback reaches completion on the headless/no-audio emulator; this is not physical audibility evidence. */
    fun assertMediaPlayerCompletes(file: File) {
        val completed = java.util.concurrent.CountDownLatch(1)
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val player = android.media.MediaPlayer()
        try {
            player.setOnCompletionListener { completed.countDown() }
            player.setOnErrorListener { _, _, _ ->
                failed.set(true)
                completed.countDown()
                true
            }
            player.setDataSource(file.path)
            player.prepare()
            assertThat(player.duration).isGreaterThan(0)
            player.start()
            assertThat(completed.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
            assertThat(failed.get()).isFalse()
        } finally {
            player.release()
        }
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
            val started = SystemClock.elapsedRealtime()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                check(SystemClock.elapsedRealtime() - started < 20000) { "Transferred Opus decoding timed out" }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val input = checkNotNull(codec.getInputBuffer(index))
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        inputDone = size < 0
                        codec.queueInputBuffer(index, 0, maxOf(size, 0),
                            if (inputDone) 0 else extractor.sampleTime,
                            if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!inputDone) extractor.advance()
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        assertThat(codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).isEqualTo(1)
                        assertThat(codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)).isEqualTo(RATE)
                    }
                    else -> if (index >= 0) {
                        try {
                            val buffer = checkNotNull(codec.getOutputBuffer(index))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buffer.get(bytes)
                            output.write(bytes)
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally {
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            val bytes = output.toByteArray()
            return ShortArray(bytes.size / 2) { i ->
                ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
        } finally {
            decoder?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }
}
