package com.vpr.screenlate.core.media

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Concrete [AudioSource] wrapping [AudioRecord] configured with playback capture on API 29+.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class AudioRecordSource(
    private val record: AudioRecord,
) : AudioSource {
    override val sampleRate: Int get() = record.sampleRate
    override val channelCount: Int get() = record.channelCount

    private val timestamp = AudioTimestamp()
    private var calibratedOriginNanos: Long? = null

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

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val result = record.read(buffer, offset, length, AudioRecord.READ_BLOCKING)
        if (result > 0 && calibratedOriginNanos == null) {
            updateTimestamp()
        }
        return result
    }

    override fun timestampNanos(): Long? {
        updateTimestamp()
        return calibratedOriginNanos
    }

    private fun updateTimestamp() {
        if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS) {
            val frameNanos = timestamp.framePosition * 1_000_000_000L / sampleRate
            calibratedOriginNanos = timestamp.nanoTime - frameNanos
        }
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
}
