package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JapaneseSupportTest {

    private fun romaji(text: String) = JapaneseSupport.fromLatin(MappedText.identity(text))?.text

    @Test
    fun `japanese text starts a lookup`() {
        assertThat(JapaneseSupport.isLookupStart("食べる", latinAsNative = false)).isTrue()
        assertThat(JapaneseSupport.isLookupStart("ラーメン", latinAsNative = false)).isTrue()
        assertThat(JapaneseSupport.isLookupStart("々", latinAsNative = false)).isTrue()
    }

    @Test
    fun `latin text counts only when japanese follows`() {
        assertThat(JapaneseSupport.isLookupStart("Tシャツを", latinAsNative = false)).isTrue()
        assertThat(JapaneseSupport.isLookupStart("ＣＤプレーヤー", latinAsNative = false)).isTrue()
        assertThat(JapaneseSupport.isLookupStart("3月に", latinAsNative = false)).isTrue()
        assertThat(JapaneseSupport.isLookupStart("ayataka", latinAsNative = false)).isFalse()
        assertThat(JapaneseSupport.isLookupStart("OK です", latinAsNative = false)).isFalse()
        assertThat(JapaneseSupport.isLookupStart("「食べる", latinAsNative = false)).isFalse()
        assertThat(JapaneseSupport.isLookupStart("", latinAsNative = false)).isFalse()
    }

    @Test
    fun `latin text counts when romaji is converted`() {
        assertThat(JapaneseSupport.isLookupStart("taberu", latinAsNative = true)).isTrue()
        assertThat(JapaneseSupport.isLookupStart("123", latinAsNative = true)).isFalse()
    }

    @Test
    fun `converts romaji to hiragana`() {
        assertThat(romaji("taberu")).isEqualTo("たべる")
        assertThat(romaji("Kanji")).isEqualTo("かんじ")
        assertThat(romaji("konnichiha")).isEqualTo("こんにちは")
        assertThat(romaji("kitte")).isEqualTo("きって")
        assertThat(romaji("matcha")).isEqualTo("まっちゃ")
        assertThat(romaji("ra-men")).isEqualTo("らーめん")
        assertThat(romaji("shinbun")).isEqualTo("しんぶん")
        assertThat(romaji("kan'i")).isEqualTo("かんい")
        assertThat(romaji("ｔａｂｅｒｕ")).isEqualTo("たべる")
        assertThat(romaji("食べる")).isNull()
    }

    @Test
    fun `keeps other characters`() {
        assertThat(romaji("taberu。")).isEqualTo("たべる。")
        assertThat(romaji("- x")).isEqualTo("- x")
    }

    @Test
    fun `maps converted lengths back to the romaji`() {
        val converted = JapaneseSupport.fromLatin(MappedText.identity("taberunda"))!!
        assertThat(converted.text).isEqualTo("たべるんだ")
        assertThat(converted.sourceLength(3)).isEqualTo("taberu".length)
        assertThat(converted.sourceLength(1)).isEqualTo(2)
        val doubled = JapaneseSupport.fromLatin(MappedText.identity("kitte"))!!
        assertThat(doubled.sourceLength(2)).isEqualTo(3)
    }

    @Test
    fun `single character entries are the kanji after the first character`() {
        assertThat(JapaneseSupport.singleCharacterEntries("食べ物")).containsExactly("物")
        assertThat(JapaneseSupport.singleCharacterEntries("お茶")).containsExactly("茶")
        assertThat(JapaneseSupport.singleCharacterEntries("日本語")).containsExactly("本", "語").inOrder()
        assertThat(JapaneseSupport.singleCharacterEntries("人々")).isEmpty()
        assertThat(JapaneseSupport.singleCharacterEntries("𠮟る𠮟")).containsExactly("𠮟")
    }
}
