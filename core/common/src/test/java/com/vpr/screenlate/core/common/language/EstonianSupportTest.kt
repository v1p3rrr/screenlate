package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class EstonianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = EstonianSupport.endsSentence(text, at)

    @Test
    fun `dates, ordinals and abbreviations do not end a sentence`() {
        assertThat(ends("24. veebruar")).isFalse()
        assertThat(ends("2020. aastal")).isFalse()
        assertThat(ends("2020. a. lõpus", "2020. a".length)).isFalse()
        assertThat(ends("u. 5 kilo")).isFalse()
        assertThat(ends("vt lk. 12")).isFalse()
        assertThat(ends("dr. Tamm")).isFalse()
        assertThat(ends("kõik v.a. Tartu", "kõik v.a".length)).isFalse()
        assertThat(ends("õunad, pirnid jne. ja muu")).isFalse()
        assertThat(ends("õunad, pirnid jne. Siis")).isTrue()
        assertThat(ends("Pärnu mnt. 10", "Pärnu mnt".length)).isFalse()
        assertThat(ends("See juhtus 1918. aastal. Siis", "See juhtus 1918. aastal".length)).isTrue()
        assertThat(ends("Maja on suur. Aed on väike")).isTrue()
    }

    @Test
    fun `an ending after a hyphen is looked up with the hyphen after a number or an abbreviation in capitals`() {
        assertThat(EstonianSupport.partStarts("5-ga", 0, "5-g".length)).containsExactly(1, 2).inOrder()
        assertThat(EstonianSupport.partStarts("NATO-ga", 0, "NATO-g".length)).containsExactly(4, 5).inOrder()
        // A hyphen after a word joins a compound or a pair.
        assertThat(EstonianSupport.partStarts("eesti-inglise", 0, "eesti-i".length)).containsExactly(6)
        assertThat(EstonianSupport.lookupStart("-ga", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `low-high quotes close with the english opening quote, english quotes and guillemets too`() {
        assertThat(EstonianSupport.quotePairs).containsEntry('„', '“')
        assertThat(EstonianSupport.quotePairs).containsEntry('“', '”')
        assertThat(EstonianSupport.quotePairs).containsEntry('«', '»')
        assertThat(EstonianSupport.quotePairs).doesNotContainKey('»')
    }

    @Test
    fun `entries and rules together, compounds split without links`() {
        // pole shows its own entry, then olema through the negative rule.
        assertThat(EstonianSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        assertThat(EstonianSupport.compoundLinks).isEmpty()
        assertThat(EstonianSupport.headwordMarks).isNull()
        assertThat(EstonianSupport.fallbackLanguage).isNull()
        assertThat(EstonianSupport.formTagAliases).isEmpty()
        assertThat(Language.ESTONIAN.lookupDictionaryCodes).containsExactly("et")
        assertThat(EstonianSupport.defaultScanLength).isEqualTo(16)
    }

    @Test
    fun `recognized text is taken as read, the bundle tries the tilde`() {
        assertThat(EstonianSupport.ocrWord("möte")).isEqualTo("möte")
        assertThat(EstonianSupport.ocrWord("NATO-ga")).isEqualTo("NATO-ga")
    }

    @Test
    fun `estonian's own code, flag and letters`() {
        assertThat(Language.of("et")).isEqualTo(Language.ESTONIAN)
        assertThat(EstonianSupport.languageTag).isEqualTo("et")
        assertThat(EstonianSupport.localeLanguages).containsExactly("et")
        assertThat(EstonianSupport.codeLabel).isEqualTo("ET")
        assertThat(EstonianSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(EstonianSupport.flagCountries).containsExactly("ee")
        assertThat(EstonianSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(EstonianSupport.fontSample.lowercase().toSet()).containsAtLeast('õ', 'ä', 'ö', 'ü', 'š', 'ž')
        assertThat(EstonianSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(EstonianSupport.glyph).isEqualTo("E")
    }
}
