package com.vpr.screenlate.audioprobe

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.util.Properties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object ProbeCodec {
    fun encode(pcm: File, destination: File, rate: Int, metrics: Properties, opus: Boolean = false) {
        val mime = if (opus) "audio/opus" else "audio/mp4a-latm"
        val format = MediaFormat.createAudioFormat(mime, rate, 1).apply {
            if (!opus) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (opus) 32000 else 64000)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        val muxer = MediaMuxer(destination.path, if (opus) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val started = SystemClock.elapsedRealtime()
            var frames = 0L
            var inputDone = false
            var outputDone = false
            var track = -1
            var outputFrames = 0
            val buffer = ByteArray(8192)
            val info = MediaCodec.BufferInfo()
            pcm.inputStream().use { input ->
                while (!outputDone) {
                    check(SystemClock.elapsedRealtime() - started < 30000) { "encode-timeout" }
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val target = checkNotNull(codec.getInputBuffer(index))
                            val count = input.read(buffer, 0, minOf(buffer.size, target.capacity() / 2 * 2))
                            val end = count < 0
                            if (!end) target.put(buffer, 0, count)
                            val last = end || input.available() == 0
                            codec.queueInputBuffer(index, 0, maxOf(count, 0), frames * 1_000_000 / rate,
                                if (last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                            if (!end) frames += count / 2
                            inputDone = last
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxerStarted)
                        val outputFormat = codec.outputFormat
                        metrics["encoderFormat"] = outputFormat.toString()
                        track = muxer.addTrack(outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (index >= 0) {
                        val output = checkNotNull(codec.getOutputBuffer(index))
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted)
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(track, output, info)
                            if (outputFrames == 0) metrics["firstEncodedPts"] = info.presentationTimeUs.toString()
                            metrics["lastEncodedPts"] = info.presentationTimeUs.toString()
                            outputFrames++
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            // Explicit track end avoids MediaMuxer's inferred last-sample duration.
            if (muxerStarted && !opus) {
                val end = MediaCodec.BufferInfo().apply {
                    set(0, 0, frames * 1_000_000 / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                muxer.writeSampleData(track, ByteBuffer.allocate(0), end)
            }
            metrics["encodedPackets"] = outputFrames.toString()
            metrics["encodeMillis"] = (SystemClock.elapsedRealtime() - started).toString()
            metrics["encoder"] = codec.name
        } finally {
            runCatching { codec.stop() }
            codec.release()
            try { if (muxerStarted) muxer.stop() } finally { muxer.release() }
        }
        metrics["encodedBytes"] = destination.length().toString()
    }

    fun decode(source: File, destination: File, metrics: Properties) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(source.path)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            metrics["extractorFormat"] = format.toString()
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            val start = SystemClock.elapsedRealtime()
            var inputDone = false
            var outputDone = false
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val info = MediaCodec.BufferInfo()
            destination.outputStream().use { output ->
                while (!outputDone) {
                    check(SystemClock.elapsedRealtime() - start < 30000) { "decode-timeout" }
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val target = checkNotNull(codec.getInputBuffer(index))
                            val count = extractor.readSampleData(target, 0)
                            inputDone = count < 0
                            codec.queueInputBuffer(index, 0, maxOf(0, count), if (inputDone) 0 else extractor.sampleTime,
                                if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                            if (!inputDone) extractor.advance()
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        metrics["decoderFormat"] = codec.outputFormat.toString()
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        check(codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
                    } else if (index >= 0) {
                        val buffer = checkNotNull(codec.getOutputBuffer(index))
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buffer.get(bytes)
                        output.write(bytes)
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            analyze(destination, rate, metrics, "decoded")
        } finally {
            decoder?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }

    fun analyze(pcm: File, rate: Int, metrics: Properties, prefix: String) {
        var frames = 0L
        var square = 0.0
        var sourceSin = 0.0
        var sourceCos = 0.0
        var ownSin = 0.0
        var ownCos = 0.0
        val bytes = ByteArray(9600)
        pcm.inputStream().use { input ->
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                check(count % 2 == 0)
                for (i in 0 until count step 2) {
                    val value = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toDouble()
                    val time = frames++.toDouble() / rate
                    square += value * value
                    sourceSin += value * sin(2 * PI * 440 * time)
                    sourceCos += value * cos(2 * PI * 440 * time)
                    ownSin += value * sin(2 * PI * 997 * time)
                    ownCos += value * cos(2 * PI * 997 * time)
                }
            }
        }
        metrics["${prefix}Frames"] = frames.toString()
        metrics["${prefix}Rms"] = sqrt(square / maxOf(frames, 1)).toString()
        metrics["${prefix}SourceAmplitude"] = (2 * sqrt(sourceSin * sourceSin + sourceCos * sourceCos) / maxOf(frames, 1)).toString()
        metrics["${prefix}OwnAmplitude"] = (2 * sqrt(ownSin * ownSin + ownCos * ownCos) / maxOf(frames, 1)).toString()
    }
}
