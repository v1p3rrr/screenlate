package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException

class WaveformSummaryTest {

    @Test(expected = EOFException::class)
    fun truncatedSourceIsNotRenderedAsInventedQuietTail() {
        WaveformSummary.compute(ByteArrayInputStream(byteArrayOf(0, 64, 0)), AudioRange(0, 2), 2)
    }

    @Test
    fun shortOddReadsKeepEverySampleAndItsPolarity() {
        val bytes = byteArrayOf(0, 0, 0, 64, 0, -128, 0, 32)
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(1, length))
        }
        val summary = WaveformSummary.compute(input, AudioRange(100, 104), 4)
        assertThat(summary.peaks).usingTolerance(0.0001).containsExactly(0.0, 0.5, 1.0, 0.25).inOrder()
    }

    @Test
    fun computeCreatesCorrectNumberOfBucketsWithPeaks() {
        val frames = 1000
        val pcm = ByteArray(frames * 2)

        // First 500 frames: silence
        // Next 250 frames: amplitude 16384 (~0.5)
        for (i in 500 until 750) {
            val sample = 16384
            pcm[i * 2] = (sample and 0xFF).toByte()
            pcm[i * 2 + 1] = (sample shr 8).toByte()
        }
        // Next 250 frames: amplitude 32767 (~1.0)
        for (i in 750 until 1000) {
            val sample = 32767
            pcm[i * 2] = (sample and 0xFF).toByte()
            pcm[i * 2 + 1] = (sample shr 8).toByte()
        }

        val summary = WaveformSummary.compute(
            ByteArrayInputStream(pcm),
            AudioRange(0, 1000),
            targetBuckets = 10,
        )

        assertThat(summary.buckets).isEqualTo(10)
        assertThat(summary.peaks.size).isEqualTo(10)

        // Buckets 0..4 (frames 0..500) should be 0.0f
        for (b in 0..4) {
            assertThat(summary.peaks[b]).isEqualTo(0.0f)
        }

        // Buckets 5..6 (frames 500..700) should be ~0.5f
        for (b in 5..6) {
            assertThat(summary.peaks[b]).isWithin(0.02f).of(0.5f)
        }

        // Buckets 8..9 (frames 800..1000) should be ~1.0f
        for (b in 8..9) {
            assertThat(summary.peaks[b]).isWithin(0.02f).of(1.0f)
        }
    }

    @Test
    fun targetBucketsLargerThanFramesClampsToFrameCount() {
        val frames = 5
        val pcm = ByteArray(frames * 2)
        val summary = WaveformSummary.compute(
            ByteArrayInputStream(pcm),
            AudioRange(0, 5),
            targetBuckets = 100,
        )
        assertThat(summary.buckets).isEqualTo(5)
        assertThat(summary.peaks.size).isEqualTo(5)
    }
}
