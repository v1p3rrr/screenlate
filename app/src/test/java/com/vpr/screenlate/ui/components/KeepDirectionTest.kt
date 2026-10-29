package com.vpr.screenlate.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeepDirectionTest {
    private val leftToRight = "\u2066"
    private val rightToLeft = "\u2067"
    private val end = "\u2069"

    @Test
    fun `an english sentence in a right-to-left interface keeps its direction`() {
        val text = "Swedish glosses of JMdict."
        assertThat(keepDirection(text, rightToLeft = true)).isEqualTo(leftToRight + text + end)
        assertThat(keepDirection("2026: $text", rightToLeft = true)).isEqualTo(leftToRight + "2026: $text" + end)
    }

    @Test
    fun `a hebrew or arabic text in a left-to-right interface keeps its direction`() {
        assertThat(keepDirection("מילון עברי.", rightToLeft = false)).isEqualTo(rightToLeft + "מילון עברי." + end)
        assertThat(keepDirection("قاموس عربي.", rightToLeft = false)).isEqualTo(rightToLeft + "قاموس عربي." + end)
    }

    @Test
    fun `text of the interface's direction or without letters stays as it is`() {
        assertThat(keepDirection("Swedish glosses of JMdict.", rightToLeft = false)).isEqualTo("Swedish glosses of JMdict.")
        assertThat(keepDirection("מילון עברי.", rightToLeft = true)).isEqualTo("מילון עברי.")
        assertThat(keepDirection("2026-08-11", rightToLeft = true)).isEqualTo("2026-08-11")
    }

    @Test
    fun `each line is marked on its own`() {
        val lines = keepDirection("First line.\n\nמילון.", rightToLeft = true).split('\n')
        assertThat(lines).containsExactly(leftToRight + "First line." + end, "", "מילון.").inOrder()
    }
}
