package com.vpr.screenlate.core.common.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EInkEnlargementTest {

    private fun enlarge(bubble: Int, font: Int) =
        EInkEnlargement.of(bubbleNow = bubble, fontNow = font, minBubble = 56, fontStep = 2, maxFont = 24)

    @Test
    fun `raises a smaller bubble and the text`() {
        assertThat(enlarge(bubble = 44, font = 16)).isEqualTo(
            EInkEnlargement(EInkEnlargement.Change(44, 56), EInkEnlargement.Change(16, 18)),
        )
    }

    @Test
    fun `leaves a larger bubble and text at the maximum as they are`() {
        assertThat(enlarge(bubble = 60, font = 24)).isEqualTo(EInkEnlargement(null, null))
        assertThat(enlarge(bubble = 60, font = 23).font).isEqualTo(EInkEnlargement.Change(23, 24))
        // Above the maximum (a size from another version) the text is never made smaller.
        assertThat(enlarge(bubble = 60, font = 30).font).isNull()
    }

    @Test
    fun `restores sizes that are still enlarged`() {
        assertThat(enlarge(bubble = 44, font = 16).restore(bubbleNow = 56, fontNow = 18))
            .isEqualTo(EInkEnlargement.Restore(bubble = 44, font = 16))
    }

    @Test
    fun `keeps sizes changed by hand since`() {
        assertThat(enlarge(bubble = 44, font = 16).restore(bubbleNow = 50, fontNow = 18))
            .isEqualTo(EInkEnlargement.Restore(bubble = null, font = 16))
        assertThat(enlarge(bubble = 44, font = 16).restore(bubbleNow = 56, fontNow = 20))
            .isEqualTo(EInkEnlargement.Restore(bubble = 44, font = null))
    }

    @Test
    fun `does not restore a size the enlargement left alone`() {
        assertThat(enlarge(bubble = 60, font = 16).restore(bubbleNow = 60, fontNow = 18))
            .isEqualTo(EInkEnlargement.Restore(bubble = null, font = 16))
    }
}
