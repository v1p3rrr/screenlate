package com.vpr.screenlate.overlay

import android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
import android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
import android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForegroundWindowTest {

    private fun window(name: String, type: Int, focused: Boolean = false, layer: Int = 0, pip: Boolean = false) =
        ForegroundWindow.Window(name, type, focused, layer, pip)

    @Test
    fun `the focused application window wins over a higher one`() {
        val picked = ForegroundWindow.pick(
            listOf(window("reader", TYPE_APPLICATION, focused = true, layer = 1), window("other", TYPE_APPLICATION, layer = 2)),
        )
        assertThat(picked).isEqualTo("reader")
    }

    @Test
    fun `while an overlay holds focus the topmost application window counts`() {
        val picked = ForegroundWindow.pick(
            listOf(
                window("menu", TYPE_ACCESSIBILITY_OVERLAY, focused = true, layer = 9),
                window("launcher", TYPE_APPLICATION, layer = 3),
                window("reader", TYPE_APPLICATION, layer = 2),
            ),
        )
        assertThat(picked).isEqualTo("launcher")
    }

    @Test
    fun `a picture-in-picture window is not taken for the app under the overlay`() {
        val picked = ForegroundWindow.pick(
            listOf(
                window("menu", TYPE_ACCESSIBILITY_OVERLAY, focused = true, layer = 9),
                window("video", TYPE_APPLICATION, layer = 5, pip = true),
                window("reader", TYPE_APPLICATION, layer = 2),
            ),
        )
        assertThat(picked).isEqualTo("reader")
    }

    @Test
    fun `nothing while system UI holds focus`() {
        val picked = ForegroundWindow.pick(
            listOf(window("shade", TYPE_SYSTEM, focused = true, layer = 9), window("reader", TYPE_APPLICATION, layer = 2)),
        )
        assertThat(picked).isNull()
    }
}
