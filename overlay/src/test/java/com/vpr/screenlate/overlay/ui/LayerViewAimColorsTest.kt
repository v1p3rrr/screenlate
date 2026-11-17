package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.theme.PaletteGenerator
import org.junit.Test

class LayerViewAimColorsTest {

    @Test
    fun `dark bubble colors get opaque fill and white outline`() {
        val emerald = 0xFF059669.toInt()
        val (fill, outline) = LayerView.aimColors(emerald, eInk = false)
        assertThat(fill).isEqualTo(emerald)
        assertThat(outline).isEqualTo(PaletteGenerator.WHITE)
    }

    @Test
    fun `translucent bubble color input produces opaque aim fill`() {
        val translucentEmerald = 0x73059669
        val (fill, outline) = LayerView.aimColors(translucentEmerald, eInk = false)
        assertThat(fill).isEqualTo(0xFF059669.toInt())
        assertThat(outline).isEqualTo(PaletteGenerator.WHITE)
    }

    @Test
    fun `light bubble colors invert outline to charcoal`() {
        val pastelPink = 0xFFFFB3EC.toInt()
        val pastelYellow = 0xFFFDE047.toInt()
        val white = 0xFFFFFFFF.toInt()

        for (color in listOf(pastelPink, pastelYellow, white)) {
            val (fill, outline) = LayerView.aimColors(color, eInk = false)
            assertThat(fill).isEqualTo(color)
            assertThat(outline).isEqualTo(PaletteGenerator.CHARCOAL)
        }
    }

    @Test
    fun `e-ink mode forces black aim dot with white outline regardless of bubble color`() {
        val lightColor = 0xFFFFF59D.toInt()
        val (fill, outline) = LayerView.aimColors(lightColor, eInk = true)
        assertThat(fill).isEqualTo(0xFF000000.toInt())
        assertThat(outline).isEqualTo(PaletteGenerator.WHITE)
    }
}
