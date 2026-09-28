package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class SymbolLagTest {

    /** Five glyphs of 40 columns at a 50-column pitch from column 10: ink at 10..49, 60..99, and so on. */
    private val contrast = IntArray(270) { column -> if (column >= 10 && (column - 10) % 50 < 40 && column < 260) 200 else 5 }

    /** Boxes along x with the given edges; the first and last edges are the outer ones. */
    private fun boxes(vararg edges: Float) = edges.toList().zipWithNext { from, to -> Box(from, 0f, to, 30f) }

    @Test
    fun `lagging boundaries move back onto the gaps`() {
        // The real boundaries are at 55, 105, 155, 205; these sit 20 columns further, inside the next glyph.
        val lagging = boxes(5f, 75f, 125f, 175f, 225f, 262f)
        val aligned = SymbolLag.align(lagging, contrast, origin = 0, vertical = false)
        assertThat(aligned.map { it.left }).containsExactly(5f, 55f, 105f, 155f, 205f).inOrder()
        assertThat(aligned.last().right).isEqualTo(262f)
    }

    @Test
    fun `boundaries already in the gaps stay`() {
        val exact = boxes(8f, 55f, 105f, 155f, 205f, 262f)
        assertThat(SymbolLag.align(exact, contrast, origin = 0, vertical = false)).isSameInstanceAs(exact)
    }

    @Test
    fun `the origin maps boxes onto the profile`() {
        val lagging = boxes(5f, 75f, 125f, 175f, 225f, 262f).map { it.offset(1000f, 0f) }
        val aligned = SymbolLag.align(lagging, contrast, origin = 1000, vertical = false)
        assertThat(aligned[1].left).isEqualTo(1055f)
    }

    @Test
    fun `vertical boxes are moved along y`() {
        val lagging = listOf(5f, 75f, 125f, 175f, 225f, 262f).zipWithNext { from, to -> Box(0f, from, 30f, to) }
        val aligned = SymbolLag.align(lagging, contrast, origin = 0, vertical = true)
        assertThat(aligned.map { it.top }).containsExactly(5f, 55f, 105f, 155f, 205f).inOrder()
        assertThat(aligned.map { it.left }.distinct()).containsExactly(0f)
    }

    @Test
    fun `no clear answer leaves the boxes`() {
        val lagging = boxes(5f, 75f, 125f, 175f, 225f, 262f)
        val blank = IntArray(270) { 10 }
        assertThat(SymbolLag.align(lagging, blank, origin = 0, vertical = false)).isSameInstanceAs(lagging)
        val twoGlyphs = boxes(5f, 75f, 110f)
        assertThat(SymbolLag.align(twoGlyphs, contrast, origin = 0, vertical = false)).isSameInstanceAs(twoGlyphs)
    }

    @Test
    fun `contrast is the light and dark spread of each column or row`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        // 3 × 2 pixels: the middle column has one black pixel.
        val pixels = intArrayOf(white, black, white, white, white, white)
        assertThat(SymbolLag.contrast(pixels, 3, 2, vertical = false).toList()).containsExactly(0, 255, 0).inOrder()
        assertThat(SymbolLag.contrast(pixels, 3, 2, vertical = true).toList()).containsExactly(255, 0).inOrder()
    }
}
