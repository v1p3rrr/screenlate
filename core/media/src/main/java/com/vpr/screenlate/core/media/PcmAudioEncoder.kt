package com.vpr.screenlate.core.media

import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

/** Owns native encoding/container resources. Every call uses a bounded native buffer wait. */
internal interface PcmEncoder : AutoCloseable {
    enum class Drain { NONE, PROGRESS, FINISHED }

    /** Null means unavailable; otherwise consumes an aligned prefix and queues EOS only if all final bytes fit. */
    fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int?
    fun drain(): Drain
}

/** Streams an exact kept PCM prefix, with bounded scratch space, cancellation and independent progress/deadline limits. */
internal object PcmAudioEncoder {
    private const val STALL_MILLIS = 10_000L
    private const val STARTUP_MILLIS = 30_000L
    private const val QUIET_AMPLITUDE = 0.02f

    fun encode(
        input: InputStream,
        frames: Long,
        sampleRate: Int,
        factory: () -> PcmEncoder,
        nowMillis: () -> Long,
        checkActive: () -> Unit = {},
    ): Boolean {
        require(frames > 0 && sampleRate > 0)
        val durationMillis = Math.addExact(Math.multiplyExact(frames, 1000L), sampleRate - 1L) / sampleRate
        val overallMillis = Math.addExact(STARTUP_MILLIS, Math.multiplyExact(durationMillis, 2L))
        val started = nowMillis()
        var lastProgress = started
        var submitted = 0L
        var inputDone = false
        var maxAmplitude = 0f
        val buffer = ByteArray(8192)
        var pending = 0
        var offset = 0
        fun checkpoint(): Long {
            checkActive()
            val now = nowMillis()
            if (now - started >= overallMillis) throw IOException("Audio export deadline exceeded")
            if (now - lastProgress >= STALL_MILLIS) throw IOException("Audio encoder made no progress")
            return now
        }
        checkActive()
        factory().use { encoder ->
            while (true) {
                checkpoint()
                if (!inputDone) {
                    if (offset == pending) {
                        pending = (minOf(buffer.size / 2L, frames - submitted) * 2).toInt()
                        input.readPcm16(buffer, pending) { checkpoint() }
                        offset = 0
                        for (i in 0 until pending step 2) {
                            val sample = ((buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)).toShort()
                            maxAmplitude = maxOf(maxAmplitude, abs(sample.toInt()) / 32768f)
                        }
                    }
                    val remaining = pending - offset
                    val final = submitted + remaining / 2 == frames
                    checkpoint()
                    val accepted = encoder.write(buffer, offset, remaining,
                        submitted * 1_000_000L / sampleRate, final)
                    val observed = checkpoint()
                    if (accepted != null) {
                        if (accepted !in 0..remaining || accepted % 2 != 0 || (remaining > 0 && accepted == 0)) {
                            throw IOException("Invalid PCM encoder input progress")
                        }
                        offset += accepted
                        submitted += accepted / 2
                        inputDone = final && offset == pending
                        lastProgress = observed
                    }
                }
                val drained = encoder.drain()
                val observed = checkpoint()
                when (drained) {
                    PcmEncoder.Drain.NONE -> Unit
                    PcmEncoder.Drain.PROGRESS -> lastProgress = observed
                    PcmEncoder.Drain.FINISHED -> {
                        if (!inputDone || submitted != frames) throw IOException("Audio encoder ended before all input")
                        checkActive()
                        return maxAmplitude < QUIET_AMPLITUDE
                    }
                }
            }
        }
    }
}
