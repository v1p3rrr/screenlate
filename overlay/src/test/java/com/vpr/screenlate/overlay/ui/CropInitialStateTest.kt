package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.settings.NormalizedCrop
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class CropInitialStateTest {

    private val screen = Box(0f, 0f, 1080f, 2400f)
    private val focus = Box(100f, 400f, 900f, 700f)

    @Test
    fun `without remembered area starts around focus paragraph`() {
        val state = CropInitialState.resolve(screen, focus, remembered = null)
        assertThat(state.initial).isEqualTo(focus)
        assertThat(state.frame).isEqualTo(focus)
        assertThat(state.whole).isFalse()
    }

    @Test
    fun `without remembered area or focus starts with whole image and center-quarter fallback frame`() {
        val state = CropInitialState.resolve(screen, focus = null, remembered = null)
        assertThat(state.initial).isEqualTo(screen)
        assertThat(state.frame).isEqualTo(Box(270f, 600f, 810f, 1800f))
        assertThat(state.whole).isTrue()
    }

    @Test
    fun `remembered sub-region overrides focus paragraph`() {
        val remembered = NormalizedCrop(0.1f, 0.5f, 0.9f, 0.9f)
        val state = CropInitialState.resolve(screen, focus, remembered)
        val expected = remembered.toBox(screen)
        assertThat(state.initial).isEqualTo(expected)
        assertThat(state.frame).isEqualTo(expected)
        assertThat(state.whole).isFalse()
    }

    @Test
    fun `remembered whole screen selects whole screen and keeps focus as Frame fallback`() {
        val remembered = NormalizedCrop(0f, 0f, 1f, 1f)
        val state = CropInitialState.resolve(screen, focus, remembered)
        assertThat(state.initial).isEqualTo(screen)
        assertThat(state.frame).isEqualTo(focus)
        assertThat(state.whole).isTrue()
    }

    @Test
    fun `invalid remembered crop falls back to focus`() {
        val invalid = NormalizedCrop(0.8f, 0.2f, 0.2f, 0.8f)
        val state = CropInitialState.resolve(screen, focus, invalid)
        assertThat(state.initial).isEqualTo(focus)
        assertThat(state.frame).isEqualTo(focus)
        assertThat(state.whole).isFalse()
    }
}
