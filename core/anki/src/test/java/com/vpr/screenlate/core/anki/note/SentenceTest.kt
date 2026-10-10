package com.vpr.screenlate.core.anki.note

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class SentenceTest {
    private fun extract(paragraph: String, word: String, language: Language = Language.JAPANESE): Sentence =
        Sentence.extract(paragraph, paragraph.indexOf(word), word.length, language)

    @Test
    fun cutsAtTerminators() {
        val sentence = extract("昨日は雨だった。今日は晴れている！明日は？", "晴れ")
        assertThat(sentence).isEqualTo(Sentence("今日は", "晴れ", "ている！"))
    }

    @Test
    fun keepsQuotedSpeechTogether() {
        val sentence = extract("「行く。」と彼は言った。次の文。", "言っ")
        assertThat(sentence.text).isEqualTo("「行く。」と彼は言った。")
        assertThat(sentence.body).isEqualTo("言っ")
    }

    @Test
    fun keepsClosingQuoteAfterTerminator() {
        val sentence = extract("彼は「もう帰る。」", "帰る")
        assertThat(sentence.text).isEqualTo("彼は「もう帰る。」")
    }

    @Test
    fun wholeParagraphWithoutTerminators() {
        val sentence = extract("ありがとうございます 桃香さんもね", "桃香")
        assertThat(sentence.prefix).isEqualTo("ありがとうございます ")
        assertThat(sentence.suffix).isEqualTo("さんもね")
    }

    @Test
    fun abbreviationsAndDecimalsDoNotEndAnEnglishSentence() {
        val paragraph = "She gave up. Mr. Smith met her at 5 p.m. at gate 3.5 near example.com. They went home."
        val sentence = extract(paragraph, "gate", Language.ENGLISH)
        assertThat(sentence.text).isEqualTo("Mr. Smith met her at 5 p.m. at gate 3.5 near example.com.")
    }

    @Test
    fun englishSentenceEndsAtOtherTerminatorsAndQuotes() {
        val sentence = extract("Really? “Go home.” he said. Fine!", "said", Language.ENGLISH)
        assertThat(sentence.text).isEqualTo("“Go home.” he said.")
        assertThat(extract("It was late... Then it rained.", "rained", Language.ENGLISH).text).isEqualTo("Then it rained.")
    }
}
