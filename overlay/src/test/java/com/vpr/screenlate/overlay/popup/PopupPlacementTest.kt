package com.vpr.screenlate.overlay.popup

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PopupPlacementTest {

    private val screen = Box(0f, 100f, 1000f, 2000f)

    private fun place(word: Box, vertical: Boolean = false, bubble: Box? = null, minHeight: Float = 300f) =
        PopupPlacement.place(word, vertical, bubble, PopupPlacement.Size(800f, 600f, minHeight), screen, margin = 10f)

    @Test
    fun `horizontal word near the bottom gets the popup above`() {
        val popup = place(Box(400f, 1500f, 600f, 1550f))

        assertThat(popup.bottom).isEqualTo(1490f)
        assertThat(popup.left).isEqualTo(100f)
    }

    @Test
    fun `horizontal word near the top gets the popup below`() {
        val popup = place(Box(400f, 200f, 600f, 250f))

        assertThat(popup.top).isEqualTo(260f)
    }

    @Test
    fun `popup below the word goes below the bubble`() {
        // Aim above the finger: the bubble sits right under the word near the top of the screen.
        val word = Box(400f, 200f, 600f, 250f)
        val bubble = Box(450f, 300f, 550f, 400f)

        val popup = place(word, bubble = bubble)

        assertThat(popup.top).isEqualTo(410f)
        assertThat(popup.intersects(bubble)).isFalse()
    }

    @Test
    fun `popup never covers the bubble`() {
        val word = Box(400f, 1200f, 600f, 1250f)
        val bubble = Box(450f, 900f, 550f, 1000f)

        val popup = place(word, bubble = bubble)

        assertThat(popup.intersects(bubble)).isFalse()
        assertThat(popup.intersects(word)).isFalse()
    }

    @Test
    fun `popup shrinks to the free space`() {
        // 450 px free above the word, 290 px below: too little for the full 600 px on either side.
        val word = Box(400f, 560f, 600f, 1700f)

        val popup = place(word)

        assertThat(popup.bottom).isEqualTo(550f)
        assertThat(popup.height).isEqualTo(450f)
    }

    @Test
    fun `popup is clamped horizontally into the screen`() {
        val popup = place(Box(950f, 1500f, 990f, 1550f))

        assertThat(popup.right).isEqualTo(1000f)
    }

    @Test
    fun `vertical text goes beside the column when there is room`() {
        val popup = place(Box(900f, 800f, 950f, 1300f), vertical = true)

        assertThat(popup.right).isEqualTo(890f)
    }

    @Test
    fun `vertical text falls back to above or below when the sides are too narrow`() {
        val popup = place(Box(500f, 1300f, 550f, 1800f), vertical = true)

        assertThat(popup.bottom).isEqualTo(1290f)
    }

    @Test
    fun `falls back to a clamped box when nothing fits`() {
        val tinyScreen = Box(0f, 0f, 500f, 500f)
        val popup = PopupPlacement.place(Box(200f, 200f, 300f, 250f), false, null, PopupPlacement.Size(800f, 600f, 300f), tinyScreen, 10f)

        assertThat(popup.left).isEqualTo(0f)
        assertThat(popup.top).isEqualTo(200f)
        assertThat(popup.height).isEqualTo(300f)
    }

    @Test
    fun `portrait popups take about a third of the height`() {
        val size = PopupPlacement.size(Box(0f, 0f, 1080f, 2340f), density = 3f, maxWidth = 1260f)
        assertThat(size.width).isWithin(0.5f).of(1080f * 0.85f)
        assertThat(size.height).isWithin(0.5f).of(2340f * 0.35f)
        assertThat(size.minHeight).isWithin(0.5f).of(480f)
    }

    @Test
    fun `landscape popups go beside the word with nearly the whole height`() {
        val size = PopupPlacement.size(Box(0f, 0f, 2340f, 1000f), density = 3f, maxWidth = 1260f)
        assertThat(size.width).isEqualTo(1170f)
        assertThat(size.height).isWithin(0.5f).of(900f)
        assertThat(size.minWidth).isEqualTo(780f)
        assertThat(size.minHeight).isAtMost(size.height)
        assertThat(size.besideFirst).isTrue()
    }

    private val landscape = Box(0f, 0f, 2340f, 1000f)
    private val landscapeSize = PopupPlacement.Size(1170f, 900f, 300f, minWidth = 780f, besideFirst = true)

    @Test
    fun `landscape horizontal text gets the popup on the roomier side, shrunk to fit`() {
        // Word and bubble around x 1300..1500: 1290 px free on the left, 830 on the right.
        val popup = PopupPlacement.place(
            Box(1300f, 450f, 1500f, 500f), false, Box(1350f, 520f, 1450f, 620f), landscapeSize, landscape, margin = 10f,
        )
        assertThat(popup.right).isEqualTo(1290f)
        assertThat(popup.width).isEqualTo(1170f)
        assertThat(popup.height).isEqualTo(900f)
        assertThat(popup.top).isAtLeast(0f)
        assertThat(popup.bottom).isAtMost(1000f)

        // In the middle both sides are narrower than the popup, which shrinks to the side with more room.
        val middle = PopupPlacement.place(Box(1000f, 450f, 1340f, 500f), false, null, landscapeSize, landscape, 10f)
        assertThat(middle.width).isEqualTo(990f)
        assertThat(middle.right).isEqualTo(990f)
    }

    @Test
    fun `landscape falls back to above or below when no side is wide enough`() {
        val wide = Box(500f, 800f, 1840f, 850f)
        val popup = PopupPlacement.place(wide, false, null, landscapeSize, landscape, margin = 10f)
        assertThat(popup.bottom).isAtMost(wide.top)
    }
}
