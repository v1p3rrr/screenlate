package com.vpr.screenlate.core.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

/**
 * [AudioSink] backed by Android's [AudioTrack].
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class AndroidAudioTrackSink(sampleRate: Int = 48000) : AudioSink {
    private val progress = java.lang.Object()
    private var previousHead = 0L
    private var headEpoch = 0L
    private val padding = ByteArray(4096)
    private var paddedBytes = 0L
    private var paddingTargetBytes = 0L

    private val track: AudioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    } else {
        @Suppress("DEPRECATION")
        AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096),
            AudioTrack.MODE_STREAM,
        )
    }

    init {
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Audio output is not initialized" }
        } catch (error: Exception) {
            track.release()
            throw error
        }
    }

    override val playedFrames: Long
        get() {
            val raw = track.playbackHeadPosition.toLong() and 0xffff_ffffL
            if (raw < previousHead) headEpoch += 0x1_0000_0000L
            previousHead = raw
            return headEpoch + raw
        }

    override fun write(buffer: ByteArray, offset: Int, size: Int): Int {
        return track.write(buffer, offset, size, AudioTrack.WRITE_NON_BLOCKING)
    }

    override fun awaitProgress(timeoutMillis: Long) {
        // Sample the native head on the playback worker, without accumulating periodic Main messages.
        // Pause/flush/stop notify immediately; normal head checks have a bounded timeout.
        synchronized(progress) { if (timeoutMillis > 0) progress.wait(timeoutMillis) }
    }

    override fun finishInput(): Boolean {
        // A MODE_STREAM track may not start below its native minimum, including after an underrun.
        // At most one native buffer of trailing zeros is submitted, never added to logical source frames.
        paddingTargetBytes = maxOf(paddingTargetBytes, track.bufferSizeInFrames * 2L)
        while (paddedBytes < paddingTargetBytes) {
            val count = minOf(padding.size.toLong(), paddingTargetBytes - paddedBytes).toInt()
            val accepted = track.write(padding, 0, count, AudioTrack.WRITE_NON_BLOCKING)
            check(accepted >= 0 && accepted % 2 == 0) { "Audio output rejected tail padding" }
            if (accepted == 0) return false
            paddedBytes += accepted
        }
        return true
    }

    override fun play() {
        track.play()
    }

    override fun pause() {
        track.pause()
        signalProgress()
    }

    override fun stop() {
        track.stop()
        signalProgress()
    }

    override fun flush() {
        track.flush()
        signalProgress()
    }

    override fun close() {
        runCatching { track.stop() }
        track.release()
        signalProgress()
    }

    private fun signalProgress() { synchronized(progress) { progress.notifyAll() } }
}
