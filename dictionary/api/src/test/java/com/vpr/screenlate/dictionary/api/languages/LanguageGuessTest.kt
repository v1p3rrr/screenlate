package com.vpr.screenlate.dictionary.api.languages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageGuessTest {

    private val unknown: (String) -> String? = { null }

    @Test
    fun `japanese definitions make a monolingual dictionary`() {
        val text = "たべもの を くち に いれて かみ、のみこむ。 食物を口に入れ、かんで飲み込む。 ".repeat(3)
        assertThat(LanguageGuess.of(text, unknown)).isEqualTo("ja")
    }

    @Test
    fun `cyrillic definitions are russian unless the detector says otherwise`() {
        val text = "есть, кушать; питаться чем-либо ".repeat(4)
        assertThat(LanguageGuess.of(text, unknown)).isEqualTo("ru")
        assertThat(LanguageGuess.of(text) { "uk" }).isEqualTo("uk")
        // Only Cyrillic languages count for Cyrillic text.
        assertThat(LanguageGuess.of(text) { "en" }).isEqualTo("ru")
    }

    @Test
    fun `latin definitions go to the detector, english by function words without one`() {
        // Bilingual entries carry Japanese examples too.
        val text = "to eat; to live on something 私たちは毎日ご飯を食べる。 We eat rice every day. ".repeat(3)
        assertThat(LanguageGuess.of(text) { "de" }).isEqualTo("de")
        assertThat(LanguageGuess.of(text, unknown)).isEqualTo("en")
        assertThat(LanguageGuess.of("essen; speisen; sich ernähren von etwas ".repeat(4), unknown)).isNull()
    }

    @Test
    fun `loanword spellings and pinyin leave japanese definitions japanese`() {
        // A monolingual dictionary that opens with loanwords: a quarter of the letters are Latin.
        val loanwords = "ラジオ【radio】 無線で音声を送る放送。 ".repeat(3)
        assertThat(LanguageGuess.of(loanwords) { "en" }).isEqualTo("ja")
        // A Chinese-Japanese dictionary gives pinyin before the Japanese definition.
        val pinyin = "吃饭【chīfàn】 ご飯を食べる。食事をする。 ".repeat(3)
        assertThat(LanguageGuess.of(pinyin) { "en" }).isEqualTo("ja")
    }

    @Test
    fun `bilingual definitions with many japanese examples keep their language`() {
        // About half of the letters are Japanese examples.
        val latin = "〜ばかりに just because; simply because 彼の一言ばかりに、計画が台無しになった。 ".repeat(2)
        assertThat(LanguageGuess.of(latin) { "en" }).isEqualTo("en")
        val cyrillic = "たべる【食べる】 есть; кушать; питаться 毎日ご飯を食べる。 ".repeat(2)
        assertThat(LanguageGuess.of(cyrillic, unknown)).isEqualTo("ru")
    }

    @Test
    fun `kanji without kana are chinese, hangul is korean`() {
        assertThat(LanguageGuess.of("吃飯，進食，吃東西，用餐。".repeat(5), unknown)).isEqualTo("zh")
        assertThat(LanguageGuess.of("먹다, 식사하다, 음식을 먹다 ".repeat(5), unknown)).isEqualTo("ko")
    }

    @Test
    fun `too little text tells nothing`() {
        assertThat(LanguageGuess.of("to eat", unknown)).isNull()
        assertThat(LanguageGuess.of("", unknown)).isNull()
    }
}
