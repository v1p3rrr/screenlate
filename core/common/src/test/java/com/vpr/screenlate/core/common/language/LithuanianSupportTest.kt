package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class LithuanianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = LithuanianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, years, centuries and amounts before a capital do`() {
        assertThat(ends("Pvz. obuoliai ir kriaušės")).isFalse()
        assertThat(ends("Jį gydė prof. Kazlauskas")).isFalse()
        assertThat(ends("Šv. Jono gatvė")).isFalse()
        assertThat(ends("Žr. 5 skyrių")).isFalse()
        assertThat(ends("Tai įvyko 2020 m. rugsėjo mėnesį")).isFalse()
        assertThat(ends("Tai įvyko 2020 m. Kitais metais")).isTrue()
        assertThat(ends("XIV a. pradžioje")).isFalse()
        assertThat(ends("Kaina 5 tūkst. eurų")).isFalse()
        assertThat(ends("Kainavo 5 tūkst. Vėliau")).isTrue()
        assertThat(ends("Obuoliai, kriaušės ir kt. Vėliau")).isTrue()
        assertThat(ends("Diena baigėsi. Vakare lijo")).isTrue()
    }

    @Test
    fun `letters of a spaced abbreviation hold only beside each other`() {
        val thatIs = "vaisiai, t. y. obuoliai"
        assertThat(ends(thatIs, thatIs.indexOf("t.") + 1)).isFalse()
        assertThat(ends(thatIs, thatIs.indexOf("y.") + 1)).isFalse()
        val joined = "vaisiai, t.y. obuoliai"
        assertThat(ends(joined, joined.lastIndexOf('.'))).isFalse()
        val era = "gyveno 450 m. pr. m. e. graikai"
        assertThat(ends(era, era.lastIndexOf("e.") + 1)).isFalse()
        val thisYear = "š. m. rugsėjo"
        assertThat(ends(thisYear, 1)).isFalse()
        assertThat(ends("Tai raidė y. Vėliau")).isTrue()
    }

    @Test
    fun `ordinals take an ending, so a number with a period ends the sentence`() {
        assertThat(LithuanianSupport.ordinalPeriod).isFalse()
        assertThat(ends("Jis gimė 1990. Vėliau")).isTrue()
        assertThat(ends("pi lygu 3.14")).isFalse()
    }

    @Test
    fun `quotes open low and close high, english quotes and guillemets are not paired`() {
        assertThat(LithuanianSupport.quotePairs).containsEntry('„', '“')
        assertThat(LithuanianSupport.quotePairs).doesNotContainKey('“')
        assertThat(LithuanianSupport.quotePairs).doesNotContainKey('«')
    }

    @Test
    fun `ocr's u with a diaeresis, e with an acute and i with a dot below are lithuanian letters, others stay`() {
        assertThat(LithuanianSupport.ocrWord("ÜKIS")).isEqualTo("ŪKIS")
        assertThat(LithuanianSupport.ocrWord("büti")).isEqualTo("būti")
        assertThat(LithuanianSupport.ocrWord("upé")).isEqualTo("upė")
        assertThat(LithuanianSupport.ocrWord("ịvykis")).isEqualTo("įvykis")
        assertThat(LithuanianSupport.ocrWord("žmogus")).isEqualTo("žmogus")
        assertThat(LithuanianSupport.ocrWord("José")).isEqualTo("Josė")
        assertThat(LithuanianSupport.ocrWord("É")).isEqualTo("É")
        assertThat(LithuanianSupport.ocrLines(listOf(listOf("büti", "upé")))).containsExactly(listOf("būti", "upė"))
    }

    @Test
    fun `forms are keyed without stress, every headword without the letters' marks as well`() {
        assertThat(LithuanianSupport.headwordMarks).isEqualTo(
            HeadwordMarks(
                marks = "\u0301\u0300\u0303",
                bases = "aeiouylmnr",
                everyHeadword = true,
                lemmasAsWritten = false,
                markBases = mapOf("\u0307" to "i"),
                letterMarks = "\u0328\u0307\u0304\u030C",
                letterBases = "aeiucsz",
            ),
        )
        assertThat(LithuanianSupport.inflectionRulesOnlyWithoutEntry).isFalse()
    }

    @Test
    fun `lithuanian's own code, name, flag and letters`() {
        assertThat(Language.of("lt")).isEqualTo(Language.LITHUANIAN)
        assertThat(LithuanianSupport.languageTag).isEqualTo("lt")
        assertThat(LithuanianSupport.speechTag).isEqualTo("lt")
        assertThat(LithuanianSupport.localeLanguages).containsExactly("lt")
        assertThat(LithuanianSupport.nameIn(Locale.ENGLISH)).isEqualTo("Lithuanian")
        assertThat(LithuanianSupport.codeLabel).isEqualTo("LT")
        assertThat(LithuanianSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(LithuanianSupport.flagCountries).containsExactly("lt")
        assertThat(LithuanianSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(LithuanianSupport.fontLetters).isEqualTo("ąčęėįšųūž")
        assertThat(LithuanianSupport.compoundLinks).isNull()
        assertThat(LithuanianSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(LithuanianSupport.linguaLibreItems).containsExactly("Q9083")
        assertThat(LithuanianSupport.glyph).isEqualTo("Ė")
    }
}
