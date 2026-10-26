package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PhotoViewportTest {

    @org.junit.Test
    fun zoomPreservesTheFocalPointAndIsBoundedAtFitAndEightTimesFit() {
        val fit = PhotoViewport.fit(4000, 3000, com.vpr.screenlate.core.common.geometry.Box(20f, 100f, 1020f, 1900f))
        val center = fit.toPhoto(520f, 1000f)!!
        val zoomed = fit.transformed(520f, 1000f, 0f, 0f, 3f)
        assertThat(zoomed.toPhoto(520f, 1000f)).isEqualTo(center)
        val maximum = zoomed.transformed(520f, 1000f, 0f, 0f, 100f)
        assertThat(maximum.scale).isEqualTo(fit.scale * 8)
        val minimum = maximum.transformed(520f, 1000f, 0f, 0f, 0.001f)
        assertThat(minimum).isEqualTo(fit)
    }

    @org.junit.Test
    fun panCannotExposeBeyondThePhotoAndSmallAxesStayCentered() {
        val fit = PhotoViewport.fit(4000, 1000, com.vpr.screenlate.core.common.geometry.Box(20f, 100f, 1020f, 1900f))
        val moved = fit.transformed(520f, 1000f, 100000f, -100000f, 2f)
        assertThat(moved.offsetX).isEqualTo(20f)
        assertThat(moved.offsetY).isEqualTo(750f)
        val other = moved.transformed(520f, 1000f, -100000f, 100000f, 1f)
        assertThat(other.offsetX).isEqualTo(-980f)
        assertThat(other.offsetY).isEqualTo(750f)
    }
    /** A 4000 × 3000 photo in the part of a 1080 × 2400 screen between a top bar and the navigation. */
    private val area = Box(0f, 200f, 1080f, 2200f)

    @Test
    fun `a fitted photo is centered at the largest scale that shows all of it`() {
        val viewport = PhotoViewport.fit(4000, 3000, area)
        assertThat(viewport.scale).isEqualTo(0.27f)
        assertNear(viewport.visible!!, Box(0f, 795f, 1080f, 1605f))
        // A tall photo on a wide area is centered across.
        val tall = PhotoViewport.fit(1000, 4000, Box(0f, 0f, 1000f, 1000f))
        assertThat(tall.visible).isEqualTo(Box(375f, 0f, 625f, 1000f))
    }

    @Test
    fun `screen points and photo boxes round-trip through pan and zoom`() {
        for (viewport in viewports()) {
            val word = Box(1200f, 1500f, 1260f, 1560f)
            val onScreen = viewport.toScreen(word)
            val (x, y) = viewport.toPhoto(onScreen.centerX, onScreen.centerY)!!
            assertThat(x).isWithin(0.01f).of(word.centerX)
            assertThat(y).isWithin(0.01f).of(word.centerY)
        }
    }

    @Test
    fun `no photo point outside the shown photo`() {
        val viewport = PhotoViewport.fit(4000, 3000, area)
        // The letterbox above the photo, the top bar and a point beside the screen.
        assertThat(viewport.toPhoto(540f, 600f)).isNull()
        assertThat(viewport.toPhoto(540f, 100f)).isNull()
        assertThat(viewport.toPhoto(-1f, 1000f)).isNull()
        assertThat(viewport.toPhoto(540f, 1200f)).isNotNull()
    }

    @Test
    fun `zoomed in, the photo outside the area does not count`() {
        // Scale 1 from the photo's top left at the area's top left: most of the photo is off the screen.
        val viewport = PhotoViewport(1f, 0f, 200f, 4000, 3000, area)
        assertThat(viewport.visible).isEqualTo(area)
        assertThat(viewport.toPhoto(500f, 2300f)).isNull()
        assertThat(viewport.toVisibleScreen(Box(1000f, 100f, 1200f, 200f))).isEqualTo(Box(1000f, 300f, 1080f, 400f))
        assertThat(viewport.toVisibleScreen(Box(2000f, 100f, 2100f, 200f))).isNull()
    }

    @Test
    fun `a photo moved out of the area shows nothing`() {
        val viewport = PhotoViewport(0.5f, 2000f, 200f, 4000, 3000, area)
        assertThat(viewport.visible).isNull()
        assertThat(viewport.toPhoto(1000f, 1000f)).isNull()
        assertThat(viewport.toVisibleScreen(Box(0f, 0f, 10f, 10f))).isNull()
    }

    @Test
    fun `a screen tolerance grows in photo pixels as the photo shrinks`() {
        assertThat(PhotoViewport.fit(4000, 3000, area).toPhotoDistance(36f)).isWithin(0.01f).of(133.33f)
        assertThat(PhotoViewport(3f, 0f, 0f, 4000, 3000, area).toPhotoDistance(36f)).isEqualTo(12f)
    }

    @Test
    fun `a rotated screen fits the same photo again`() {
        val portrait = PhotoViewport.fit(4000, 3000, area)
        val landscape = PhotoViewport.fit(4000, 3000, Box(0f, 0f, 2400f, 1080f))
        val word = Box(2000f, 1500f, 2100f, 1600f)
        for (viewport in listOf(portrait, landscape)) {
            val onScreen = viewport.toVisibleScreen(word)!!
            assertThat(viewport.toPhoto(onScreen.centerX, onScreen.centerY)!!.first).isWithin(0.01f).of(word.centerX)
        }
    }

    private fun assertNear(actual: Box, expected: Box) {
        assertThat(actual.left).isWithin(0.01f).of(expected.left)
        assertThat(actual.top).isWithin(0.01f).of(expected.top)
        assertThat(actual.right).isWithin(0.01f).of(expected.right)
        assertThat(actual.bottom).isWithin(0.01f).of(expected.bottom)
    }

    private fun viewports() = listOf(
        PhotoViewport.fit(4000, 3000, area),
        PhotoViewport(1f, -800f, -900f, 4000, 3000, area),
        PhotoViewport(2.5f, -2900f, -3300f, 4000, 3000, area),
        PhotoViewport(0.1f, 300f, 900f, 4000, 3000, area),
    )
}
