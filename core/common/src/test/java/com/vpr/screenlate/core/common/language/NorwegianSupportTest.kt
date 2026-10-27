package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class NorwegianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = NorwegianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, words and era names before a capital do`() {
        assertThat(ends("Frukt, f.eks. epler", at = 12)).isFalse()
        assertThat(ends("Vi ses kl. 8")).isFalse()
        assertThat(ends("Det var bl.a. Anna", at = 12)).isFalse()
        assertThat(ends("Det koster ca. 300 kroner")).isFalse()
        assertThat(ends("Han bor i St. Petersburg")).isFalse()
        assertThat(ends("Se s. 12")).isFalse()
        assertThat(ends("Rom ble grunnlagt 753 f.Kr. Byen vokste.", at = 26)).isTrue()
        assertThat(ends("I 753 f.Kr. ble Rom grunnlagt.", at = 10)).isFalse()
        assertThat(ends("Epler, pærer osv. Så gikk vi.", at = 16)).isTrue()
        assertThat(ends("epler, pærer osv. som vi kjøpte", at = 16)).isFalse()
        assertThat(ends("Anna, Per m.fl. Så gikk de.", at = 14)).isTrue()
    }

    @Test
    fun `ordinals take a period, a year before a capital ends the sentence`() {
        assertThat(NorwegianSupport.ordinalPeriod).isTrue()
        assertThat(ends("Den 17. mai feirer vi")).isFalse()
        assertThat(ends("Den 17. Mai")).isFalse()
        assertThat(ends("Han kom i 1999. Så")).isTrue()
    }

    @Test
    fun `ocr's a with a hook, a circumflex or a diaeresis is the ring a, other letters and app text stay`() {
        assertThat(NorwegianSupport.ocrWord("pả")).isEqualTo("på")
        assertThat(NorwegianSupport.ocrWord("Âr")).isEqualTo("År")
        assertThat(NorwegianSupport.ocrWord("gä")).isEqualTo("gå")
        assertThat(NorwegianSupport.ocrWord("ÄR")).isEqualTo("ÅR")
        assertThat(NorwegianSupport.ocrWord("søster")).isEqualTo("søster")
        assertThat(NorwegianSupport.ocrWord("være")).isEqualTo("være")
        assertThat(NorwegianSupport.ocrLines(listOf(listOf("pả", "vei")))).containsExactly(listOf("på", "vei"))
    }

    @Test
    fun `quotes are guillemets with single quotes inside`() {
        assertThat(NorwegianSupport.quotePairs).containsEntry('«', '»')
        assertThat(NorwegianSupport.quotePairs).containsEntry('‘', '’')
    }

    @Test
    fun `bokmål's tag for services, norwegian's code for the app and every written form's locale`() {
        assertThat(Language.NORWEGIAN.code).isEqualTo("no")
        assertThat(Language.NORWEGIAN.dictionaryCode).isEqualTo("no")
        assertThat(Language.of("nb")).isEqualTo(Language.NORWEGIAN)
        assertThat(Language.of("nn")).isEqualTo(Language.NORWEGIAN)
        assertThat(NorwegianSupport.languageTag).isEqualTo("nb")
        assertThat(NorwegianSupport.speechTag).isEqualTo("nb")
        assertThat(NorwegianSupport.localeLanguages).containsExactly("no", "nb", "nn")
        assertThat(NorwegianSupport.nameIn(Locale.ENGLISH)).isEqualTo("Norwegian")
        assertThat(NorwegianSupport.codeLabel).isEqualTo("NO")
    }

    @Test
    fun `compounds of up to 24 letters split at s and e, latin letters, lingua libre first`() {
        assertThat(NorwegianSupport.defaultScanLength).isEqualTo(24)
        assertThat(NorwegianSupport.compoundLinks).containsExactly("s", "e").inOrder()
        assertThat(NorwegianSupport.capitalizedNouns).isFalse()
        assertThat(NorwegianSupport.fontSubsets).containsExactly("latin")
        assertThat(NorwegianSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(NorwegianSupport.linguaLibreItems).containsExactly("Q9043", "Q25167").inOrder()
        assertThat(NorwegianSupport.flagCountries).containsExactly("no")
    }
}
