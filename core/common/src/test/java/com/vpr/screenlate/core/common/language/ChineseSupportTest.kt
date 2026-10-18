package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChineseSupportTest {

    private fun start(text: String) = ChineseSupport.lookupStart(text, latinAsNative = false)

    @Test
    fun `chinese characters start a lookup in either script`() {
        assertThat(start("你好")).isEqualTo(LookupStart.Any)
        assertThat(start("學習")).isEqualTo(LookupStart.Any)
        assertThat(start("〇")).isEqualTo(LookupStart.Any)
        assertThat(start("𠮶")).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `radical look-alikes of characters start a lookup`() {
        // KANGXI RADICAL SUN (looks like 日) and CJK RADICAL C-SIMPLIFIED SEE (looks like 见).
        assertThat(start(String(Character.toChars(0x2F47)) + "本")).isEqualTo(LookupStart.Any)
        assertThat(start(String(Character.toChars(0x2EC5)))).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `latin text that chinese follows takes any match`() {
        assertThat(start("T恤衫")).isEqualTo(LookupStart.Any)
        assertThat(start("3月")).isEqualTo(LookupStart.Any)
        assertThat(start("卡拉OK")).isEqualTo(LookupStart.Any)
        assertThat(start("AA制")).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `a latin word on its own counts only as a whole`() {
        assertThat(start("DNA，")).isEqualTo(LookupStart.Whole(3))
        assertThat(start("OK 了")).isEqualTo(LookupStart.Whole(2))
    }

    @Test
    fun `kana, punctuation and single letters start no lookup`() {
        assertThat(start("ひらがな")).isNull()
        assertThat(start("「你好")).isNull()
        assertThat(start("A")).isNull()
        assertThat(start("")).isNull()
    }

    @Test
    fun `the later characters of a match get entries of their own`() {
        assertThat(ChineseSupport.singleCharacterEntries("图书馆")).containsExactly("书", "馆").inOrder()
        assertThat(ChineseSupport.singleCharacterEntries("圖書館")).containsExactly("書", "館").inOrder()
        assertThat(ChineseSupport.singleCharacterEntries("一〇")).isEmpty()
        assertThat(ChineseSupport.singleCharacterEntries("T恤")).containsExactly("恤")
    }

    @Test
    fun `a character entry is the first character`() {
        assertThat(ChineseSupport.characterEntry("學習")).isEqualTo("學")
        assertThat(ChineseSupport.characterEntry("𠮶子")).isEqualTo("𠮶")
        assertThat(ChineseSupport.characterEntry("OK")).isNull()
    }

    @Test
    fun `sentences end at chinese and latin marks`() {
        assertThat(ChineseSupport.endsSentence("好。", 1)).isTrue()
        assertThat(ChineseSupport.endsSentence("吗？", 1)).isTrue()
        assertThat(ChineseSupport.endsSentence("好，", 1)).isFalse()
        // A full-width period between digits is a decimal point.
        assertThat(ChineseSupport.endsSentence("约３．５米", 2)).isFalse()
        assertThat(ChineseSupport.endsSentence("好．再", 1)).isTrue()
        assertThat(ChineseSupport.quotePairs['「']).isEqualTo('」')
        assertThat(ChineseSupport.quotePairs['《']).isEqualTo('》')
    }

    @Test
    fun `wiktionary recordings are found by the pinyin without spaces`() {
        assertThat(ChineseSupport.recordingTitle("你好", "nǐ hǎo")).isEqualTo("nǐhǎo")
        assertThat(ChineseSupport.recordingTitle("行", "xíng (xing²)")).isEqualTo("xíng")
        assertThat(ChineseSupport.recordingTitle("你好", "")).isEqualTo("你好")
        assertThat(ChineseSupport.recordingTitle("書", "syu¹")).isEqualTo("書")
    }

    @Test
    fun `text to speech reads the characters, not the pinyin`() {
        assertThat(ChineseSupport.speechText("行", "háng")).isEqualTo("行")
        assertThat(JapaneseSupport.speechText("猫", "ねこ")).isEqualTo("ねこ")
        assertThat(JapaneseSupport.speechText("猫", "")).isEqualTo("猫")
    }
}
