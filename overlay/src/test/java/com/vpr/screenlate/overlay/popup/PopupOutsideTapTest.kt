package com.vpr.screenlate.overlay.popup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PopupOutsideTapTest {

    @Test
    fun `catcher is active only when attached, visible, not concealed, and enabled`() {
        for (attached in listOf(false, true)) {
            for (visible in listOf(false, true)) {
                for (concealed in listOf(false, true)) {
                    for (enabled in listOf(false, true)) {
                        val expected = attached && visible && !concealed && enabled
                        assertThat(OutsideTapState.shouldCatch(attached, visible, concealed, enabled))
                            .isEqualTo(expected)
                    }
                }
            }
        }
    }

    @Test
    fun `down followed by up dismisses when active`() {
        val state = OutsideTapState()
        state.onDown()
        assertThat(state.onUp(active = true)).isTrue()
        // A second up without a new down does not dismiss again.
        assertThat(state.onUp(active = true)).isFalse()
    }

    @Test
    fun `cancelled edge gesture does not dismiss`() {
        val state = OutsideTapState()
        state.onDown()
        state.onCancel()
        assertThat(state.onUp(active = true)).isFalse()
    }

    @Test
    fun `reset while pressed prevents late dismissal`() {
        val state = OutsideTapState()
        state.onDown()
        state.reset()
        assertThat(state.onUp(active = true)).isFalse()
    }

    @Test
    fun `up while inactive clears press without dismissing`() {
        val state = OutsideTapState()
        state.onDown()
        assertThat(state.onUp(active = false)).isFalse()
        assertThat(state.onUp(active = true)).isFalse()
    }
}
