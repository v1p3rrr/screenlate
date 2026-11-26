package com.vpr.screenlate.core.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Properties
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in allocation profile separating codec, container and production exporter lifetimes. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CodecNativeIsolationDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val rate = 48000
    private val frames = rate / 4L
    private val source = ByteArray(frames.toInt() * 2) { index ->
        val frame = index / 2
        val value = (sin(2 * PI * 440 * frame / rate) * 10000).toInt()
        if (index % 2 == 0) value.toByte() else (value shr 8).toByte()
    }

    @Test
    fun codecAllocationProfile() {
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("codecMode")
        assumeTrue("Select codecMode", mode in setOf(
            "codec-lifecycle", "muxer-lifecycle", "extract", "remux", "muxer-write", "encode", "exporter"))
        val selectedMode = checkNotNull(mode)
        val cycles = arguments.getString("codecCycles")?.toIntOrNull()?.takeIf { it > 0 } ?: 40
        val settleMillis = arguments.getString("codecSettleMillis")?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
        val sampleLimit = arguments.getString("codecSampleLimit")?.toIntOrNull()?.takeIf { it >= 0 } ?: Int.MAX_VALUE
        val tag = arguments.getString("codecMetricsTag")?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
        val seed = File(context.cacheDir, "codec-isolation-seed.opus")
        try {
            if (selectedMode in setOf("extract", "remux", "muxer-write")) export(seed)
            val writtenSeed = if (selectedMode == "muxer-write") {
                readSeed(seed).let { it.copy(samples = it.samples.take(sampleLimit)) }
            } else {
                null
            }
            Log.i("CodecNativeIsolation", "mode=$selectedMode seedSamples=${writtenSeed?.samples?.size ?: -1}")
            fun operation(cycle: Int) {
                when (selectedMode) {
                    "codec-lifecycle" -> codecLifecycle()
                    "muxer-lifecycle" -> muxerLifecycle(cycle)
                    "extract" -> extract(seed)
                    "remux" -> remux(seed, cycle)
                    "muxer-write" -> writeMuxer(checkNotNull(writtenSeed), cycle)
                    "encode" -> encodeWithoutMuxer()
                    "exporter" -> {
                        val output = File(context.cacheDir, "codec-isolation-exporter-$cycle.opus")
                        try { export(output) } finally { assertThat(output.delete()).isTrue() }
                    }
                }
            }
            repeat(5) { operation(it) }
            SystemClock.sleep(7_000)
            val native = mutableListOf<Long>()
            val nativeSize = mutableListOf<Long>()
            val pss = mutableListOf<Long>()
            val descriptors = mutableListOf<Int>()
            val threads = mutableListOf<Int>()
            val started = SystemClock.elapsedRealtime()
            repeat(cycles) { cycle ->
                operation(cycle + 5)
                SystemClock.sleep(settleMillis)
                native += Debug.getNativeHeapAllocatedSize()
                nativeSize += Debug.getNativeHeapSize()
                pss += Debug.getPss()
                descriptors += File("/proc/self/fd").list()?.size ?: -1
                threads += Thread.activeCount()
                if (cycle == 0 || (cycle + 1) % 10 == 0) {
                    Log.i("CodecNativeIsolation", "mode=$selectedMode cycle=${cycle + 1}/$cycles " +
                        "native=${native.last()} nativeSize=${nativeSize.last()} pss=${pss.last()} " +
                        "fd=${descriptors.last()} threads=${threads.last()}")
                }
            }
            SystemClock.sleep(7_000)
            val metrics = Properties().apply {
                setProperty("mode", selectedMode)
                setProperty("cycles", cycles.toString())
                setProperty("settleMillisPerCycle", settleMillis.toString())
                setProperty("elapsedMillis", (SystemClock.elapsedRealtime() - started).toString())
                setProperty("nativeHeapBytes", native.joinToString(","))
                setProperty("nativeHeapSizeBytes", nativeSize.joinToString(","))
                setProperty("pssKiB", pss.joinToString(","))
                setProperty("descriptors", descriptors.joinToString(","))
                setProperty("threads", threads.joinToString(","))
                setProperty("settledNativeHeapBytes", Debug.getNativeHeapAllocatedSize().toString())
                setProperty("settledNativeHeapSizeBytes", Debug.getNativeHeapSize().toString())
                setProperty("settledPssKiB", Debug.getPss().toString())
                setProperty("settledDescriptors", (File("/proc/self/fd").list()?.size ?: -1).toString())
            }
            File(context.filesDir, "codec-isolation-$selectedMode${tag?.let { "-$it" }.orEmpty()}.properties")
                .outputStream().use { metrics.store(it, "Isolated native codec allocation profile") }
        } finally {
            seed.delete()
        }
    }

    private fun export(output: File) {
        if (!output.exists()) assertThat(output.createNewFile()).isTrue()
        ByteArrayInputStream(source).use {
            AudioExporter.exportOpus(it, AudioEdit(AudioRange(0, frames)), rate, output)
        }
    }

    private fun codecLifecycle() {
        val codec = MediaCodec.createEncoderByType("audio/opus")
        try {
            codec.configure(opusFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun muxerLifecycle(cycle: Int) {
        val output = File(context.cacheDir, "codec-isolation-empty-$cycle.opus")
        try {
            MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG).release()
        } finally {
            assertThat(output.delete()).isTrue()
        }
    }

    private data class EncodedSeed(val format: MediaFormat, val samples: List<EncodedSample>)
    private data class EncodedSample(val bytes: ByteArray, val timeUs: Long, val flags: Int)

    private fun readSeed(seed: File): EncodedSeed {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(seed.path)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val buffer = ByteBuffer.allocateDirect(65_536)
            val samples = mutableListOf<EncodedSample>()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val bytes = ByteArray(size)
                buffer.position(0)
                buffer.get(bytes)
                samples += EncodedSample(bytes, extractor.sampleTime, extractor.sampleFlags)
                extractor.advance()
            }
            return EncodedSeed(format, samples)
        } finally {
            extractor.release()
        }
    }

    private fun extract(seed: File) {
        readSeed(seed)
    }

    private fun writeMuxer(seed: EncodedSeed, cycle: Int) {
        val output = File(context.cacheDir, "codec-isolation-muxer-write-$cycle.opus")
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        var started = false
        try {
            val track = muxer.addTrack(seed.format)
            muxer.start()
            started = true
            val buffer = ByteBuffer.allocateDirect(65_536)
            val info = MediaCodec.BufferInfo()
            for (sample in seed.samples) {
                buffer.clear()
                buffer.put(sample.bytes)
                buffer.flip()
                info.set(0, sample.bytes.size, sample.timeUs, sample.flags)
                muxer.writeSampleData(track, buffer, info)
            }
        } finally {
            runCatching { if (started) muxer.stop() }
            muxer.release()
            assertThat(output.delete()).isTrue()
        }
    }

    private fun remux(seed: File, cycle: Int) {
        val output = File(context.cacheDir, "codec-isolation-remux-$cycle.opus")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(seed.path)
            extractor.selectTrack(0)
            val mux = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG).also { muxer = it }
            val track = mux.addTrack(extractor.getTrackFormat(0))
            mux.start()
            started = true
            val buffer = ByteBuffer.allocateDirect(65_536)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                mux.writeSampleData(track, buffer, info)
                extractor.advance()
            }
        } finally {
            muxer?.let {
                runCatching { if (started) it.stop() }
                it.release()
            }
            extractor.release()
            assertThat(output.delete()).isTrue()
        }
    }

    private fun encodeWithoutMuxer() {
        PcmAudioEncoder.encode(
            input = ByteArrayInputStream(source),
            frames = frames,
            sampleRate = rate,
            factory = { DiscardingOpusEncoder() },
            nowMillis = SystemClock::elapsedRealtime,
        )
    }

    private class DiscardingOpusEncoder : PcmEncoder {
        private val codec = MediaCodec.createEncoderByType("audio/opus")
        private val info = MediaCodec.BufferInfo()

        init {
            codec.configure(opusFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int? {
            val index = codec.dequeueInputBuffer(0L)
            if (index < 0) return null
            val input = checkNotNull(codec.getInputBuffer(index))
            input.clear()
            val accepted = minOf(length, input.remaining() / 2 * 2)
            input.put(bytes, offset, accepted)
            val flags = if (endOfInput && accepted == length) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            codec.queueInputBuffer(index, 0, accepted, presentationTimeUs, flags)
            return accepted
        }

        override fun drain(): PcmEncoder.Drain {
            val index = codec.dequeueOutputBuffer(info, 10_000L)
            if (index < 0) return if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                PcmEncoder.Drain.PROGRESS
            } else {
                PcmEncoder.Drain.NONE
            }
            val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val data = info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
            codec.releaseOutputBuffer(index, false)
            return when {
                ended -> PcmEncoder.Drain.FINISHED
                data -> PcmEncoder.Drain.PROGRESS
                else -> PcmEncoder.Drain.NONE
            }
        }

        override fun close() {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private companion object {
        fun opusFormat(): MediaFormat = MediaFormat.createAudioFormat("audio/opus", 48000, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 32000)
            setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
        }
    }
}
