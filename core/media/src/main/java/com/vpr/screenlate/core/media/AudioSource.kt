package com.vpr.screenlate.core.media

/**
 * Audio input provider for the capture worker.
 * Concrete implementation wraps [android.media.AudioRecord]; fakes provide synthetic frames in tests.
 */
interface AudioSource : AutoCloseable {
    val sampleRate: Int
    val channelCount: Int

    /** Starts capture. Returns false if the hardware/session could not be started. */
    fun start(): Boolean

    /**
     * Reads up to [length] bytes of mono PCM16 into [buffer] starting at [offset].
     * Returns the number of bytes read, or a negative value on read error / stream end.
     */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /**
     * Returns the monotonic timestamp in nanoseconds for the most recent audio read,
     * or null if timestamps are not supported or not yet calibrated.
     */
    fun timestampNanos(): Long?

    /** Stops audio capture. */
    fun stop()
}
