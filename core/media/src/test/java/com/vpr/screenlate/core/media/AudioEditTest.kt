package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioEditTest {
    @Test fun mergesClipsAndIgnoresEmptyOrOutsideExclusions() {
        val edit = AudioEdit(AudioRange(100, 1000), listOf(
            AudioRange(0, 150), AudioRange(140, 200), AudioRange(200, 250),
            AudioRange(400, 500), AudioRange(950, 2000), AudioRange(700, 700),
        ))
        assertThat(edit.exclusions).containsExactly(AudioRange(100, 250), AudioRange(400, 500), AudioRange(950, 1000)).inOrder()
        assertThat(edit.kept).containsExactly(AudioRange(250, 400), AudioRange(500, 950)).inOrder()
        assertThat(edit.outputFrames).isEqualTo(600)
    }

    @Test fun mapsSourceAndResultAcrossCutsAndEnd() {
        val edit = AudioEdit(AudioRange(100, 1000), listOf(AudioRange(200, 400), AudioRange(700, 1000)))
        assertThat(edit.sourceFrame(0)).isEqualTo(100)
        assertThat(edit.sourceFrame(99)).isEqualTo(199)
        assertThat(edit.sourceFrame(100)).isEqualTo(400)
        assertThat(edit.sourceFrame(399)).isEqualTo(699)
        assertThat(edit.sourceFrame(400)).isEqualTo(1000)
        assertThat(edit.resultOffset(300)).isEqualTo(100)
        assertThat(edit.resultOffset(700)).isEqualTo(400)
        assertThat(edit.resultOffset(500)).isEqualTo(200)
        assertThrows(IllegalArgumentException::class.java) { edit.sourceFrame(401) }
    }

    @Test fun fullCutIsAnEmptyResultWithoutAnInventedFrame() {
        val edit = AudioEdit(AudioRange(10, 20), listOf(AudioRange(0, 30)))
        assertThat(edit.outputFrames).isEqualTo(0)
        assertThat(edit.kept).isEmpty()
        assertThat(edit.sourceFrame(0)).isEqualTo(20)
    }

    @Test fun usesLongFramesAndDefensivelyCopiesCuts() {
        val start = Int.MAX_VALUE.toLong() + 10
        val cuts = mutableListOf(AudioRange(start + 20, start + 30))
        val edit = AudioEdit(AudioRange(start, start + 100), cuts)
        cuts.clear()
        assertThat(edit.outputFrames).isEqualTo(90)
        assertThat(edit.sourceFrame(20)).isEqualTo(start + 30)
        assertThat(edit).isEqualTo(AudioEdit(edit.selection, listOf(AudioRange(start + 20, start + 30))))
    }

    @Test fun everyKeptFrameRoundTripsForSeveralCuts() {
        val edit = AudioEdit(AudioRange(10, 1000), listOf(AudioRange(11, 40), AudioRange(65, 80), AudioRange(200, 900)))
        for (position in 0 until edit.outputFrames) {
            assertThat(edit.resultOffset(edit.sourceFrame(position))).isEqualTo(position)
        }
    }
}
