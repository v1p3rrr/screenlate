package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class AzerbaijaniSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = AzerbaijaniSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the ones that close a list only before a lowercase word`() {
        assertThat(ends("prof. Əliyev")).isFalse()
        assertThat(ends("məs. kitab")).isFalse()
        assertThat(ends("kitablar, dəftərlər və s. alındı", "kitablar, dəftərlər və s".length)).isFalse()
        assertThat(ends("kitablar, dəftərlər və s. Sonra", "kitablar, dəftərlər və s".length)).isTrue()
        assertThat(ends("300 mln. manat", "300 mln".length)).isFalse()
        assertThat(ends("eramızdan əvvəl 300-cü il, e.ə. dövr", "eramızdan əvvəl 300-cü il, e.ə".length)).isFalse()
        assertThat(ends("Bakı şəh. Nizami küç. 5", "Bakı şəh".length)).isFalse()
        assertThat(ends("Ev böyükdür. Bağ kiçikdir")).isTrue()
    }

    @Test
    fun `an ending after a hyphen is looked up with the hyphen after a number or an abbreviation in capitals`() {
        assertThat(AzerbaijaniSupport.partStarts("2020-ci", 0, "2020-c".length)).containsExactly(4, 5).inOrder()
        assertThat(AzerbaijaniSupport.partStarts("ABŞ-da", 0, "ABŞ-d".length)).containsExactly(3, 4).inOrder()
        // A hyphen after a word joins a compound or a pair.
        assertThat(AzerbaijaniSupport.partStarts("sosial-iqtisadi", 0, "sosial-i".length)).containsExactly(7)
        assertThat(AzerbaijaniSupport.lookupStart("-ci", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `english quotes and guillemets`() {
        assertThat(AzerbaijaniSupport.quotePairs).containsEntry('“', '”')
        assertThat(AzerbaijaniSupport.quotePairs).containsEntry('«', '»')
        assertThat(AzerbaijaniSupport.quotePairs).doesNotContainKey('»')
    }

    @Test
    fun `rules only for words without an entry, no compounds`() {
        // belə "so" shows its own entry, not bel "waist" through the dative.
        assertThat(AzerbaijaniSupport.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(AzerbaijaniSupport.compoundLinks).isNull()
        assertThat(AzerbaijaniSupport.headwordMarks).isNull()
        assertThat(AzerbaijaniSupport.fallbackLanguage).isNull()
        assertThat(AzerbaijaniSupport.formTagAliases).isEmpty()
        assertThat(Language.AZERBAIJANI.lookupDictionaryCodes).containsExactly("az")
        assertThat(AzerbaijaniSupport.defaultScanLength).isEqualTo(16)
    }

    @Test
    fun `recognized text is taken as read, the bundle tries schwa and dotless i`() {
        assertThat(AzerbaijaniSupport.ocrWord("seher")).isEqualTo("seher")
        assertThat(AzerbaijaniSupport.ocrWord("ABŞ-da")).isEqualTo("ABŞ-da")
    }

    @Test
    fun `azerbaijani's own code, flag and letters`() {
        assertThat(Language.of("az")).isEqualTo(Language.AZERBAIJANI)
        assertThat(AzerbaijaniSupport.languageTag).isEqualTo("az")
        assertThat(AzerbaijaniSupport.localeLanguages).containsExactly("az")
        assertThat(AzerbaijaniSupport.codeLabel).isEqualTo("AZ")
        assertThat(AzerbaijaniSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(AzerbaijaniSupport.flagCountries).containsExactly("az")
        assertThat(AzerbaijaniSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(AzerbaijaniSupport.fontSample.toSet()).containsAtLeast('ə', 'ı', 'İ', 'ğ', 'ö', 'ü', 'ç', 'ş', 'q', 'x')
        assertThat(AzerbaijaniSupport.defaultAudioSources)
            .containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
        assertThat(AzerbaijaniSupport.glyph).isEqualTo("Ə")
    }
}
