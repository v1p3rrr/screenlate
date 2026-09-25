package com.vpr.screenlate.overlay.popup

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PopupPlacementTest {

    private val screen = Box(0f, 100f, 1000f, 2000f)

    private fun place(word: Box, vertical: Boolean = false, bubble: Box? = null) =
        PopupPlacement.place(word, vertical, bubble, popupWidth = 800f, popupHeight = 600f, screen = screen, margin = 10f)

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
    fun `avoids the bubble even when the other side has less room`() {
        // Word in the lower middle: above has more room, but the bubble sits above the word.
        val word = Box(400f, 1200f, 600f, 1250f)
        val bubble = Box(450f, 900f, 550f, 1000f)

        val popup = place(word, bubble = bubble)

        assertThat(popup.top).isEqualTo(1260f)
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
        val popup = PopupPlacement.place(Box(200f, 200f, 300f, 250f), false, null, 800f, 600f, tinyScreen, 10f)

        assertThat(popup.left).isEqualTo(0f)
        assertThat(popup.top).isEqualTo(0f)
    }
}
