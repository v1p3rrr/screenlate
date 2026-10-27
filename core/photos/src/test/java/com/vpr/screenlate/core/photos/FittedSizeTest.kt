package com.vpr.screenlate.core.photos

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

class FittedSizeTest {
    @Test
    fun aSizeWithinTheLimitsStaysAsItIs() {
        assertThat(fittedSize(1000, 800, maxPixels = 4_000_000)).isEqualTo(1000 to 800)
        assertThat(fittedSize(1500, 1500, maxSide = 1500)).isEqualTo(1500 to 1500)
    }

    @Test
    fun aLargePhotoIsScaledInProportionToAboutThePixelLimit() {
        val (width, height) = fittedSize(8160, 6120, maxPixels = 4_000_000)
        assertThat(width.toLong() * height).isIn(Range.closed(3_990_000L, 4_010_000L))
        assertThat(abs(width * 6120L - height * 8160L)).isAtMost(8160L)
    }

    @Test
    fun theLongestSideIsScaledToTheSideLimit() {
        assertThat(fittedSize(4000, 3000, maxSide = 1500)).isEqualTo(1500 to 1125)
        assertThat(fittedSize(1000, 30_000, maxSide = 1500)).isEqualTo(50 to 1500)
    }

    @Test
    fun theDecoderSamplesByTheLargestPowerOfTwoThatKeepsTheTarget() {
        assertThat(decoderSample(1000, 800, 1000 to 800)).isEqualTo(1)
        assertThat(decoderSample(8160, 6120, 2309 to 1732)).isEqualTo(2)
        assertThat(decoderSample(6000, 4000, 1500 to 1000)).isEqualTo(4)
        assertThat(decoderSample(6000, 4000, 1501 to 1000)).isEqualTo(2)
    }
}
