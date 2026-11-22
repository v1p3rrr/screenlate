package com.vpr.screenlate.core.media

import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

/**
 * Append-only amplitude pyramid in source frames. Serialize access on a worker.
 * Base buckets cover 5ms at 48kHz; coarser levels answer viewport queries without reopening PCM.
 */
internal class WaveformIndex(
    val startFrame: Long,
    private val bucketFrames: Int = 240,
    private val maxBuckets: Int = 360_000,
) {
    private val levels = mutableListOf<FloatArray>()
    private val sizes = mutableListOf<Int>()
    private var partialFrames = 0
    private var partialPeak = 0f
    var endFrame = startFrame
        private set

    val allocatedBytes: Long get() = levels.sumOf { it.size * 4L }

    init { require(startFrame >= 0 && bucketFrames > 0 && maxBuckets > 0) }

    fun append(input: InputStream, range: AudioRange, checkActive: () -> Unit = {}) {
        require(range.start == endFrame) { "Waveform input is not continuous" }
        if (range.endExclusive - startFrame > maxBuckets.toLong() * bucketFrames) {
            throw IOException("Waveform cache budget reached")
        }
        val buffer = ByteArray(8192)
        while (endFrame < range.endExclusive) {
            val count = (minOf(buffer.size / 2L, range.endExclusive - endFrame) * 2).toInt()
            input.readPcm16(buffer, count, checkActive)
            for (i in 0 until count step 2) {
                val sample = ((buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)).toShort()
                partialPeak = maxOf(partialPeak, abs(sample.toInt()) / 32768f)
                partialFrames++
                endFrame++
                if (partialFrames == bucketFrames) {
                    appendPeak(partialPeak)
                    partialFrames = 0
                    partialPeak = 0f
                }
            }
        }
    }

    fun summary(range: AudioRange, targetBuckets: Int): WaveformSummary {
        require(range.start >= startFrame && range.endExclusive <= endFrame && range.frames > 0)
        require(targetBuckets in 1..2048)
        val count = minOf(targetBuckets.toLong(), range.frames).toInt()
        val complete = sizes.firstOrNull() ?: 0
        val peaks = FloatArray(count) { i ->
            val first = range.start - startFrame + range.frames * i / count
            val end = range.start - startFrame + range.frames * (i + 1) / count
            val lo = (first / bucketFrames).toInt()
            val hi = ((end + bucketFrames - 1) / bucketFrames).toInt()
            var peak = peak(lo.coerceAtMost(complete), hi.coerceAtMost(complete))
            if (partialFrames > 0 && lo <= complete && hi > complete) peak = maxOf(peak, partialPeak)
            peak
        }
        return WaveformSummary(range, count, peaks)
    }

    private fun appendPeak(value: Float) {
        var level = 0
        var peak = value
        while (true) {
            if (level == levels.size) {
                levels += FloatArray(minOf(256, maxBuckets shr level))
                sizes += 0
            }
            val index = sizes[level]
            if (index == levels[level].size) {
                levels[level] = levels[level].copyOf(minOf(maxBuckets shr level, maxOf(index + 1, index * 2)))
            }
            levels[level][index] = peak
            sizes[level]++
            if (sizes[level] % 2 != 0) return
            peak = maxOf(levels[level][index - 1], peak)
            level++
        }
    }

    private fun peak(from: Int, end: Int): Float {
        var cursor = from
        var peak = 0f
        while (cursor < end) {
            val remainingLevel = 31 - Integer.numberOfLeadingZeros(end - cursor)
            val alignedLevel = if (cursor == 0) remainingLevel else Integer.numberOfTrailingZeros(cursor)
            val level = minOf(remainingLevel, alignedLevel)
            peak = maxOf(peak, levels[level][cursor shr level])
            cursor += 1 shl level
        }
        return peak
    }
}
