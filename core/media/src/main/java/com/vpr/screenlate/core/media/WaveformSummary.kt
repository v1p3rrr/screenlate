package com.vpr.screenlate.core.media

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Downsampled amplitude envelope for waveform visualization.
 * [peaks] contains normalized peak amplitude (0.0f .. 1.0f) for each bucket.
 */
data class WaveformSummary(
    val range: AudioRange,
    val buckets: Int,
    val peaks: FloatArray,
) {
    override fun equals(other: Any?): Boolean = other is WaveformSummary &&
        range == other.range && buckets == other.buckets && peaks.contentEquals(other.peaks)

    override fun hashCode(): Int = 31 * (31 * range.hashCode() + buckets) + peaks.contentHashCode()

    companion object {
        fun compute(stream: InputStream, range: AudioRange, targetBuckets: Int): WaveformSummary {
            require(range.frames > 0 && targetBuckets > 0)
            val buckets = minOf(targetBuckets.toLong(), range.frames).toInt()
            val peaks = FloatArray(buckets)
            val framesPerBucket = range.frames.toDouble() / buckets

            val buffer = ByteArray(4096)
            var frameIndex = 0L
            var currentBucket = 0
            var currentPeak = 0f

            while (frameIndex < range.frames) {
                val toRead = minOf(buffer.size.toLong(), (range.frames - frameIndex) * 2).toInt()
                val read = stream.read(buffer, 0, toRead)
                if (read <= 0) break
                val frames = read / 2
                for (i in 0 until frames) {
                    val bucketForFrame = (frameIndex.toDouble() / framesPerBucket).toInt().coerceAtMost(buckets - 1)
                    if (bucketForFrame != currentBucket) {
                        peaks[currentBucket] = currentPeak
                        currentBucket = bucketForFrame
                        currentPeak = 0f
                    }
                    val sample = ((buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)).toShort()
                    val absNorm = abs(sample.toInt()) / 32768f
                    currentPeak = max(currentPeak, absNorm)
                    frameIndex++
                }
            }
            if (currentBucket < buckets) {
                peaks[currentBucket] = currentPeak
            }
            return WaveformSummary(range, buckets, peaks)
        }
    }
}
