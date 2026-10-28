package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class AlbanianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = AlbanianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, words and era names before a capital do`() {
        assertThat(ends("Frutat, p.sh. mollët", at = 12)).isFalse()
        assertThat(ends("Frutat, psh. mollët")).isFalse()
        assertThat(ends("Pra, d.m.th. ai erdhi", at = 11)).isFalse()
        assertThat(ends("Shih nr. 5 të ligjit")).isFalse()
        assertThat(ends("Z. Hoxha erdhi")).isFalse()
        assertThat(ends("Takimi me prof. Kadare")).isFalse()
        assertThat(ends("Kalaja e shek. XIV")).isFalse()
        assertThat(ends("Libri, fq. 12")).isFalse()
        assertThat(ends("Ai banon në rr. Durrësit")).isFalse()
        assertThat(ends("Libra, revista etj. Pastaj shkuam.")).isTrue()
        assertThat(ends("libra, revista etj. dhe të tjera")).isFalse()
        assertThat(ends("Cezari vdiq në vitin 44 p.e.s. Pastaj", at = 29)).isTrue()
        assertThat(ends("Në vitin 44 p.e.s. vdiq Cezari", at = 17)).isFalse()
        assertThat(ends("Rruga është 5 km. E gjatë")).isTrue()
        assertThat(ends("Mbledhja përfundoi. Pastaj ikën")).isTrue()
    }

    @Test
    fun `ordinals take an ending, so a number with a period ends the sentence`() {
        assertThat(AlbanianSupport.ordinalPeriod).isFalse()
        assertThat(ends("Ai lindi në 1999. Pastaj")).isTrue()
        assertThat(ends("pi është 3.14")).isFalse()
    }

    @Test
    fun `english quotes and guillemets pair, low quotes do not`() {
        assertThat(AlbanianSupport.quotePairs).containsEntry('“', '”')
        assertThat(AlbanianSupport.quotePairs).containsEntry('«', '»')
        assertThat(AlbanianSupport.quotePairs).doesNotContainKey('„')
        assertThat(AlbanianSupport.quotePairs).doesNotContainKey('»')
    }

    @Test
    fun `the word after an elided s, ç, t or m starts a new word`() {
        assertThat(AlbanianSupport.wordStartOffset("Ai s'", "ka")).isEqualTo(0)
        assertThat(AlbanianSupport.wordStartOffset("s’", "kam")).isEqualTo(0)
        assertThat(AlbanianSupport.wordStartOffset("Ç'", "është")).isEqualTo(0)
        assertThat(AlbanianSupport.wordStartOffset("ç’", "të")).isEqualTo(0)
        assertThat(AlbanianSupport.wordStartOffset("për t'", "i")).isEqualTo(0)
        assertThat(AlbanianSupport.wordStartOffset("m’", "u")).isEqualTo(0)
        // Other apostrophes join the word, as does one after a longer word ending in s.
        assertThat(AlbanianSupport.wordStartOffset("Paris'", "s")).isEqualTo(6)
        assertThat(AlbanianSupport.wordStartOffset("don'", "t")).isEqualTo(4)
    }

    @Test
    fun `ocr's e with a caron is the e with a diaeresis, other letters stay`() {
        assertThat(AlbanianSupport.ocrWord("shtěpi")).isEqualTo("shtëpi")
        assertThat(AlbanianSupport.ocrWord("ĚSHTË")).isEqualTo("ËSHTË")
        assertThat(AlbanianSupport.ocrWord("çështje")).isEqualTo("çështje")
        assertThat(AlbanianSupport.ocrWord("éshtë")).isEqualTo("éshtë")
        assertThat(AlbanianSupport.ocrLines(listOf(listOf("shtěpi", "ěshtë")))).containsExactly(listOf("shtëpi", "është"))
    }

    @Test
    fun `no keys without the letters' marks, and no other language's dictionaries`() {
        assertThat(AlbanianSupport.headwordMarks).isNull()
        assertThat(AlbanianSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        assertThat(AlbanianSupport.fallbackLanguage).isNull()
        assertThat(Language.ALBANIAN.lookupDictionaryCodes).containsExactly("sq")
    }

    @Test
    fun `albanian's own code, name, flag and letters`() {
        assertThat(Language.of("sq")).isEqualTo(Language.ALBANIAN)
        assertThat(AlbanianSupport.languageTag).isEqualTo("sq")
        assertThat(AlbanianSupport.speechTag).isEqualTo("sq")
        assertThat(AlbanianSupport.localeLanguages).containsExactly("sq")
        assertThat(AlbanianSupport.nameIn(Locale.ENGLISH)).isEqualTo("Albanian")
        assertThat(AlbanianSupport.codeLabel).isEqualTo("SQ")
        assertThat(AlbanianSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(AlbanianSupport.flagCountries).containsExactly("al")
        assertThat(AlbanianSupport.fontSubsets).containsExactly("latin")
        assertThat(AlbanianSupport.fontLetters).isEqualTo("ëç")
        assertThat(AlbanianSupport.compoundLinks).isNull()
        assertThat(AlbanianSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(AlbanianSupport.linguaLibreItems).containsExactly("Q8748")
        assertThat(AlbanianSupport.glyph).isEqualTo("S")
    }
}
