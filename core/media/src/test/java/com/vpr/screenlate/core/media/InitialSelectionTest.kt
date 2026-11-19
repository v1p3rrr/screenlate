package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InitialSelectionTest {
    @Test fun suggestsFifteenBeforeAndFiveAfterWithoutPaddingUnavailableAudio() {
        assertThat(InitialSelection.suggest(100_000, AudioRange(0, 200_000), 1000)).isEqualTo(AudioRange(85_000, 105_000))
        assertThat(InitialSelection.suggest(10_000, AudioRange(5000, 12_000), 1000)).isEqualTo(AudioRange(5000, 12_000))
        assertThat(InitialSelection.suggest(100_000, AudioRange(0, 30_000), 1000)).isNull()
    }

    @Test fun trimsOnlyMoreThanFiveSecondsAndLeavesPadding() {
        val selection = AudioRange(85_000, 105_000)
        assertThat(InitialSelection.refine(selection, AudioRange(85_000, 91_000), 1000)).isEqualTo(AudioRange(90_850, 105_000))
        assertThat(InitialSelection.refine(selection, AudioRange(85_000, 90_000), 1000)).isEqualTo(selection)
        assertThat(InitialSelection.refine(selection, AudioRange(85_000, 89_000), 1000)).isEqualTo(selection)
    }

    @Test fun doesNotTrimAllQuietUncertainOrNonLeadingRanges() {
        val selection = AudioRange(85_000, 105_000)
        assertThat(InitialSelection.refine(selection, selection, 1000)).isEqualTo(selection)
        assertThat(InitialSelection.refine(selection, null, 1000)).isEqualTo(selection)
        assertThat(InitialSelection.refine(selection, AudioRange(86_000, 100_000), 1000)).isEqualTo(selection)
    }
}
