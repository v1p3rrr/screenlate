package com.vpr.screenlate.core.media

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi

/**
 * Concrete [AudioSource] wrapping [AudioRecord] configured with playback capture on API 29+.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AudioRecordSource internal constructor(
    private val record: AudioRecord,
) : AudioSource {
    override val sampleRate: Int get() = record.sampleRate
    override val channelCount: Int get() = record.channelCount

    private val timestamp = AudioTimestamp()

    @SuppressLint("MissingPermission")
    override fun start(): Boolean {
        if (record.state != AudioRecord.STATE_INITIALIZED) return false
        return try {
            record.startRecording()
            record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (_: Exception) {
            false
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        record.read(buffer, offset, length, AudioRecord.READ_BLOCKING)

    override fun timestampNanos(): Long? {
        if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS) {
            val frameNanos = timestamp.framePosition * 1_000_000_000L / sampleRate
            return timestamp.nanoTime - frameNanos
        }
        return null
    }

    override fun stop() {
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
    }

    override fun close() {
        stop()
        record.release()
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun create(projection: MediaProjection, sampleRate: Int = 48000): AudioRecordSource? {
            return try {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .excludeUid(Process.myUid())
                    .build()
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuffer <= 0) return null
                val record = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setSampleRate(sampleRate)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, sampleRate / 5 * 2))
                    .build()
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return null
                }
                AudioRecordSource(record)
            } catch (_: Exception) {
                null
            }
        }
    }
}
