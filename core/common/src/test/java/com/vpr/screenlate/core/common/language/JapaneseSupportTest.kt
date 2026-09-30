package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JapaneseSupportTest {

    private fun romaji(text: String) = JapaneseSupport.fromLatin(MappedText.identity(text))?.text

    private fun start(text: String, romaji: Boolean = false) = JapaneseSupport.lookupStart(text, latinAsNative = romaji)

    @Test
    fun `japanese text starts a lookup`() {
        assertThat(start("食べる")).isEqualTo(LookupStart.Any)
        assertThat(start("ラーメン")).isEqualTo(LookupStart.Any)
        assertThat(start("々")).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `latin text that japanese follows takes any match`() {
        assertThat(start("Tシャツを")).isEqualTo(LookupStart.Any)
        assertThat(start("ＣＤプレーヤー")).isEqualTo(LookupStart.Any)
        assertThat(start("3月に")).isEqualTo(LookupStart.Any)
        assertThat(start("CD-ROMドライブ")).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `a latin word on its own counts only as a whole`() {
        assertThat(start("OL")).isEqualTo(LookupStart.Whole(2))
        assertThat(start("OK です")).isEqualTo(LookupStart.Whole(2))
        assertThat(start("ＤＮＡ、")).isEqualTo(LookupStart.Whole(3))
        assertThat(start("Wi-Fi is on")).isEqualTo(LookupStart.Whole(5))
        assertThat(start("M&A.")).isEqualTo(LookupStart.Whole(3))
        assertThat(start("3D")).isEqualTo(LookupStart.Whole(2))
        assertThat(start("ayataka")).isEqualTo(LookupStart.Whole(7))
    }

    @Test
    fun `single letters, numbers and punctuation start no lookup`() {
        assertThat(start("W")).isNull()
        assertThat(start("I think")).isNull()
        assertThat(start("123")).isNull()
        assertThat(start("「食べる")).isNull()
        assertThat(start("")).isNull()
    }

    @Test
    fun `latin text takes any match when romaji is converted`() {
        assertThat(start("taberu", romaji = true)).isEqualTo(LookupStart.Any)
        assertThat(start("123", romaji = true)).isNull()
    }

    @Test
    fun `a latin word is looked up from its first letter`() {
        assertThat(JapaneseSupport.wordStartOffset("これは", "O")).isEqualTo(0)
        assertThat(JapaneseSupport.wordStartOffset("これはO", "L")).isEqualTo(1)
        assertThat(JapaneseSupport.wordStartOffset("the CD-", "R")).isEqualTo(3)
        assertThat(JapaneseSupport.wordStartOffset("Base6", "4")).isEqualTo(0)
        assertThat(JapaneseSupport.wordStartOffset("3", "D")).isEqualTo(1)
        assertThat(JapaneseSupport.wordStartOffset("食べ", "る")).isEqualTo(0)
        assertThat(JapaneseSupport.wordStartOffset("end. ", "N")).isEqualTo(0)
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
    fun `spelling variant applies all rules in order`() {
        fun variants(text: String) = JapaneseSupport.spellingVariants(MappedText.identity(text)).map { it.text }
        assertThat(variants("1人")).containsExactly("一人")
        assertThat(variants("３日")).containsExactly("三日")
        assertThat(variants("色々")).containsExactly("色色")
        assertThat(variants("ロボッ・ト、 だ-.")).containsExactly("ロボっトだ")
        assertThat(variants("食べる")).isEmpty()
        assertThat(variants("・ 、")).isEmpty()
    }

    @Test
    fun `spelling variant maps back to the source`() {
        val variant = JapaneseSupport.spellingVariants(MappedText.identity("ス・マホ"))[0]
        assertThat(variant.text).isEqualTo("スマホ")
        assertThat(variant.sourceLength(2)).isEqualTo(3)
        val doubled = JapaneseSupport.spellingVariants(MappedText.identity("時々だ"))[0]
        assertThat(doubled.text).isEqualTo("時時だ")
        assertThat(doubled.sourceLength(2)).isEqualTo(2)
    }

    @Test
    fun `single character entries are the kanji after the first character`() {
        assertThat(JapaneseSupport.singleCharacterEntries("食べ物")).containsExactly("物")
        assertThat(JapaneseSupport.singleCharacterEntries("お茶")).containsExactly("茶")
        assertThat(JapaneseSupport.singleCharacterEntries("日本語")).containsExactly("本", "語").inOrder()
        assertThat(JapaneseSupport.singleCharacterEntries("人々")).isEmpty()
        assertThat(JapaneseSupport.singleCharacterEntries("𠮟る𠮟")).containsExactly("𠮟")
    }

    @Test
    fun `the kanji entry is for a kanji at the start`() {
        assertThat(JapaneseSupport.characterEntry("彅は")).isEqualTo("彅")
        assertThat(JapaneseSupport.characterEntry("𠮟る")).isEqualTo("𠮟")
        assertThat(JapaneseSupport.characterEntry("は彅")).isNull()
        assertThat(JapaneseSupport.characterEntry("々")).isNull()
        assertThat(JapaneseSupport.characterEntry("abc")).isNull()
        assertThat(JapaneseSupport.characterEntry("")).isNull()
    }
}
