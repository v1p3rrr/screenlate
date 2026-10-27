package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebpSizeTest {
    @Test
    fun aPictureThatFitsKeepsItsSize() {
        assertThat(webpSize(1080, 2400)).isEqualTo(1080 to 2400)
        assertThat(webpSize(WEBP_MAX_SIDE, WEBP_MAX_SIDE)).isEqualTo(WEBP_MAX_SIDE to WEBP_MAX_SIDE)
    }

    @Test
    fun aLongPictureIsScaledDownInProportion() {
        assertThat(webpSize(1080, 22000)).isEqualTo(804 to WEBP_MAX_SIDE)
        assertThat(webpSize(40000, 10)).isEqualTo(WEBP_MAX_SIDE to 4)
        assertThat(webpSize(WEBP_MAX_SIDE + 1, WEBP_MAX_SIDE + 1)).isEqualTo(WEBP_MAX_SIDE to WEBP_MAX_SIDE)
    }

    @Test
    fun aThinSideStaysAtLeastOnePixel() {
        assertThat(webpSize(100000, 1)).isEqualTo(WEBP_MAX_SIDE to 1)
    }
}
