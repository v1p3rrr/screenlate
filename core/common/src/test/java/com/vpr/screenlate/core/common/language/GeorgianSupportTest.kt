package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import java.util.Locale
import org.junit.Test

class GeorgianSupportTest {

    private val support = GeorgianSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `a period, a question mark and an exclamation mark end a sentence`() {
        assertThat(ends("წყალი ცივია. ის წავიდა")).isTrue()
        assertThat(ends("სად ხარ? აქ ვარ", at = "სად ხარ".length)).isTrue()
        assertThat(ends("რა კარგია! წავიდეთ", at = "რა კარგია".length)).isTrue()
    }

    @Test
    fun `abbreviations hold the sentence`() {
        assertThat(ends("ქ. თბილისი")).isFalse()
        assertThat(ends("მაგ. ის მოვიდა")).isFalse()
        assertThat(ends("1991 წ. 9 აპრილს")).isFalse()
        assertThat(ends("ძვ. წ. IV საუკუნე")).isFalse()
        assertThat(ends("ძვ. წ. IV საუკუნე", at = "ძვ. წ".length)).isFalse()
        assertThat(ends("ე.ი. ის მოვიდა", at = "ე.ი".length)).isFalse()
        assertThat(ends("იხ. გვ. 12", at = "იხ. გვ".length)).isFalse()
        assertThat(ends("პროფ. ბერიძე")).isFalse()
        // An abbreviation in a heading in Mtavruli capitals.
        assertThat(ends("Ქ. ᲗᲑᲘᲚᲘᲡᲘ")).isFalse()
    }

    @Test
    fun `low and high quotes and guillemets pair`() {
        assertThat(support.quotePairs['„']).isEqualTo('“')
        assertThat(support.quotePairs['«']).isEqualTo('»')
    }

    @Test
    fun `a lookup starts at the start of the aimed word`() {
        assertThat(support.wordStartOffset("ჩემი სახლ", "ში")).isEqualTo(4)
        assertThat(support.wordStartOffset("ᲡᲐᲥ", "ᲐᲠᲗᲕᲔᲚᲝ")).isEqualTo(3)
        assertThat(support.lookupStart("წყალი", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `georgian is read by tesseract, the page keeps the phone's sans-serif, lookups take 24 characters`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.GEORGIAN)
        assertThat(support.systemFonts.sans).isEmpty()
        assertThat(support.systemFonts.serif.first()).isEqualTo("NotoSerifGeorgian-Regular")
        assertThat(support.systemFonts.unicodeRange).contains("U+10A0-10FF")
        assertThat(support.systemFonts.unicodeRange).contains("U+1C90-1CBA")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
        assertThat(support.flagCountries).containsExactly("ge")
        assertThat(support.glyph).isEqualTo("ქ")
        assertThat(support.headwordMarks).isNull()
        assertThat(support.defaultScanLength).isEqualTo(24)
    }

    @Test
    fun `the language is called Georgian as the phone names it`() {
        assertThat(support.nameIn(Locale.ENGLISH)).isEqualTo("Georgian")
        val russian = Locale.forLanguageTag("ru")
        assertThat(support.nameIn(russian)).isEqualTo(Locale.forLanguageTag("ka").getDisplayLanguage(russian))
    }

    @Test
    fun `the language's own texts are composed and in the ordinary letters`() {
        val texts = listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph) +
            support.abbreviations
        for (text in texts) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
            // Mtavruli capitals (U+1C90-1CBF) only in headings; the abbreviations are matched lowercased.
            assertThat(text.none { it in '\u1C90'..'\u1CBF' }).isTrue()
        }
    }
}
