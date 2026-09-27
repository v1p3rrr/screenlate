package com.vpr.screenlate.overlay.capture

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class AccessibilityTextTest {

    private fun row(count: Int) = (0 until count).map { Box(it * 30f, 100f, it * 30f + 30f, 140f) }

    @Test
    fun `characters with boxes of their own are usable`() {
        assertThat(sharesBoxes(row(12))).isFalse()
        assertThat(sharesBoxes(row(1))).isFalse()
    }

    @Test
    fun `the node's bounds repeated for every character are not`() {
        val node = Box(40f, 1000f, 1220f, 1360f)
        assertThat(sharesBoxes(List(30) { node })).isTrue()
        assertThat(sharesBoxes(List(2) { node })).isTrue()
    }

    @Test
    fun `a few characters sharing a box do not reject the text`() {
        // A combining mark may report its base character's box.
        val boxes = row(10).toMutableList().apply { add(3, this[3]) }
        assertThat(sharesBoxes(boxes)).isFalse()
    }
}
