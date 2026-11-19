package com.vpr.screenlate.audioprobe

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

internal class ProbeTone(private val frequency: Double) : AutoCloseable {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null
    private val released = AtomicBoolean(false)

    fun start() {
        check(worker == null && !released.get())
        val output = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(19200)
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        track = output
        running = true
        worker = thread(name = "probe-tone") {
            try {
                output.play()
                val buffer = ShortArray(4800)
                var position = 0L
                val deadline = SystemClock.elapsedRealtime() + MAX_DURATION_MILLIS
                while (running && SystemClock.elapsedRealtime() < deadline) {
                    for (i in buffer.indices) buffer[i] = (sin(2 * PI * frequency * position++ / RATE) * 6000).toInt().toShort()
                    var offset = 0
                    while (running && SystemClock.elapsedRealtime() < deadline && offset < buffer.size) {
                        val count = output.write(buffer, offset, buffer.size - offset)
                        if (count <= 0) return@thread
                        offset += count
                    }
                }
            } finally {
                running = false
                release(output)
            }
        }
    }

    override fun close() {
        running = false
        runCatching { track?.pause() }
        worker?.join(2000)
        track?.let(::release)
        track = null
        worker = null
    }

    private fun release(output: AudioTrack) {
        if (released.compareAndSet(false, true)) output.release()
    }

    private companion object {
        const val RATE = 48000
        const val MAX_DURATION_MILLIS = 30_000L
    }
}
