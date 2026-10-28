package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class EsperantoSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = EsperantoSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, eras and lists before a capital do`() {
        assertThat(ends("Ekz. pomoj kaj piroj")).isFalse()
        assertThat(ends("Multaj lingvoj, ekz. Konrad diris")).isFalse()
        val soCalled = "Ĝi estas la t.n. Ora pordego"
        assertThat(ends(soCalled, soCalled.lastIndexOf('.'))).isFalse()
        assertThat(ends("Prelegis prof. Zamenhof")).isFalse()
        assertThat(ends("Ĝi okazis ĉ. 1900 en Varsovio")).isFalse()
        assertThat(ends("Vidu p. 46 de la libro")).isFalse()
        assertThat(ends("Pomoj, piroj ktp. Poste")).isTrue()
        assertThat(ends("pomoj, piroj ktp. por krei")).isFalse()
        val era = "la 9-a jarcento a.K. Pliiĝo"
        assertThat(ends(era, era.lastIndexOf('.'))).isTrue()
        val year = "post 346 a.K. estis"
        assertThat(ends(year, year.lastIndexOf('.'))).isFalse()
        assertThat(ends("En la 12a jc. ekzistis")).isFalse()
        assertThat(ends("La tago finiĝis. Vespere pluvis")).isTrue()
    }

    @Test
    fun `letters of a spaced abbreviation hold only beside each other`() {
        val thatIs = "fruktoj, t. e. pomoj"
        assertThat(ends(thatIs, thatIs.indexOf("t.") + 1)).isFalse()
        assertThat(ends(thatIs, thatIs.indexOf("e.") + 1)).isFalse()
        val among = "kaj i. a. ankaŭ"
        assertThat(ends(among, among.indexOf("a.") + 1)).isFalse()
        assertThat(ends("Ĝi estas la litero e. Poste")).isTrue()
    }

    @Test
    fun `ordinals take an ending, so a number with a period ends the sentence`() {
        assertThat(EsperantoSupport.ordinalPeriod).isFalse()
        assertThat(ends("Li naskiĝis en 1990. Poste")).isTrue()
        assertThat(ends("pi egalas 3.14")).isFalse()
    }

    @Test
    fun `english quotes, low quotes closing high and guillemets are paired`() {
        assertThat(EsperantoSupport.quotePairs).containsEntry('“', '”')
        assertThat(EsperantoSupport.quotePairs).containsEntry('„', '“')
        assertThat(EsperantoSupport.quotePairs).containsEntry('«', '»')
        assertThat(EsperantoSupport.quotePairs).doesNotContainKey('»')
    }

    @Test
    fun `every headword is keyed without the hats`() {
        assertThat(EsperantoSupport.headwordMarks).isEqualTo(
            HeadwordMarks(marks = "̂", bases = "cghjs", everyHeadword = true, markBases = mapOf("̆" to "u")),
        )
        assertThat(EsperantoSupport.inflectionRulesOnlyWithoutEntry).isFalse()
    }

    @Test
    fun `ocr's letters of other languages are the hat letters, others stay`() {
        assertThat(EsperantoSupport.ocrWord("šipo")).isEqualTo("ŝipo")
        assertThat(EsperantoSupport.ocrWord("ŠI")).isEqualTo("ŜI")
        assertThat(EsperantoSupport.ocrWord("čiu")).isEqualTo("ĉiu")
        assertThat(EsperantoSupport.ocrWord("ċambro")).isEqualTo("ĉambro")
        assertThat(EsperantoSupport.ocrWord("ġardeno")).isEqualTo("ĝardeno")
        assertThat(EsperantoSupport.ocrWord("Ği")).isEqualTo("Ĝi")
        assertThat(EsperantoSupport.ocrWord("ankaǔ")).isEqualTo("ankaŭ")
        assertThat(EsperantoSupport.ocrWord("laů")).isEqualTo("laŭ")
        assertThat(EsperantoSupport.ocrWord("aű")).isEqualTo("aŭ")
        assertThat(EsperantoSupport.ocrWord("ħoro")).isEqualTo("ĥoro")
        assertThat(EsperantoSupport.ocrWord("ĵaŭdo")).isEqualTo("ĵaŭdo")
        assertThat(EsperantoSupport.ocrWord("José")).isEqualTo("José")
        assertThat(EsperantoSupport.ocrLines(listOf(listOf("čiu", "šipo")))).containsExactly(listOf("ĉiu", "ŝipo"))
    }

    @Test
    fun `esperanto's own code, name, flag and letters`() {
        assertThat(Language.of("eo")).isEqualTo(Language.ESPERANTO)
        assertThat(EsperantoSupport.languageTag).isEqualTo("eo")
        assertThat(EsperantoSupport.speechTag).isEqualTo("eo")
        assertThat(EsperantoSupport.localeLanguages).containsExactly("eo")
        assertThat(EsperantoSupport.nameIn(Locale.ENGLISH)).isEqualTo("Esperanto")
        assertThat(EsperantoSupport.codeLabel).isEqualTo("EO")
        assertThat(EsperantoSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(EsperantoSupport.flagCountries).containsExactly("lang_eo")
        assertThat(EsperantoSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(EsperantoSupport.fontLetters).isEqualTo("ĉĝĥĵŝŭ")
        assertThat(EsperantoSupport.compoundLinks).isNull()
        assertThat(EsperantoSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(EsperantoSupport.linguaLibreItems).containsExactly("Q143")
        assertThat(EsperantoSupport.glyph).isEqualTo("E")
    }
}
