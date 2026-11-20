package com.vpr.screenlate.core.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream

/**
 * Single-encoder streaming audio export for sentence recordings.
 * Kept ranges from [edit] stream through one [MediaCodec] and [MediaMuxer] instance into an Opus/Ogg file.
 *
 * Presentation timestamps derive from cumulative kept frames, so excluded cuts remove time without gaps.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal object AudioExporter {

    private const val OPUS_MIME = "audio/opus"
    private const val DEFAULT_BITRATE = 32000
    private const val QUIET_AMPLITUDE_THRESHOLD = 0.02f

    data class ExportResult(
        val isQuiet: Boolean,
    )

    fun exportOpus(
        stream: InputStream,
        edit: AudioEdit,
        sampleRate: Int,
        destination: File,
        bitRate: Int = DEFAULT_BITRATE,
    ): ExportResult {
        require(edit.outputFrames > 0 && sampleRate > 0)
        val format = MediaFormat.createAudioFormat(OPUS_MIME, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
        val codec = MediaCodec.createEncoderByType(OPUS_MIME)
        val muxer = MediaMuxer(destination.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        var muxerStarted = false
        var trackIndex = -1
        var maxAmplitude = 0f

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val startTime = SystemClock.elapsedRealtime()
            var framesSubmitted = 0L
            var inputDone = false
            var outputDone = false
            val buffer = ByteArray(8192)
            val info = MediaCodec.BufferInfo()

            while (!outputDone) {
                check(SystemClock.elapsedRealtime() - startTime < 30_000L) { "Audio export timed out" }

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = checkNotNull(codec.getInputBuffer(inputIndex))
                        val maxRead = minOf(buffer.size, inputBuffer.capacity() / 2 * 2)
                        val remainingFrames = edit.outputFrames - framesSubmitted
                        val maxBytes = minOf(maxRead.toLong(), remainingFrames * 2).toInt()

                        val readCount = if (maxBytes > 0) stream.read(buffer, 0, maxBytes) else -1
                        val isEos = readCount < 0 || framesSubmitted + (readCount / 2) >= edit.outputFrames

                        if (readCount > 0) {
                            inputBuffer.clear()
                            inputBuffer.put(buffer, 0, readCount)
                            val samples = readCount / 2
                            for (i in 0 until samples) {
                                val s = ((buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)).toShort()
                                val norm = kotlin.math.abs(s.toInt()) / 32768f
                                if (norm > maxAmplitude) maxAmplitude = norm
                            }
                        }

                        val ptsUs = framesSubmitted * 1_000_000L / sampleRate
                        val flags = if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        codec.queueInputBuffer(inputIndex, 0, maxOf(0, readCount), ptsUs, flags)

                        if (readCount > 0) framesSubmitted += readCount / 2
                        inputDone = isEos
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxerStarted)
                    val outputFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outputIndex >= 0) {
                    val outputBuffer = checkNotNull(codec.getOutputBuffer(outputIndex))
                    if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        check(muxerStarted)
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, info)
                    }
                    outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            try {
                if (muxerStarted) muxer.stop()
            } finally {
                muxer.release()
            }
        }
        check(destination.length() > 0) { "Audio export produced an empty file" }
        return ExportResult(isQuiet = maxAmplitude < QUIET_AMPLITUDE_THRESHOLD)
    }
}
