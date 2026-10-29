package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class MongolianSupportTest {

    private val support = MongolianSupport

    private fun ends(text: String, at: Int = text.lastIndexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `initials joined to the name keep the sentence going, other periods end it`() {
        assertThat(ends("Х.Баттулга ирэв", at = 1)).isFalse()
        assertThat(ends("Тэр ирэв. Би")).isTrue()
        assertThat(ends("ном г.м. Дараа")).isTrue()
    }

    @Test
    fun `news quotes open, their closing ones open nothing`() {
        assertThat(support.quotePairs['“']).isEqualTo('”')
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs.keys).doesNotContain('„')
    }

    @Test
    fun `the shared latin look-alikes are fixed, the mongolian letters are kept`() {
        assertThat(support.ocrWord("нoм")).isEqualTo("ном")
        assertThat(support.ocrWord("y")).isEqualTo("у")
        assertThat(support.ocrWord("өдөр")).isEqualTo("өдөр")
        assertThat(support.ocrWord("үг")).isEqualTo("үг")
        assertThat(support.misreadSpellings("лам")).isEmpty()
    }

    @Test
    fun `the fonts need the cyrillic extension for its two letters`() {
        assertThat(support.fontSubsets).containsExactly("latin", "cyrillic", "cyrillic-ext")
        assertThat(support.fontLetters).contains("ө")
        assertThat(support.fontLetters).contains("ү")
        assertThat(support.foldsMarks).isTrue()
        assertThat(support.flagCountries).containsExactly("mn")
        assertThat(support.glyph).isEqualTo("М")
        assertThat(support.codeLabel).isEqualTo("МН")
        assertThat(Language.of("mn")).isEqualTo(Language.MONGOLIAN)
        assertThat(Language.MONGOLIAN.lookupDictionaryCodes).containsExactly("mn")
    }
}
