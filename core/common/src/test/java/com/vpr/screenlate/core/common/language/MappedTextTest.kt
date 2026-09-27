package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MappedTextTest {

    @Test
    fun `identity maps lengths to themselves`() {
        val text = MappedText.identity("食べる")
        assertThat(text.sourceLength(0)).isEqualTo(0)
        assertThat(text.sourceLength(2)).isEqualTo(2)
        assertThat(text.sourceLength(10)).isEqualTo(3)
    }

    @Test
    fun `a replaced part maps to the whole match`() {
        val text = MappedText.identity("ｶﾞｯｺｳへ行く").replace(Regex("ｶﾞｯｺｳ"), "学校")
        assertThat(text.text).isEqualTo("学校へ行く")
        assertThat(text.sourceLength(1)).isEqualTo(5)
        assertThat(text.sourceLength(2)).isEqualTo(5)
        assertThat(text.sourceLength(3)).isEqualTo(6)
    }

    @Test
    fun `removed characters are skipped`() {
        val text = MappedText.identity("食《た》べる").replace(Regex("《[^》]*》"), "")
        assertThat(text.text).isEqualTo("食べる")
        assertThat(text.sourceLength(2)).isEqualTo(5)
        assertThat(text.sourceLength(3)).isEqualTo(6)
    }

    @Test
    fun `javascript replacement syntax`() {
        val text = MappedText.identity("abc").replace(Regex("(b)"), "[$1$&$$]")
        assertThat(text.text).isEqualTo("a[bb$]c")
        val named = MappedText.identity("abc").replace(Regex("(?<x>b)"), "<$<x>>")
        assertThat(named.text).isEqualTo("a<b>c")
        assertThat(MappedText.identity("a").replace(Regex("a"), """\""").text).isEqualTo("""\""")
    }

    @Test
    fun `mappings compose`() {
        val first = MappedText.identity("xxab").replace(Regex("xx"), "y")
        val second = first.replace(Regex("ya"), "z")
        assertThat(second.text).isEqualTo("zb")
        assertThat(second.sourceLength(1)).isEqualTo(3)
        assertThat(second.sourceLength(2)).isEqualTo(4)
    }
}
