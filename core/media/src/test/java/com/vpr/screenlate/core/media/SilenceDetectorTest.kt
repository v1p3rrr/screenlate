package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.PI
import kotlin.math.sin

class SilenceDetectorTest {

    private val sampleRate = 1000 // 1000 frames per second for easy arithmetic

    @Test
    fun detectLeadingQuietFindsQuietIntervalWhenLongerThanFiveSeconds() {
        // 6 seconds silence + 2 seconds sound (440 Hz at ~0.5 amplitude)
        val silenceFrames = 6000
        val soundFrames = 2000
        val totalFrames = silenceFrames + soundFrames
        val pcm = createTonePcm(totalFrames, soundStart = silenceFrames, soundEnd = totalFrames)

        val quiet = SilenceDetector.detectLeadingQuiet(
            ByteArrayInputStream(pcm),
            AudioRange(0, totalFrames.toLong()),
            sampleRate,
        )

        assertThat(quiet).isNotNull()
        assertThat(quiet!!.start).isEqualTo(0L)
        // Leading quiet should end near 6000 frames (within window resolution of 50 ms = 50 frames)
        assertThat(quiet.endExclusive).isAtLeast(5950L)
        assertThat(quiet.endExclusive).isAtMost(6050L)

        // Refine with InitialSelection
        val initial = AudioRange(0, totalFrames.toLong())
        val refined = InitialSelection.refine(initial, quiet, sampleRate)
        // Refined start should be quiet.endExclusive - 150 ms (150 frames)
        assertThat(refined.start).isWithin(50L).of(5850L)
        assertThat(refined.endExclusive).isEqualTo(totalFrames.toLong())
    }

    @Test
    fun detectLeadingQuietAbstainsWhenShorterThanFiveSeconds() {
        // 4 seconds silence + 4 seconds sound
        val silenceFrames = 4000
        val soundFrames = 4000
        val totalFrames = silenceFrames + soundFrames
        val pcm = createTonePcm(totalFrames, soundStart = silenceFrames, soundEnd = totalFrames)

        val quiet = SilenceDetector.detectLeadingQuiet(
            ByteArrayInputStream(pcm),
            AudioRange(0, totalFrames.toLong()),
            sampleRate,
        )

        assertThat(quiet).isNull()
    }

    @Test
    fun detectLeadingQuietAbstainsWhenEntireAudioIsSilent() {
        val totalFrames = 10000
        val pcm = ByteArray(totalFrames * 2) // all zeros

        val quiet = SilenceDetector.detectLeadingQuiet(
            ByteArrayInputStream(pcm),
            AudioRange(0, totalFrames.toLong()),
            sampleRate,
        )

        assertThat(quiet).isNull()
    }

    @Test
    fun findSilenceCutsIdentifiesQuietIntervalsWithPadding() {
        // 2s sound + 2s silence + 2s sound
        // Silence is at 2000..4000 (2.0s duration >= 1.5s min)
        val totalFrames = 6000
        val pcm = ByteArray(totalFrames * 2)

        // Write sound in 0..2000 and 4000..6000
        writeTone(pcm, 0, 2000)
        writeTone(pcm, 4000, 6000)

        val cuts = SilenceDetector.findSilenceCuts(
            ByteArrayInputStream(pcm),
            AudioRange(100, 100 + totalFrames.toLong()), // With an offset to check range arithmetic
            sampleRate,
            minSilenceMillis = 1500,
            edgePaddingMillis = 150,
        )

        assertThat(cuts.size).isEqualTo(1)
        val cut = cuts[0]
        // Cut start should be 100 + 2000 + 150 frames = 2250 (within 50 frame window)
        assertThat(cut.start).isWithin(50L).of(2250L)
        // Cut end should be 100 + 4000 - 150 frames = 3950 (within 50 frame window)
        assertThat(cut.endExclusive).isWithin(50L).of(3950L)
    }

    @Test
    fun findSilenceCutsIgnoresShortSilenceBelowMinimum() {
        // 2s sound + 1s silence (< 1.5s) + 2s sound
        val totalFrames = 5000
        val pcm = ByteArray(totalFrames * 2)
        writeTone(pcm, 0, 2000)
        writeTone(pcm, 3000, 5000)

        val cuts = SilenceDetector.findSilenceCuts(
            ByteArrayInputStream(pcm),
            AudioRange(0, totalFrames.toLong()),
            sampleRate,
            minSilenceMillis = 1500,
            edgePaddingMillis = 150,
        )

        assertThat(cuts).isEmpty()
    }

    private fun createTonePcm(totalFrames: Int, soundStart: Int, soundEnd: Int): ByteArray {
        val pcm = ByteArray(totalFrames * 2)
        writeTone(pcm, soundStart, soundEnd)
        return pcm
    }

    private fun writeTone(pcm: ByteArray, start: Int, end: Int) {
        for (i in start until end) {
            val sample = (sin(2 * PI * 10 * i / sampleRate) * 16000).toInt()
            pcm[i * 2] = (sample and 0xFF).toByte()
            pcm[i * 2 + 1] = (sample shr 8).toByte()
        }
    }
}
