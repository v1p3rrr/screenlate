package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class FinnishSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = FinnishSupport.endsSentence(text, at)

    /** The offset of the word start for a tap on [text]'s character at [at]. */
    private fun start(text: String, at: Int) =
        FinnishSupport.wordStartOffset(text.substring(0, at), text.substring(at, at + 1), text.substring(at + 1))

    @Test
    fun `ordinals, dates and abbreviations do not end a sentence`() {
        assertThat(ends("24. toukokuuta")).isFalse()
        assertThat(ends("3. kerran")).isFalse()
        assertThat(ends("hedelmiä, esim. omenoita")).isFalse()
        assertThat(ends("hedelmiä, esim. Omenoita")).isFalse()
        assertThat(ends("niin sanottu, mm. Helsinki")).isFalse()
        assertThat(ends("ks. sivu 4")).isFalse()
        assertThat(ends("omenoita, päärynöitä jne. ja muuta")).isFalse()
        assertThat(ends("omenoita, päärynöitä jne. Sitten")).isTrue()
        assertThat(ends("500 eKr. alkaen")).isFalse()
        assertThat(ends("Se tapahtui 1918. Sitten")).isTrue()
        assertThat(ends("Talo on suuri. Piha on pieni")).isTrue()
    }

    @Test
    fun `an ending after a colon belongs to the word or number before it`() {
        assertThat(start("EU:n", 3)).isEqualTo(3)
        assertThat(start("STT:lle", 4)).isEqualTo(4)
        assertThat(start("STT:lle", 5)).isEqualTo(5)
        assertThat(start("STT:lle", 6)).isEqualTo(6)
        assertThat(start("vuoden 2023:ssa", 12)).isEqualTo(5)
        assertThat(start("Nato:on", 5)).isEqualTo(5)
        // The word before the colon keeps its own start.
        assertThat(start("EU:n", 1)).isEqualTo(1)
        // A capital, a digit, a space or more than four letters after the colon start a word of their own.
        assertThat(start("Huom:Talo", 5)).isEqualTo(0)
        assertThat(start("klo 12:30", 7)).isEqualTo(0)
        assertThat(start("sana: talo", 6)).isEqualTo(0)
        assertThat(start("lisää:taloissa", 7)).isEqualTo(1)
        assertThat(start(":n", 1)).isEqualTo(0)
    }

    @Test
    fun `an ending after a hyphen is looked up with the hyphen only after a number`() {
        assertThat(FinnishSupport.partStarts("1990-luvulla", 0, "1990-l".length)).containsExactly(4, 5).inOrder()
        assertThat(FinnishSupport.partStarts("20-vuotias", 0, "20-v".length)).containsExactly(2, 3).inOrder()
        // A hyphen after an abbreviation or a word joins a compound.
        assertThat(FinnishSupport.partStarts("EU-maat", 0, "EU-m".length)).containsExactly(3)
        assertThat(FinnishSupport.partStarts("linja-auto", 0, "linja-a".length)).containsExactly(6)
        assertThat(FinnishSupport.lookupStart("-luvulla", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(FinnishSupport.lookupStart("- luvulla", latinAsNative = false)).isNull()
    }

    @Test
    fun `right quotes on both sides, guillemets pointing one way, english quotes too`() {
        assertThat(FinnishSupport.quotePairs).containsEntry('”', '”')
        assertThat(FinnishSupport.quotePairs).containsEntry('»', '»')
        assertThat(FinnishSupport.quotePairs).containsEntry('“', '”')
        assertThat(FinnishSupport.quotePairs).doesNotContainKey('«')
        assertThat(FinnishSupport.quotePairs).doesNotContainKey('„')
    }

    @Test
    fun `rule results after the entries, rule chains only without a row, compounds split without links`() {
        assertThat(FinnishSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        assertThat(FinnishSupport.ruleChainsOnlyWithoutRow).isTrue()
        assertThat(FinnishSupport.compoundLinks).isEmpty()
        assertThat(FinnishSupport.headwordMarks).isNull()
        assertThat(FinnishSupport.fallbackLanguage).isNull()
        assertThat(Language.FINNISH.lookupDictionaryCodes).containsExactly("fi")
        assertThat(FinnishSupport.defaultScanLength).isEqualTo(24)
    }

    @Test
    fun `recognized text is taken as read`() {
        assertThat(FinnishSupport.ocrWord("EU:n")).isEqualTo("EU:n")
        assertThat(FinnishSupport.ocrWord("1990-luvulla")).isEqualTo("1990-luvulla")
    }

    @Test
    fun `finnish's own code, flag and letters`() {
        assertThat(Language.of("fi")).isEqualTo(Language.FINNISH)
        assertThat(FinnishSupport.languageTag).isEqualTo("fi")
        assertThat(FinnishSupport.localeLanguages).containsExactly("fi")
        assertThat(FinnishSupport.codeLabel).isEqualTo("FI")
        assertThat(FinnishSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(FinnishSupport.flagCountries).containsExactly("fi")
        assertThat(FinnishSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(FinnishSupport.fontSample.lowercase().toSet()).containsAtLeast('ä', 'ö', 'å')
        assertThat(FinnishSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(FinnishSupport.glyph).isEqualTo("S")
    }
}
