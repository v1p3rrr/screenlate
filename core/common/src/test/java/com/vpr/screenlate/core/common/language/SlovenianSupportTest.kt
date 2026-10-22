package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class SlovenianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = SlovenianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, centuries, amounts and company forms before a capital do`() {
        assertThat(ends("Npr. jabolka in hruške")).isFalse()
        assertThat(ends("Predaval je prof. Novak")).isFalse()
        assertThat(ends("Predaval je dr. Novak")).isFalse()
        assertThat(ends("Cerkev sv. Martina")).isFalse()
        assertThat(ends("Glej str. 18")).isFalse()
        assertThat(ends("Stanovanje št. 5 je prazno")).isFalse()
        assertThat(ends("Stalo je 5 mio. evrov")).isFalse()
        assertThat(ends("Stalo je 5 mio. Potem")).isTrue()
        val century = "V 20. stol. se je mesto razširilo"
        assertThat(ends(century, century.indexOf("stol.") + 4)).isFalse()
        val company = "Podjetje Novak d.o.o. je zaprlo"
        assertThat(ends(company, company.lastIndexOf('.'))).isFalse()
        val companyEnd = "Delal je pri Novak d.o.o. Potem"
        assertThat(ends(companyEnd, companyEnd.lastIndexOf('.'))).isTrue()
        assertThat(ends("Sadje, hruške itd. Potem")).isTrue()
        assertThat(ends("Videl ga. Potem")).isTrue()
        assertThat(ends("Dan se je končal. Zvečer je deževalo")).isTrue()
    }

    @Test
    fun `letters of a spaced abbreviation hold only beside each other, the era only before a lowercase word`() {
        val thatIs = "sadje, t. i. jabolka"
        assertThat(ends(thatIs, thatIs.indexOf("t.") + 1)).isFalse()
        assertThat(ends(thatIs, thatIs.indexOf("i.") + 1)).isFalse()
        val joined = "sadje, t.i. jabolka"
        assertThat(ends(joined, joined.lastIndexOf('.'))).isFalse()
        val era = "leta 400 pr. n. št. so živeli"
        assertThat(ends(era, era.indexOf("pr.") + 2)).isFalse()
        assertThat(ends(era, era.indexOf("n.") + 1)).isFalse()
        assertThat(ends(era, era.indexOf("št.") + 2)).isFalse()
        val eraEnd = "leta 400 pr. n. št. Potem"
        assertThat(ends(eraEnd, eraEnd.indexOf("št.") + 2)).isTrue()
        assertThat(ends("To je črka i. Potem")).isTrue()
    }

    @Test
    fun `ordinals take a period, a year before a capital ends the sentence`() {
        assertThat(SlovenianSupport.ordinalPeriod).isTrue()
        assertThat(ends("Rodil se je 5. maja")).isFalse()
        assertThat(ends("V 20. stoletju")).isFalse()
        assertThat(ends("V XX. stoletju")).isFalse()
        assertThat(ends("Rodil se je leta 1990. Potem")).isTrue()
    }

    @Test
    fun `quotes open with a right-pointing guillemet and close with a left-pointing one, or open low and close high`() {
        assertThat(SlovenianSupport.quotePairs).containsEntry('»', '«')
        assertThat(SlovenianSupport.quotePairs).containsEntry('„', '“')
        assertThat(SlovenianSupport.quotePairs).doesNotContainKey('«')
        assertThat(SlovenianSupport.quotePairs).doesNotContainKey('“')
    }

    @Test
    fun `forms are keyed without the tonemic marks, with l and e for its l and schwa, every headword without the caron as well`() {
        assertThat(SlovenianSupport.headwordMarks).isEqualTo(
            HeadwordMarks(
                marks = "\u0301\u0300\u0302\u030F\u0311\u0323\u0304",
                bases = "aeiourə",
                everyHeadword = true,
                replacements = mapOf("ł" to "l", "ə" to "e"),
                lemmasAsWritten = false,
                letterMarks = "\u030C",
                letterBases = "csz",
            ),
        )
        assertThat(SlovenianSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        // No letter is read for another after OCR.
        assertThat(SlovenianSupport.ocrWord("čevlji")).isEqualTo("čevlji")
        assertThat(SlovenianSupport.ocrWord("Dončić")).isEqualTo("Dončić")
    }

    @Test
    fun `words its dictionaries lack are not looked up in another language's`() {
        assertThat(SlovenianSupport.fallbackLanguage).isNull()
        assertThat(Language.SLOVENIAN.lookupDictionaryCodes).containsExactly("sl")
    }

    @Test
    fun `slovenian's own code, name, flag and letters`() {
        assertThat(Language.of("sl")).isEqualTo(Language.SLOVENIAN)
        assertThat(SlovenianSupport.languageTag).isEqualTo("sl")
        assertThat(SlovenianSupport.speechTag).isEqualTo("sl")
        assertThat(SlovenianSupport.localeLanguages).containsExactly("sl")
        assertThat(SlovenianSupport.nameIn(Locale.ENGLISH)).isEqualTo("Slovenian")
        assertThat(SlovenianSupport.codeLabel).isEqualTo("SL")
        assertThat(SlovenianSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(SlovenianSupport.flagCountries).containsExactly("si")
        assertThat(SlovenianSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(SlovenianSupport.fontLetters).isEqualTo("čšž")
        assertThat(SlovenianSupport.compoundLinks).isNull()
        assertThat(SlovenianSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(SlovenianSupport.linguaLibreItems).containsExactly("Q9063")
        assertThat(SlovenianSupport.glyph).isEqualTo("SL")
    }
}
