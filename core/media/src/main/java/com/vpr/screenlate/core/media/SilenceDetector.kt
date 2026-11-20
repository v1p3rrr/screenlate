package com.vpr.screenlate.core.media

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
    ): List<AudioRange> {
        require(range.frames > 0 && sampleRate > 0)
        val windows = computeWindowRms(stream, range.frames, sampleRate)
        if (windows.isEmpty()) return emptyList()

        val sortedRms = windows.map { it.rms }.sorted()
        val noiseFloor = sortedRms[(sortedRms.size * 0.15).toInt().coerceIn(0, sortedRms.size - 1)]
        val speechPeak = sortedRms[(sortedRms.size * 0.90).toInt().coerceIn(0, sortedRms.size - 1)]

        // Abstention rule: entire selection is uniformly quiet, uniformly loud, or SNR is too low
        if (speechPeak < 0.015f || (noiseFloor > 0.0001f && speechPeak / noiseFloor < 2.0f)) {
            return emptyList()
        }

        val threshold = maxOf(0.015f, noiseFloor * 2.5f)
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

        for (w in windows) {
            val isSilent = w.rms < threshold
            if (isSilent) {
                if (silenceStart == null) silenceStart = w.startFrame
            } else {
                if (silenceStart != null) {
                    tryAddCut(silenceStart, w.startFrame)
                    silenceStart = null
                }
            }
        }

        // Check trailing silence run through the end of the range
        if (silenceStart != null) {
            tryAddCut(silenceStart, range.frames)
        }

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
    ): AudioRange? {
        require(range.frames > 0 && sampleRate > 0)
        val windows = computeWindowRms(stream, range.frames, sampleRate)
        if (windows.isEmpty()) return null

        val sortedRms = windows.map { it.rms }.sorted()
        val noiseFloor = sortedRms[(sortedRms.size * 0.15).toInt().coerceIn(0, sortedRms.size - 1)]
        val speechPeak = sortedRms[(sortedRms.size * 0.90).toInt().coerceIn(0, sortedRms.size - 1)]

        // Must have distinguishable non-silent sound later in the selection
        if (speechPeak < 0.015f || (noiseFloor > 0.0001f && speechPeak / noiseFloor < 2.0f)) {
            return null
        }

        val threshold = maxOf(0.015f, noiseFloor * 2.5f)
        val minFrames = sampleRate * LEADING_QUIET_THRESHOLD_MILLIS / 1000L

        // Selection must start with silence
        if (windows.first().rms >= threshold) return null

        for (w in windows) {
            if (w.rms >= threshold) {
                // First non-silent sound found at w.startFrame
                return if (w.startFrame > minFrames) {
                    AudioRange(range.start, range.start + w.startFrame)
                } else {
                    null
                }
            }
        }
        // Entire audio was quiet - abstain per spec (don't trim everything)
        return null
    }

    private data class WindowInfo(val startFrame: Long, val endFrame: Long, val rms: Float)

    private fun computeWindowRms(stream: InputStream, totalFrames: Long, sampleRate: Int): List<WindowInfo> {
        val windowFrames = maxOf(1, sampleRate * WINDOW_MILLIS / 1000L).toInt()
        val buffer = ByteArray(windowFrames * 2)
        val result = mutableListOf<WindowInfo>()
        var frameOffset = 0L

        while (frameOffset < totalFrames) {
            val toRead = minOf(buffer.size.toLong(), (totalFrames - frameOffset) * 2).toInt()
            val read = stream.read(buffer, 0, toRead)
            if (read <= 0) break
            val frames = read / 2

            var sumSquare = 0.0
            for (i in 0 until frames) {
                val sample = ((buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)).toShort()
                val norm = sample.toDouble() / 32768.0
                sumSquare += norm * norm
            }
            val rms = sqrt(sumSquare / frames).toFloat()
            result.add(WindowInfo(frameOffset, frameOffset + frames, rms))
            frameOffset += frames
        }
        return result
    }
}
