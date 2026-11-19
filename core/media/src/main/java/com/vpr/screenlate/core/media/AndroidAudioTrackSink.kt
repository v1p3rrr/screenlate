package com.vpr.screenlate.core.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

/**
 * [AudioSink] backed by Android's [AudioTrack].
 */
class AndroidAudioTrackSink(sampleRate: Int = 48000) : AudioSink {

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

    override fun write(buffer: ByteArray, offset: Int, size: Int): Int {
        return track.write(buffer, offset, size)
    }

    override fun play() {
        track.play()
    }

    override fun pause() {
        track.pause()
    }

    override fun stop() {
        track.stop()
    }

    override fun flush() {
        track.flush()
    }

    override fun close() {
        runCatching { track.stop() }
        track.release()
    }
}
