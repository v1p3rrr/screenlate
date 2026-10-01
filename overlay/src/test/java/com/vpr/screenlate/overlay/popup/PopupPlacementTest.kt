package com.vpr.screenlate.overlay.popup

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PopupPlacementTest {

    private val screen = Box(0f, 100f, 1000f, 2000f)

    private fun place(word: Box, bubble: Box? = null) =
        PopupPlacement.place(word, bubble, PopupPlacement.Size(800f, 600f), screen, margin = 10f)

    @Test
    fun `word near the bottom gets the popup above`() {
        val popup = place(Box(400f, 1500f, 600f, 1550f))

        assertThat(popup.bottom).isEqualTo(1490f)
        assertThat(popup.left).isEqualTo(100f)
    }

    @Test
    fun `word near the top gets the popup below`() {
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
    fun `popup covers neither the word nor the bubble when it fits`() {
        val word = Box(400f, 1200f, 600f, 1250f)
        val bubble = Box(450f, 900f, 550f, 1000f)

        val popup = place(word, bubble = bubble)

        assertThat(popup.intersects(bubble)).isFalse()
        assertThat(popup.intersects(word)).isFalse()
    }

    @Test
    fun `popup is clamped horizontally into the screen`() {
        val popup = place(Box(950f, 1500f, 990f, 1550f))

        assertThat(popup.right).isEqualTo(1000f)
    }

    @Test
    fun `above wins over a roomier space below`() {
        // 690 px free above, 1140 px below: both hold the 600 px popup.
        val popup = place(Box(400f, 800f, 600f, 850f))

        assertThat(popup.bottom).isEqualTo(790f)
        assertThat(popup.height).isEqualTo(600f)
    }

    @Test
    fun `a word too tall for either side keeps the full size and covers part of it on the roomier side`() {
        // 450 px free above the word, 290 px below: too little for 600 px on either side.
        val word = Box(400f, 560f, 600f, 1700f)

        val popup = place(word)

        assertThat(popup.top).isEqualTo(100f)
        assertThat(popup.height).isEqualTo(600f)
        assertThat(popup.width).isEqualTo(800f)
    }

    @Test
    fun `below is used at full size and covers the bubble when there is more room there`() {
        // The word near the top with the bubble under it: 0 px above, 500 px below the bubble.
        val word = Box(400f, 110f, 600f, 1300f)
        val bubble = Box(450f, 1340f, 550f, 1490f)

        val popup = place(word, bubble = bubble)

        assertThat(popup.bottom).isEqualTo(2000f)
        assertThat(popup.height).isEqualTo(600f)
    }

    @Test
    fun `portrait vertical text in the middle of the screen goes above with the full width`() {
        val phone = Box(0f, 100f, 1080f, 2340f)
        val size = PopupPlacement.size(phone, maxWidth = 1260f)
        // The owner's case: a short column low on the page, the bubble right under it.
        val word = Box(500f, 1500f, 560f, 2000f)
        val bubble = Box(455f, 2010f, 605f, 2160f)
        val popup = PopupPlacement.place(word, bubble, size, phone, margin = 10f)
        assertThat(popup.bottom).isEqualTo(1490f)
        assertThat(popup.width).isWithin(0.5f).of(1080f * 0.85f)
        assertThat(popup.height).isWithin(0.5f).of(2240f * 0.35f)
    }

    @Test
    fun `portrait column taller than the free space never gets a popup beside it`() {
        val phone = Box(0f, 100f, 1080f, 2340f)
        val size = PopupPlacement.size(phone, maxWidth = 1260f)
        // A column over nearly the whole height, 750 px from the left edge.
        val word = Box(750f, 300f, 800f, 2150f)
        val popup = PopupPlacement.place(word, null, size, phone, margin = 10f)
        assertThat(popup.width).isWithin(0.5f).of(1080f * 0.85f)
        assertThat(popup.height).isWithin(0.5f).of(2240f * 0.35f)
        assertThat(popup.top).isEqualTo(100f)
    }

    @Test
    fun `portrait popups take about a third of the height`() {
        val size = PopupPlacement.size(Box(0f, 0f, 1080f, 2340f), maxWidth = 1260f)
        assertThat(size.width).isWithin(0.5f).of(1080f * 0.85f)
        assertThat(size.height).isWithin(0.5f).of(2340f * 0.35f)
        assertThat(size.beside).isFalse()
    }

    @Test
    fun `landscape popups go beside the word with nearly the whole height`() {
        val size = PopupPlacement.size(Box(0f, 0f, 2340f, 1000f), maxWidth = 1260f)
        assertThat(size.width).isEqualTo(1170f)
        assertThat(size.height).isWithin(0.5f).of(900f)
        assertThat(size.minWidth).isWithin(0.5f).of(936f)
        assertThat(size.beside).isTrue()
    }

    private val landscape = Box(0f, 0f, 2340f, 1000f)
    private val landscapeSize = PopupPlacement.Size(1170f, 900f, minWidth = 936f, beside = true)

    @Test
    fun `landscape text gets the popup on the roomier side`() {
        // Word and bubble around x 1300..1500: 1290 px free on the left, 830 on the right.
        val popup = PopupPlacement.place(
            Box(1300f, 450f, 1500f, 500f), Box(1350f, 520f, 1450f, 620f), landscapeSize, landscape, margin = 10f,
        )
        assertThat(popup.right).isEqualTo(1290f)
        assertThat(popup.width).isEqualTo(1170f)
        assertThat(popup.height).isEqualTo(900f)
        assertThat(popup.top).isAtLeast(0f)
        assertThat(popup.bottom).isAtMost(1000f)
    }

    @Test
    fun `landscape popup shrinks a little to stay beside the word`() {
        // 990 px free on the left, 990 on the right: a bit narrower than the popup, wider than 80% of it.
        val popup = PopupPlacement.place(Box(1000f, 450f, 1340f, 500f), null, landscapeSize, landscape, 10f)
        assertThat(popup.width).isEqualTo(990f)
        assertThat(popup.right).isEqualTo(990f)
    }

    @Test
    fun `landscape popup keeps its size and covers part of the word when no side has 80 percent of the width`() {
        // 690 px free on the left, 490 on the right.
        val wide = Box(700f, 800f, 1840f, 850f)
        val popup = PopupPlacement.place(wide, null, landscapeSize, landscape, margin = 10f)
        assertThat(popup.left).isEqualTo(0f)
        assertThat(popup.width).isEqualTo(1170f)
        assertThat(popup.height).isEqualTo(900f)
        assertThat(popup.bottom).isAtMost(1000f)
    }
}
