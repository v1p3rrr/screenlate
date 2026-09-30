package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class MenuPlacementTest {

    private val screen = Box(0f, 0f, 1000f, 2000f)

    private fun bubble(centerX: Float, centerY: Float) = Box.fromCenter(centerX, centerY, 100f, 100f)

    @Test
    fun `opens below and to the right of a bubble on the left`() {
        assertThat(MenuPlacement.position(400f, 300f, bubble(100f, 500f), screen, 10f)).isEqualTo(50f to 560f)
    }

    @Test
    fun `ends at the right side of a bubble on the right`() {
        val (x, _) = MenuPlacement.position(400f, 300f, bubble(900f, 500f), screen, 10f)
        assertThat(x).isEqualTo(550f)
    }

    @Test
    fun `opens above a bubble near the bottom`() {
        assertThat(MenuPlacement.position(400f, 300f, bubble(900f, 1800f), screen, 10f)).isEqualTo(550f to 1440f)
    }

    @Test
    fun `takes the side with more room and stays on the screen when neither fits`() {
        val short = Box(0f, 0f, 1000f, 600f)
        assertThat(MenuPlacement.position(400f, 400f, bubble(100f, 200f), short, 10f)).isEqualTo(50f to 200f)
        assertThat(MenuPlacement.position(400f, 400f, bubble(100f, 400f), short, 10f)).isEqualTo(50f to 0f)
    }

    @Test
    fun `keeps a menu wider than the room from the left side of the screen`() {
        val (x, _) = MenuPlacement.position(1200f, 300f, bubble(900f, 500f), screen, 10f)
        assertThat(x).isEqualTo(0f)
    }

    @Test
    fun `stays inside the bounds when the bubble is partly off them`() {
        val (x, _) = MenuPlacement.position(400f, 300f, bubble(1000f, 500f), screen, 10f)
        assertThat(x).isEqualTo(600f)
    }
}
