package com.vpr.screenlate.core.media

/** One session's frame-zero time in the BOOTTIME clock, calibrated by the capture backend. */
internal class PcmClock(val sampleRate: Int, private val originNanos: Long) {
    init { require(sampleRate > 0 && originNanos >= 0) }

    fun frameAtNanos(nanos: Long): Long {
        require(nanos >= 0)
        if (nanos <= originNanos) return 0
        val elapsed = nanos - originNanos
        return Math.addExact(Math.multiplyExact(elapsed / NANOS_PER_SECOND, sampleRate.toLong()),
            elapsed % NANOS_PER_SECOND * sampleRate / NANOS_PER_SECOND)
    }

    fun frameAtMillis(millis: Long): Long = frameAtNanos(Math.multiplyExact(millis, 1_000_000))

    fun framesForMillis(millis: Long): Long {
        require(millis >= 0)
        return Math.addExact(Math.multiplyExact(millis / 1000, sampleRate.toLong()), millis % 1000 * sampleRate / 1000)
    }

    private companion object { const val NANOS_PER_SECOND = 1_000_000_000L }
}
