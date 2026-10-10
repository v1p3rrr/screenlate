package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnglishSupportTest {
    private fun ends(text: String, at: String): Boolean = EnglishSupport.endsSentence(text, text.indexOf(at))

    @Test
    fun `a period ends the sentence unless an abbreviation or a number holds it`() {
        assertThat(ends("He left. Then", ".")).isTrue()
        assertThat(ends("Mr. Smith", ".")).isFalse()
        assertThat(ends("at 5 P.M. and", "M.")).isFalse()
        assertThat(ends("e.g. this", "g.")).isFalse()
        assertThat(ends("pi is 3.14", ".")).isFalse()
        assertThat(ends("see example.com today", ".")).isFalse()
        assertThat(ends("He said no. Then", ".")).isTrue()
        assertThat(ends("Wait... no", ".")).isTrue()
    }

    @Test
    fun `other terminators always end it`() {
        assertThat(ends("Mr! Smith", "!")).isTrue()
        assertThat(ends("line\nnext", "\n")).isTrue()
        assertThat(ends("a, b", ",")).isFalse()
    }

    @Test
    fun `the word under the aim is looked up from its start`() {
        assertThat(EnglishSupport.wordStartOffset("did not want to gi", "ve up")).isEqualTo(2)
        assertThat(EnglishSupport.wordStartOffset("rock'n'r", "oll")).isEqualTo(8)
        assertThat(EnglishSupport.wordStartOffset("a well-", "known")).isEqualTo(5)
        assertThat(EnglishSupport.wordStartOffset("the ", "fight")).isEqualTo(0)
        assertThat(EnglishSupport.wordStartOffset("give", " up")).isEqualTo(0)
    }
}
