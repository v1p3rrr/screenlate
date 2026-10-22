package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class SlovakSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = SlovakSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, years, centuries and amounts before a capital do`() {
        assertThat(ends("Napr. jablká a hrušky")).isFalse()
        assertThat(ends("Ide o tzv. Vyhlásenie o zhode")).isFalse()
        assertThat(ends("Prednášal prof. Kováč")).isFalse()
        assertThat(ends("Kostol sv. Martina")).isFalse()
        assertThat(ends("Cesta č. 18 vedie")).isFalse()
        assertThat(ends("Stalo sa to v r. 1990 na jeseň")).isFalse()
        assertThat(ends("Stalo sa to v roku 1990 r. Potom")).isTrue()
        val century = "V 20. stor. sa mesto rozrástlo"
        assertThat(ends(century, century.indexOf("stor.") + 4)).isFalse()
        assertThat(ends("Stálo to 5 mil. eur")).isFalse()
        assertThat(ends("Stálo to 5 mil. Potom")).isTrue()
        assertThat(ends("jablká, hrušky atď. Potom")).isTrue()
        assertThat(ends("jablká, hrušky atď. a podobne")).isFalse()
        assertThat(ends("Deň sa skončil. Večer pršalo")).isTrue()
    }

    @Test
    fun `letters of a spaced abbreviation hold only beside each other`() {
        val thatIs = "ovocie, t. j. jablká"
        assertThat(ends(thatIs, thatIs.indexOf("t.") + 1)).isFalse()
        assertThat(ends(thatIs, thatIs.indexOf("j.") + 1)).isFalse()
        assertThat(ends("ovocie, t.j. jablká", "ovocie, t.j. jablká".lastIndexOf('.'))).isFalse()
        val era = "žili v 5. stor. pr. n. l. Gréci"
        assertThat(ends(era, era.indexOf("pr.") + 2)).isFalse()
        assertThat(ends(era, era.indexOf("n.") + 1)).isFalse()
        assertThat(ends("Je to písmeno j. Potom")).isTrue()
    }

    @Test
    fun `ordinals take a period, a year before a capital ends the sentence`() {
        assertThat(SlovakSupport.ordinalPeriod).isTrue()
        assertThat(ends("Narodil sa 1. januára")).isFalse()
        assertThat(ends("V 20. storočí")).isFalse()
        assertThat(ends("Narodil sa v roku 1990. Potom")).isTrue()
    }

    @Test
    fun `quotes open low and close high, guillemets are not paired`() {
        assertThat(SlovakSupport.quotePairs).containsEntry('„', '“')
        assertThat(SlovakSupport.quotePairs).containsEntry('‚', '‘')
        assertThat(SlovakSupport.quotePairs).doesNotContainKey('“')
        assertThat(SlovakSupport.quotePairs).doesNotContainKey('»')
        assertThat(SlovakSupport.quotePairs).doesNotContainKey('«')
    }

    @Test
    fun `every headword is keyed without the marks of its letters`() {
        assertThat(SlovakSupport.headwordMarks).isEqualTo(
            HeadwordMarks(
                marks = "\u0301",
                bases = "aeiouylr",
                everyHeadword = true,
                markBases = mapOf("\u030C" to "cdlnstz", "\u0302" to "o", "\u0308" to "a"),
            ),
        )
        assertThat(SlovakSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        assertThat(SlovakSupport.ocrWord("ďakujem")).isEqualTo("ďakujem")
    }

    @Test
    fun `words its dictionaries lack are looked up in the czech ones`() {
        assertThat(SlovakSupport.fallbackLanguage).isEqualTo(Language.CZECH)
        assertThat(Language.SLOVAK.lookupDictionaryCodes).containsExactly("sk", "cs").inOrder()
        assertThat(Language.CZECH.lookupDictionaryCodes).containsExactly("cs")
    }

    @Test
    fun `slovak's own code, name, flag and letters`() {
        assertThat(Language.of("sk")).isEqualTo(Language.SLOVAK)
        assertThat(SlovakSupport.languageTag).isEqualTo("sk")
        assertThat(SlovakSupport.speechTag).isEqualTo("sk")
        assertThat(SlovakSupport.localeLanguages).containsExactly("sk")
        assertThat(SlovakSupport.nameIn(Locale.ENGLISH)).isEqualTo("Slovak")
        assertThat(SlovakSupport.codeLabel).isEqualTo("SK")
        assertThat(SlovakSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(SlovakSupport.flagCountries).containsExactly("sk")
        assertThat(SlovakSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(SlovakSupport.fontLetters).isEqualTo("áäčďéíĺľňóôŕšťúýž")
        assertThat(SlovakSupport.compoundLinks).isNull()
        assertThat(SlovakSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(SlovakSupport.linguaLibreItems).containsExactly("Q9058")
        assertThat(SlovakSupport.glyph).isEqualTo("Ô")
    }
}
