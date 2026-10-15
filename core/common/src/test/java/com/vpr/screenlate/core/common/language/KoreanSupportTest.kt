package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KoreanSupportTest {

    private fun start(text: String) = KoreanSupport.lookupStart(text, latinAsNative = false)

    @Test
    fun `hangul and hanja start a lookup`() {
        assertThat(start("먹었습니다")).isEqualTo(LookupStart.Any)
        assertThat(start("韓國")).isEqualTo(LookupStart.Any)
        assertThat(start("ㄱ")).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `latin text that korean follows takes any match`() {
        assertThat(start("TV를")).isEqualTo(LookupStart.Any)
        assertThat(start("3월")).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `a latin word on its own counts only as a whole`() {
        assertThat(start("PC 방")).isEqualTo(LookupStart.Whole(2))
        assertThat(start("A")).isNull()
    }

    @Test
    fun `punctuation and kana start no lookup`() {
        assertThat(start("(한국)")).isNull()
        assertThat(start("ひらがな")).isNull()
        assertThat(start("")).isNull()
    }

    @Test
    fun `the lookup starts at the start of the spaced word`() {
        assertThat(KoreanSupport.wordStartOffset("공부", "하")).isEqualTo(2)
        assertThat(KoreanSupport.wordStartOffset("저는 공부", "하")).isEqualTo(2)
        assertThat(KoreanSupport.wordStartOffset("저는 ", "공")).isEqualTo(0)
        assertThat(KoreanSupport.wordStartOffset("TV", "를")).isEqualTo(2)
        assertThat(KoreanSupport.wordStartOffset("한국(韓", "國")).isEqualTo(1)
        assertThat(KoreanSupport.wordStartOffset("3", "월")).isEqualTo(0)
        assertThat(KoreanSupport.wordStartOffset("\"안", "녕")).isEqualTo(1)
    }

    @Test
    fun `a latin word is looked up from its start`() {
        assertThat(KoreanSupport.wordStartOffset("저는 T", "V")).isEqualTo(1)
        assertThat(KoreanSupport.wordStartOffset("저는 ", "T")).isEqualTo(0)
    }

    @Test
    fun `hanja after the first get entries of their own`() {
        assertThat(KoreanSupport.singleCharacterEntries("韓國語")).containsExactly("國", "語").inOrder()
        assertThat(KoreanSupport.singleCharacterEntries("한국어")).isEmpty()
        assertThat(KoreanSupport.characterEntry("韓國")).isEqualTo("韓")
        assertThat(KoreanSupport.characterEntry("한국")).isNull()
    }

    @Test
    fun `sentences end at marks, not at a period inside a number`() {
        assertThat(KoreanSupport.endsSentence("좋네요.", 3)).isTrue()
        assertThat(KoreanSupport.endsSentence("갈까요?", 3)).isTrue()
        assertThat(KoreanSupport.endsSentence("3.14", 1)).isFalse()
        assertThat(KoreanSupport.endsSentence("그리고,", 3)).isFalse()
        assertThat(KoreanSupport.quotePairs['「']).isEqualTo('」')
    }

    @Test
    fun `text to speech reads the word`() {
        assertThat(KoreanSupport.speechText("사람", "saram")).isEqualTo("사람")
    }
}
