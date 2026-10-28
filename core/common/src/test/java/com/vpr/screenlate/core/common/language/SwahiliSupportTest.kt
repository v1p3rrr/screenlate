package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class SwahiliSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = SwahiliSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, words and era names before a capital do`() {
        assertThat(ends("vyakula, k.m. wali na maharage", at = 12)).isFalse()
        assertThat(ends("vyakula, k.m. Ugali", at = 12)).isFalse()
        assertThat(ends("Aliyepiga simu alikuwa Bw. Juma")).isFalse()
        assertThat(ends("Bi. Amina alifika")).isFalse()
        assertThat(ends("marehemu Dkt. Mwakyusa")).isFalse()
        assertThat(ends("Daktari Mkuu, Dk. Rashid")).isFalse()
        assertThat(ends("chini ya Mhe. Rais")).isFalse()
        assertThat(ends("Chuo Kikuu cha Mt. Augustino")).isFalse()
        assertThat(ends("wakati Nd. Sawe alipofika")).isFalse()
        assertThat(ends("Felix Macheo, S.L.P. 30675", at = 19)).isFalse()
        assertThat(ends("alikamatwa tar. 15 Desemba")).isFalse()
        assertThat(ends("muziki, filamu n.k. Hata hivyo", at = 18)).isTrue()
        assertThat(ends("muziki, filamu n.k. na michezo", at = 18)).isFalse()
        assertThat(ends("karne ya 12 BK. Mji ulikua")).isTrue()
        assertThat(ends("mwaka 500 KK. aliishi")).isFalse()
        assertThat(ends("Mkutano ulimalizika. Kisha")).isTrue()
    }

    @Test
    fun `ordinals are written without a period, so a number with one ends the sentence`() {
        assertThat(SwahiliSupport.ordinalPeriod).isFalse()
        assertThat(ends("Alizaliwa mwaka 1999. Kisha")).isTrue()
        assertThat(ends("pi ni 3.14")).isFalse()
    }

    @Test
    fun `english quotes pair, guillemets and low quotes do not`() {
        assertThat(SwahiliSupport.quotePairs).containsEntry('“', '”')
        assertThat(SwahiliSupport.quotePairs).doesNotContainKey('«')
        assertThat(SwahiliSupport.quotePairs).doesNotContainKey('„')
    }

    @Test
    fun `the apostrophe of ng' stays inside the word`() {
        assertThat(SwahiliSupport.wordStartOffset("ng'", "ombe")).isEqualTo(3)
        assertThat(SwahiliSupport.wordStartOffset("Ng’", "ombe")).isEqualTo(3)
        assertThat(SwahiliSupport.elisions).isEmpty()
    }

    @Test
    fun `prefix rules only for a word without an entry, no keys without marks, no other language's dictionaries`() {
        assertThat(SwahiliSupport.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(SwahiliSupport.headwordMarks).isNull()
        assertThat(SwahiliSupport.fallbackLanguage).isNull()
        assertThat(Language.SWAHILI.lookupDictionaryCodes).containsExactly("sw")
    }

    @Test
    fun `recognized text is taken as read`() {
        assertThat(SwahiliSupport.ocrWord("ng'ombe")).isEqualTo("ng'ombe")
        assertThat(SwahiliSupport.ocrWord("alisoma")).isEqualTo("alisoma")
    }

    @Test
    fun `swahili's own code, name, flag and letters`() {
        assertThat(Language.of("sw")).isEqualTo(Language.SWAHILI)
        assertThat(SwahiliSupport.languageTag).isEqualTo("sw")
        assertThat(SwahiliSupport.speechTag).isEqualTo("sw")
        assertThat(SwahiliSupport.localeLanguages).containsExactly("sw")
        assertThat(SwahiliSupport.nameIn(Locale.ENGLISH)).isEqualTo("Swahili")
        assertThat(SwahiliSupport.codeLabel).isEqualTo("SW")
        assertThat(SwahiliSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(SwahiliSupport.flagCountries).containsExactly("tz")
        assertThat(SwahiliSupport.fontSubsets).containsExactly("latin")
        assertThat(SwahiliSupport.fontLetters).isEmpty()
        assertThat(SwahiliSupport.compoundLinks).isNull()
        assertThat(SwahiliSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(SwahiliSupport.linguaLibreItems).containsExactly("Q7838")
        assertThat(SwahiliSupport.glyph).isEqualTo("SW")
    }
}
