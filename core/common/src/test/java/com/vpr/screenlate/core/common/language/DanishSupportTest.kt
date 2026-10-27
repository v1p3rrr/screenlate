package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class DanishSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = DanishSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, words and era names before a capital do`() {
        assertThat(ends("Frugt, f.eks. æbler", at = 12)).isFalse()
        assertThat(ends("Vi ses kl. 8")).isFalse()
        assertThat(ends("Det var bl.a. Anna", at = 12)).isFalse()
        assertThat(ends("Prisen er hhv. 10 og 20", at = 13)).isFalse()
        assertThat(ends("Han købte 3 stk. Tuborg", at = 15)).isFalse()
        assertThat(ends("Han bor i St. Petersborg")).isFalse()
        assertThat(ends("Se s. 12")).isFalse()
        assertThat(ends("Rom blev grundlagt 753 f.Kr. Byen voksede.", at = 27)).isTrue()
        assertThat(ends("I 753 f.Kr. blev Rom grundlagt.", at = 10)).isFalse()
        assertThat(ends("Æbler, pærer osv. Så gik vi.", at = 16)).isTrue()
        assertThat(ends("æbler, pærer osv. som vi købte", at = 16)).isFalse()
        assertThat(ends("Skatter, afgifter mv. Det er dyrt.", at = 20)).isTrue()
    }

    @Test
    fun `ordinals take a period, a year before a capital ends the sentence`() {
        assertThat(DanishSupport.ordinalPeriod).isTrue()
        assertThat(ends("Den 5. maj fejrer vi")).isFalse()
        assertThat(ends("Den 5. Maj")).isFalse()
        assertThat(ends("Han kom i 1999. Så")).isTrue()
    }

    @Test
    fun `ocr's a with a hook, a circumflex or a diaeresis is the ring a, other letters and app text stay`() {
        assertThat(DanishSupport.ocrWord("pả")).isEqualTo("på")
        assertThat(DanishSupport.ocrWord("Âr")).isEqualTo("År")
        assertThat(DanishSupport.ocrWord("gä")).isEqualTo("gå")
        assertThat(DanishSupport.ocrWord("ÄR")).isEqualTo("ÅR")
        assertThat(DanishSupport.ocrWord("søster")).isEqualTo("søster")
        assertThat(DanishSupport.ocrWord("være")).isEqualTo("være")
        assertThat(DanishSupport.ocrLines(listOf(listOf("pả", "vej")))).containsExactly(listOf("på", "vej"))
    }

    @Test
    fun `quotes open with the right-pointing guillemet and close with the left-pointing one`() {
        assertThat(DanishSupport.quotePairs).containsEntry('»', '«')
        assertThat(DanishSupport.quotePairs).containsEntry('“', '”')
        assertThat(DanishSupport.quotePairs).doesNotContainKey('«')
        assertThat(DanishSupport.quotePairs).doesNotContainKey('„')
    }

    @Test
    fun `danish's own code, name and voice`() {
        assertThat(Language.of("da")).isEqualTo(Language.DANISH)
        assertThat(DanishSupport.languageTag).isEqualTo("da")
        assertThat(DanishSupport.speechTag).isEqualTo("da")
        assertThat(DanishSupport.localeLanguages).containsExactly("da")
        assertThat(DanishSupport.nameIn(Locale.ENGLISH)).isEqualTo("Danish")
        assertThat(DanishSupport.codeLabel).isEqualTo("DA")
    }

    @Test
    fun `compounds of up to 24 letters split at s and e, latin letters, lingua libre first`() {
        assertThat(DanishSupport.defaultScanLength).isEqualTo(24)
        assertThat(DanishSupport.compoundLinks).containsExactly("s", "e").inOrder()
        assertThat(DanishSupport.capitalizedNouns).isFalse()
        assertThat(DanishSupport.fontSubsets).containsExactly("latin")
        assertThat(DanishSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(DanishSupport.linguaLibreItems).containsExactly("Q9035")
        assertThat(DanishSupport.flagCountries).containsExactly("dk")
    }
}
