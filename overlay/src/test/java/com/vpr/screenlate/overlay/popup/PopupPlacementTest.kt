package com.vpr.screenlate.overlay.popup

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PopupPlacementTest {

    private val screen = Box(0f, 100f, 1000f, 2000f)

    private fun place(word: Box, vertical: Boolean = false, bubble: Box? = null, minHeight: Float = 300f) =
        PopupPlacement.place(
            word,
            vertical,
            bubble,
            popupWidth = 800f,
            popupHeight = 600f,
            minHeight = minHeight,
            screen = screen,
            margin = 10f,
        )

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
        val popup = PopupPlacement.place(Box(200f, 200f, 300f, 250f), false, null, 800f, 600f, 300f, tinyScreen, 10f)

        assertThat(popup.left).isEqualTo(0f)
        assertThat(popup.top).isEqualTo(200f)
        assertThat(popup.height).isEqualTo(300f)
    }
}
