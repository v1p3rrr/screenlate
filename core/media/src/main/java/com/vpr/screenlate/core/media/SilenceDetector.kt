package com.vpr.screenlate.core.media

import java.io.IOException
import java.io.InputStream
import kotlin.math.sqrt

/**
 * Energy-based silence detector for sentence audio cuts and leading-quiet refinement.
 *
 * Rules from the binding plan:
 * - Silence action: minimum 1.5 s duration, preserves 150 ms at each edge. Proposes reversible cuts.
 *   Abstains if signal dynamic range is too narrow or audio is uniformly quiet / noisy.
 * - Initial leading quiet: detected only if the selection starts with > 5.0 s of silence followed by sound.
 */
internal object SilenceDetector {

    const val DEFAULT_MIN_SILENCE_MILLIS = 1500L
    const val DEFAULT_EDGE_PADDING_MILLIS = 150L
    const val LEADING_QUIET_THRESHOLD_MILLIS = 5000L
    private const val WINDOW_MILLIS = 50L
    private const val MAX_WINDOWS = 36_000 // 30 minutes of 50 ms windows.
    private const val MAX_READ_BYTES = 8192
    private const val MIN_SOUND_RMS = 0.015f
    private const val MIN_CONTRAST_RATIO = 3.0f
    private const val QUIET_THRESHOLD_RATIO = 2.0f

    /**
     * Finds proposed exclusion ranges (internal cuts) within [range] based on energy threshold.
     * Returns empty list if audio dynamic range cannot be separated reliably (abstention).
     */
    fun findSilenceCuts(
        stream: InputStream,
        range: AudioRange,
        sampleRate: Int,
        minSilenceMillis: Long = DEFAULT_MIN_SILENCE_MILLIS,
        edgePaddingMillis: Long = DEFAULT_EDGE_PADDING_MILLIS,
        checkActive: () -> Unit = {},
    ): List<AudioRange> {
        require(range.frames > 0 && sampleRate > 0)
        val windowFrames = windowFrames(sampleRate)
        val windows = computeWindowRms(stream, range.frames, windowFrames, checkActive)
        if (windows.isEmpty()) return emptyList()

        val threshold = separationThreshold(windows) ?: return emptyList()
        val minSilenceFrames = sampleRate * minSilenceMillis / 1000L
        val paddingFrames = sampleRate * edgePaddingMillis / 1000L

        val cuts = mutableListOf<AudioRange>()
        var silenceStart: Long? = null

        fun tryAddCut(start: Long, end: Long) {
            val silenceDuration = end - start
            if (silenceDuration >= minSilenceFrames) {
                val cutStart = start + paddingFrames
                val cutEnd = end - paddingFrames
                if (cutEnd > cutStart) {
                    cuts.add(AudioRange(range.start + cutStart, range.start + cutEnd))
                }
            }
        }

        for (index in windows.indices) {
            checkActive()
            val startFrame = index.toLong() * windowFrames
            if (windows[index] < threshold) {
                if (silenceStart == null) silenceStart = startFrame
            } else {
                silenceStart?.let { tryAddCut(it, startFrame) }
                silenceStart = null
            }
        }

        silenceStart?.let { tryAddCut(it, range.frames) }
        return cuts
    }

    /**
     * Detects leading quiet interval from the beginning of [range].
     * Returns an [AudioRange] starting at [range.start] up to the first detected sound,
     * or null if the start is not confidently quiet or does not meet the > 5 s threshold.
     */
    fun detectLeadingQuiet(
        stream: InputStream,
        range: AudioRange,
        sampleRate: Int,
        checkActive: () -> Unit = {},
    ): AudioRange? {
        require(range.frames > 0 && sampleRate > 0)
        val windowFrames = windowFrames(sampleRate)
        val windows = computeWindowRms(stream, range.frames, windowFrames, checkActive)
        if (windows.isEmpty()) return null

        val threshold = separationThreshold(windows) ?: return null
        val minFrames = sampleRate * LEADING_QUIET_THRESHOLD_MILLIS / 1000L
        if (windows[0] >= threshold) return null

        for (index in windows.indices) {
            checkActive()
            if (windows[index] >= threshold) {
                val startFrame = index.toLong() * windowFrames
                return if (startFrame > minFrames) AudioRange(range.start, range.start + startFrame) else null
            }
        }
        return null
    }

    /**
     * Requires sound to clear the quiet threshold by a margin; otherwise the threshold could classify the whole
     * selection as quiet. Primitive arrays avoid one object and one boxed sorting entry per 50 ms window.
     */
    private fun separationThreshold(windows: FloatArray): Float? {
        val sortedRms = windows.copyOf().also { it.sort() }
        val noiseFloor = sortedRms[(sortedRms.size * 0.15).toInt().coerceIn(0, sortedRms.size - 1)]
        val soundLevel = sortedRms[(sortedRms.size * 0.90).toInt().coerceIn(0, sortedRms.size - 1)]
        if (soundLevel < MIN_SOUND_RMS) return null
        if (noiseFloor > 0.0001f && soundLevel / noiseFloor < MIN_CONTRAST_RATIO) return null
        return maxOf(MIN_SOUND_RMS, noiseFloor * QUIET_THRESHOLD_RATIO)
    }

    private fun windowFrames(sampleRate: Int) = maxOf(1, sampleRate * WINDOW_MILLIS / 1000L).toInt()

    private fun computeWindowRms(
        stream: InputStream,
        totalFrames: Long,
        windowFrames: Int,
        checkActive: () -> Unit,
    ): FloatArray {
        val windowCount = 1 + (totalFrames - 1) / windowFrames
        if (windowCount > MAX_WINDOWS) throw IOException("Audio analysis budget reached")
        val result = FloatArray(windowCount.toInt())
        val buffer = ByteArray(minOf(windowFrames * 2, MAX_READ_BYTES))
        var frameOffset = 0L
        var windowIndex = 0
        var framesInWindow = 0
        var sumSquare = 0.0

        while (frameOffset < totalFrames) {
            val count = (minOf((buffer.size / 2).toLong(), totalFrames - frameOffset) * 2).toInt()
            stream.readPcm16(buffer, count, checkActive)
            for (i in 0 until count step 2) {
                val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()
                val norm = sample.toDouble() / 32768.0
                sumSquare += norm * norm
                framesInWindow++
                if (framesInWindow == windowFrames) {
                    result[windowIndex++] = sqrt(sumSquare / framesInWindow).toFloat()
                    framesInWindow = 0
                    sumSquare = 0.0
                }
            }
            frameOffset += count / 2
        }
        if (framesInWindow > 0) result[windowIndex] = sqrt(sumSquare / framesInWindow).toFloat()
        return result
    }
}
