package com.vpr.screenlate.core.media

/** One session's frame-zero time in the BOOTTIME clock, recalibrated across device suspend gaps. */
internal class PcmClock(val sampleRate: Int, originNanos: Long) {
    init { require(sampleRate > 0 && originNanos >= 0) }

    private data class Anchor(val frame: Long, val nanos: Long)

    @Volatile private var anchor = Anchor(0L, originNanos)

    fun frameAtNanos(nanos: Long): Long {
        require(nanos >= 0)
        val current = anchor
        if (nanos <= current.nanos) return current.frame
        val elapsed = nanos - current.nanos
        return Math.addExact(
            current.frame,
            Math.addExact(
                Math.multiplyExact(elapsed / NANOS_PER_SECOND, sampleRate.toLong()),
                elapsed % NANOS_PER_SECOND * sampleRate / NANOS_PER_SECOND,
            ),
        )
    }

    fun frameAtMillis(millis: Long): Long = frameAtNanos(Math.multiplyExact(millis, 1_000_000))

    fun framesForMillis(millis: Long): Long {
        require(millis >= 0)
        return Math.addExact(Math.multiplyExact(millis / 1000, sampleRate.toLong()), millis % 1000 * sampleRate / 1000)
    }

    fun recalibrate(frame: Long, nanos: Long) {
        val current = anchor
        if (frame >= current.frame && nanos > current.nanos) {
            anchor = Anchor(frame, nanos)
        }
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        fun nanosForFrames(frames: Long, sampleRate: Int): Long {
            require(frames >= 0 && sampleRate > 0)
            return Math.addExact(
                Math.multiplyExact(frames / sampleRate, NANOS_PER_SECOND),
                (frames % sampleRate) * NANOS_PER_SECOND / sampleRate,
            )
        }
    }
}
