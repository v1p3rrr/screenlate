package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PcmClockTest {
    @Test fun mapsFramesWithoutFloatingPointOrMultiplicationOverflow() {
        val clock = PcmClock(48_000, 10_000_000_000)
        assertThat(clock.frameAtNanos(9_000_000_000)).isEqualTo(0)
        assertThat(clock.frameAtNanos(10_000_000_000)).isEqualTo(0)
        assertThat(clock.frameAtNanos(10_000_020_834)).isEqualTo(1)
        assertThat(clock.frameAtMillis(11_000)).isEqualTo(48_000)
        assertThat(clock.frameAtNanos(10_000_000_000 + 100_000L * 1_000_000_000)).isEqualTo(4_800_000_000L)
    }

    @Test fun keepsFractionalRateAndDurationRemainders() {
        val clock = PcmClock(44_100, 0)
        assertThat(clock.frameAtMillis(150)).isEqualTo(6615)
        assertThat(clock.framesForMillis(30_000)).isEqualTo(1_323_000)
        assertThat(clock.framesForMillis(150)).isEqualTo(6615)
    }
}
