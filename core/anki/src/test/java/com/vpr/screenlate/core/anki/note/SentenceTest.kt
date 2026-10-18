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

    @Test
    fun spanishSentenceStartsAtInvertedMarksAndKeepsAngleQuotes() {
        val paragraph = "Llegó tarde. ¿Dónde está el Sr. García? No sé. «Ven aquí.» dijo ella."
        assertThat(extract(paragraph, "García", Language.SPANISH).text).isEqualTo("¿Dónde está el Sr. García?")
        assertThat(extract(paragraph, "dijo", Language.SPANISH).text).isEqualTo("«Ven aquí.» dijo ella.")
    }

    @Test
    fun frenchSentenceKeepsSpacedGuillemetsAndMonsieur() {
        val paragraph = "Il pleut. M. Dupont arrive. « Restez ici. » dit-il."
        assertThat(extract(paragraph, "Dupont", Language.FRENCH).text).isEqualTo("M. Dupont arrive.")
        assertThat(extract(paragraph, "dit", Language.FRENCH).text).isEqualTo("« Restez ici. » dit-il.")
    }

    @Test
    fun germanSentenceKeepsOrdinalsAndLowQuotes() {
        val paragraph = "Es regnet. Am 3. Oktober kommt er, z. B. mit dem Zug. „Bleib hier.“ sagte sie."
        assertThat(extract(paragraph, "Oktober", Language.GERMAN).text)
            .isEqualTo("Am 3. Oktober kommt er, z. B. mit dem Zug.")
        assertThat(extract(paragraph, "sagte", Language.GERMAN).text).isEqualTo("„Bleib hier.“ sagte sie.")
    }

    @Test
    fun italianSentenceKeepsAngleQuotesAndSignore() {
        val paragraph = "Piove. Il sig. Rossi arriva. «Resta qui.» disse lei."
        assertThat(extract(paragraph, "Rossi", Language.ITALIAN).text).isEqualTo("Il sig. Rossi arriva.")
        assertThat(extract(paragraph, "disse", Language.ITALIAN).text).isEqualTo("«Resta qui.» disse lei.")
    }

    @Test
    fun portugueseSentenceKeepsAngleQuotesAndSenhora() {
        val paragraph = "Chove. A Sra. Silva chegou. «Fica aqui.» disse ela."
        assertThat(extract(paragraph, "Silva", Language.PORTUGUESE).text).isEqualTo("A Sra. Silva chegou.")
        assertThat(extract(paragraph, "disse", Language.PORTUGUESE).text).isEqualTo("«Fica aqui.» disse ela.")
    }

    @Test
    fun polishSentenceKeepsLowQuotesOrdinalsAndAbbreviations() {
        val paragraph = "Pada. W XX w. powstał np. ten dom. „Zostań tu.” powiedziała. To 5. piętro."
        assertThat(extract(paragraph, "powstał", Language.POLISH).text).isEqualTo("W XX w. powstał np. ten dom.")
        assertThat(extract(paragraph, "powiedziała", Language.POLISH).text).isEqualTo("„Zostań tu.” powiedziała.")
        assertThat(extract(paragraph, "piętro", Language.POLISH).text).isEqualTo("To 5. piętro.")
    }

    @Test
    fun turkishSentenceKeepsOrdinalsAndAbbreviations() {
        val paragraph = "Yağmur yağdı. 15. yüzyılda Dr. Ayşe yaşadı. Sonra gitti."
        assertThat(extract(paragraph, "Ayşe", Language.TURKISH).text).isEqualTo("15. yüzyılda Dr. Ayşe yaşadı.")
    }

    @Test
    fun latvianSentenceKeepsYearsLowQuotesAndAbbreviations() {
        val paragraph = "Lija. 1918. gada 18. novembrī dibināja valsti. „Nāc šurp.“ viņa teica. Āboli u.c. augļi auga."
        assertThat(extract(paragraph, "dibināja", Language.LATVIAN).text)
            .isEqualTo("1918. gada 18. novembrī dibināja valsti.")
        assertThat(extract(paragraph, "teica", Language.LATVIAN).text).isEqualTo("„Nāc šurp.“ viņa teica.")
        assertThat(extract(paragraph, "augļi", Language.LATVIAN).text).isEqualTo("Āboli u.c. augļi auga.")
    }

    @Test
    fun vietnameseSentenceKeepsCityAbbreviation() {
        val paragraph = "Trời mưa. Tôi sống ở TP. Hồ Chí Minh. Hết."
        assertThat(extract(paragraph, "sống", Language.VIETNAMESE).text).isEqualTo("Tôi sống ở TP. Hồ Chí Minh.")
    }

    @Test
    fun russianSentenceKeepsQuotesAndAbbreviations() {
        val paragraph = "Шёл дождь. В 1999 г. он сказал: «Я приду. Обязательно». Потом ушёл."
        assertThat(extract(paragraph, "сказал", Language.RUSSIAN).text)
            .isEqualTo("В 1999 г. он сказал: «Я приду. Обязательно».")
    }

    @Test
    fun aQuoteTheParagraphDoesNotCloseKeepsTheSentenceShort() {
        // German pairs „ with “; an English-style “ja” leaves a “ without its opening quote.
        val german = "Das ist gut. Er sagte “ja” und ging. Dann kam sie."
        assertThat(extract(german, "ging", Language.GERMAN).text).isEqualTo("Er sagte “ja” und ging.")
        // A list item's bracket has no opening one.
        val list = "Steps: 1) open it. 2) close it. Done."
        assertThat(extract(list, "close", Language.ENGLISH).text).isEqualTo("2) close it.")
        // Polish pairs „ with ”; a German-style „Idę.“ leaves an opening quote open after the word.
        val polish = "Powiedział „Idę.“ Potem wyszedł. Koniec."
        assertThat(extract(polish, "Powiedział", Language.POLISH).text).isEqualTo("Powiedział „Idę.“")
        // A quote that closes still holds its terminators.
        assertThat(extract("1) Er sagte: „Komm. Jetzt.“ und ging. Fertig.", "sagte", Language.GERMAN).text)
            .isEqualTo("1) Er sagte: „Komm. Jetzt.“ und ging.")
        assertThat(extract("「行く。」と彼は言った。", "言っ").text).isEqualTo("「行く。」と彼は言った。")
    }
}
