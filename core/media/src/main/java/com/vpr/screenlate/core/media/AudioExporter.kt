package com.vpr.screenlate.core.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream

/** One PCM encoder and Ogg muxer; timestamps follow cumulative kept frames across every cut. */
@RequiresApi(Build.VERSION_CODES.Q)
internal object AudioExporter {
    private const val OPUS_MIME = "audio/opus"
    private const val DEFAULT_BITRATE = 32000

    data class ExportResult(val isQuiet: Boolean)

    fun exportOpus(
        stream: InputStream,
        edit: AudioEdit,
        sampleRate: Int,
        destination: File,
        bitRate: Int = DEFAULT_BITRATE,
        checkActive: () -> Unit = {},
        minFreeSpaceBytes: Long = 52_428_800L,
        usableSpace: (File) -> Long = { it.usableSpace },
    ): ExportResult {
        val workspace = AudioExportWorkspace(destination, edit.outputFrames, minFreeSpaceBytes, usableSpace)
        workspace.preflight()
        val quiet = PcmAudioEncoder.encode(stream, edit.outputFrames, sampleRate,
            factory = { createEncoder(destination, sampleRate, bitRate, workspace) },
            nowMillis = SystemClock::elapsedRealtime, checkActive = checkActive)
        workspace.finish()
        check(destination.length() > 0) { "Audio export produced an empty file" }
        return ExportResult(quiet)
    }

    private fun createEncoder(
        destination: File,
        sampleRate: Int,
        bitRate: Int,
        workspace: AudioExportWorkspace,
    ): PcmEncoder {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val format = MediaFormat.createAudioFormat(OPUS_MIME, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            val encoder = MediaCodec.createEncoderByType(OPUS_MIME).also { codec = it }
            val container = MediaMuxer(destination.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG).also { muxer = it }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            return AndroidPcmEncoder(encoder, container, workspace)
        } catch (error: Throwable) {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            throw error
        }
    }

    private class AndroidPcmEncoder(
        private val codec: MediaCodec,
        private val muxer: MediaMuxer,
        private val workspace: AudioExportWorkspace,
    ) : PcmEncoder {
        private val info = MediaCodec.BufferInfo()
        private var muxerStarted = false
        private var track = -1

        override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int? {
            // Pending encoded packets must drain before the codec can return another input buffer.
            val index = codec.dequeueInputBuffer(0L)
            if (index < 0) return null
            val input = checkNotNull(codec.getInputBuffer(index))
            input.clear()
            val accepted = minOf(length, input.remaining() / 2 * 2)
            check(length == 0 || accepted > 0) { "Audio encoder has no PCM input capacity" }
            input.put(bytes, offset, accepted)
            val flags = if (endOfInput && accepted == length) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            codec.queueInputBuffer(index, 0, accepted, presentationTimeUs, flags)
            return accepted
        }

        override fun drain(): PcmEncoder.Drain {
            val index = codec.dequeueOutputBuffer(info, 10_000L)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                check(!muxerStarted) { "Audio encoder changed format twice" }
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
                return PcmEncoder.Drain.PROGRESS
            }
            if (index < 0) return PcmEncoder.Drain.NONE
            val data = info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
            val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            try {
                if (data) {
                    check(muxerStarted) { "Audio encoder emitted data before its format" }
                    val output = checkNotNull(codec.getOutputBuffer(index))
                    output.position(info.offset)
                    output.limit(info.offset + info.size)
                    workspace.beforeWrite(info.size)
                    muxer.writeSampleData(track, output, info)
                }
            } finally { codec.releaseOutputBuffer(index, false) }
            return when {
                ended -> PcmEncoder.Drain.FINISHED
                data -> PcmEncoder.Drain.PROGRESS
                else -> PcmEncoder.Drain.NONE
            }
        }

        override fun close() {
            var failure: Throwable? = null
            fun release(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            release { codec.stop() }
            release { codec.release() }
            release { if (muxerStarted) muxer.stop() }
            release { muxer.release() }
            failure?.let { throw it }
        }
    }
}
