package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChinesePinyinTest {

    private fun key(reading: String) = ChineseSupport.readingKey(reading)

    private fun match(query: String, reading: String) = ChineseSupport.readingMatch(query, reading)

    @Test
    fun `typed pinyin in any form has the key of the reading`() {
        assertThat(key("nǐhǎo")).isEqualTo("nihao")
        assertThat(key("nihao")).isEqualTo("nihao")
        assertThat(key("Ni Hao")).isEqualTo("nihao")
        assertThat(key("ni3hao3")).isEqualTo("nihao")
        assertThat(key("ni3 hao3")).isEqualTo("nihao")
        assertThat(key("Xī'ān")).isEqualTo("xian")
    }

    @Test
    fun `u with umlaut is v however it is written`() {
        assertThat(key("lǜ")).isEqualTo("lv")
        assertThat(key("lv4")).isEqualTo("lv")
        assertThat(key("lu:4")).isEqualTo("lv")
        assertThat(key("nǚ'ér")).isEqualTo("nver")
        assertThat(key("lu")).isEqualTo("lu")
    }

    @Test
    fun `readings in other notations have no key`() {
        assertThat(key("buk⁷")).isNull()
        assertThat(key("11Qū")).isNull()
        assertThat(key("你好")).isNull()
        assertThat(key("ni7")).isNull()
        assertThat(key("3")).isNull()
        assertThat(key("")).isNull()
        assertThat(key(" ")).isNull()
    }

    @Test
    fun `wiktionary readings key without their numbered pinyin`() {
        assertThat(key(ChineseSupport.displayReading("xíng (xing²)"))).isEqualTo("xing")
    }

    @Test
    fun `tones typed as marks or numbers rank the reading with them`() {
        assertThat(match("ni3hao3", "nǐhǎo")).isEqualTo(2)
        assertThat(match("nǐhǎo", "nǐhǎo")).isEqualTo(2)
        assertThat(match("ni2hao3", "nǐhǎo")).isEqualTo(0)
        assertThat(match("shi4", "shì")).isEqualTo(1)
        assertThat(match("shi4", "shí")).isEqualTo(-1)
        assertThat(match("xiān", "xiān")).isEqualTo(1)
        assertThat(match("xian4", "xiān")).isEqualTo(-1)
    }

    @Test
    fun `a number goes on the vowel pinyin marks`() {
        assertThat(match("gou3", "gǒu")).isEqualTo(1)
        assertThat(match("liu2", "liú")).isEqualTo(1)
        assertThat(match("gui4", "guì")).isEqualTo(1)
        assertThat(match("lve4", "lüè")).isEqualTo(1)
        assertThat(match("xue2", "xué")).isEqualTo(1)
    }

    @Test
    fun `5 or 0 is the neutral tone, a syllable without a mark`() {
        assertThat(match("ma5", "ma")).isEqualTo(1)
        assertThat(match("tou2fa0", "tóufa")).isEqualTo(2)
        assertThat(match("ma5", "mā")).isEqualTo(-1)
    }

    @Test
    fun `an apostrophe typed before a syllable ranks the reading with a break there`() {
        assertThat(match("xi'an", "Xī'ān")).isEqualTo(1)
        assertThat(match("xi'an", "xiān")).isEqualTo(-1)
        assertThat(match("xi1'an1", "Xī'ān")).isEqualTo(3)
        assertThat(match("ni hao", "nǐhǎo")).isEqualTo(0)
    }

    @Test
    fun `a query without tones ranks every reading alike`() {
        assertThat(match("nihao", "nǐhǎo")).isEqualTo(0)
        assertThat(match("xian", "Xī'ān")).isEqualTo(0)
        assertThat(match("ni3", "nǐhǎo")).isEqualTo(0)
    }

    @Test
    fun `readings join without spaces, apostrophes and case`() {
        assertThat(ChineseSupport.normalizeReading("nǐ hǎo")).isEqualTo(ChineseSupport.normalizeReading("nǐhǎo"))
        assertThat(ChineseSupport.normalizeReading("Xī'ān")).isEqualTo("xīān")
        assertThat(ChineseSupport.normalizeReading("nǐhǎo")).isNotEqualTo(ChineseSupport.normalizeReading("níhǎo"))
    }
}
