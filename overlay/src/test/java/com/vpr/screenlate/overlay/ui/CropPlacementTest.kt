package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class CropPlacementTest {
    @Test
    fun largePhotosFitAboveControlsAndRoundTripOriginalCoordinates() {
        for ((width, height) in listOf(8000f to 6000f, 6000f to 8000f, 300f to 8000f)) {
            val source = Box(0f, 0f, width, height)
            val area = Box(16f, 64f, 1064f, 1700f)
            val placement = CropPlacement.of(source, area, true)
            assertThat(placement.shown.left).isAtLeast(area.left)
            assertThat(placement.shown.top).isAtLeast(area.top)
            assertThat(placement.shown.right).isAtMost(area.right)
            assertThat(placement.shown.bottom).isAtMost(area.bottom)
            val crop = Box(width / 4, height / 4, width * 0.75f, height * 0.75f)
            val restored = placement.toSource(placement.toScreen(crop))
            assertThat(restored.left).isWithin(0.001f).of(crop.left)
            assertThat(restored.bottom).isWithin(0.001f).of(crop.bottom)
        }
    }

    @Test
    fun screenshotsKeepTheirOriginalScreenOffsetAndScale() {
        val source = Box(10f, 100f, 1090f, 2200f)
        val placement = CropPlacement.of(source, Box(16f, 80f, 1064f, 1700f), false)
        assertThat(placement.shown).isEqualTo(source)
        val crop = Box(100f, 200f, 500f, 700f)
        assertThat(placement.toSource(crop)).isEqualTo(crop)
    }
}
