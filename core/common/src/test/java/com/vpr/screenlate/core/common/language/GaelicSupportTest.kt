package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import java.util.Locale
import org.junit.Test

class GaelicSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = GaelicSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, those that end one often only before a lowercase word`() {
        assertThat(ends("measan, m.e. ùbhlan", at = 11)).isFalse()
        assertThat(ends("bailtean, m.e. Glaschu", at = 13)).isFalse()
        assertThat(ends("leis an Urr. Iain")).isFalse()
        assertThat(ends("thuirt an Dr. Dòmhnallach")).isFalse()
        assertThat(ends("thàinig Mgr. Caimbeul")).isFalse()
        assertThat(ends("an t-Oll. Ruairidh")).isFalse()
        assertThat(ends("ùbhlan, peuran msaa. agus eile")).isFalse()
        assertThat(ends("ùbhlan, peuran msaa. An uairsin")).isTrue()
        assertThat(ends("ùbhlan msa. Tha")).isTrue()
        assertThat(ends("Tha an taigh mòr. Tha an gàrradh")).isTrue()
    }

    @Test
    fun `elided dh', m', d', b', th' and bh' start a new word, other apostrophes stay inside it`() {
        assertThat(GaelicSupport.wordStartOffset("dh'fh", "àg")).isEqualTo(2)
        assertThat(GaelicSupport.wordStartOffset("Dh’fh", "àg")).isEqualTo(2)
        assertThat(GaelicSupport.wordStartOffset("m'athai", "r")).isEqualTo(5)
        assertThat(GaelicSupport.wordStartOffset("d'fhua", "ir")).isEqualTo(4)
        assertThat(GaelicSupport.wordStartOffset("b'", "e")).isEqualTo(0)
        assertThat(GaelicSupport.wordStartOffset("a th'an", "n")).isEqualTo(2)
        assertThat(GaelicSupport.wordStartOffset("bh'an", "n")).isEqualTo(2)
        // A tap on the elided part looks the whole word up from its start.
        assertThat(GaelicSupport.wordStartOffset("", "dh'fhàg")).isEqualTo(0)
        assertThat(GaelicSupport.wordStartOffset("rock'n'rol", "l")).isEqualTo(10)
    }

    @Test
    fun `mutation rows take the rule names, a word without marks finds marked headwords only without its own entry`() {
        assertThat(GaelicSupport.formTagRules["lenition mutation"]).isEqualTo("lenition")
        assertThat(GaelicSupport.formTagRules["lenited form"]).isEqualTo("lenition")
        assertThat(GaelicSupport.formTagRules["eclipsis mutation"]).isEqualTo("eclipsis")
        assertThat(GaelicSupport.formTagRules["mutation prothesis-h"]).isEqualTo("h-prothesis")
        assertThat(GaelicSupport.formTagRules["mutation prothesis-t"]).isEqualTo("t-prothesis")
        assertThat(GaelicSupport.formTagRules.values.toSet())
            .containsExactly("lenition", "eclipsis", "t-prothesis", "h-prothesis")
        assertThat(GaelicSupport.ignoredFormTags).containsExactly("mutation mutation-radical")
        assertThat(GaelicSupport.respelledOnlyWithoutEntry).isTrue()
        assertThat(GaelicSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        val marks = requireNotNull(GaelicSupport.headwordMarks)
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.marks).isEqualTo("̀́")
        assertThat(marks.bases).isEqualTo("aeiou")
        assertThat(GaelicSupport.fallbackLanguage).isNull()
        assertThat(Language.SCOTTISH_GAELIC.lookupDictionaryCodes).containsExactly("gd")
    }

    @Test
    fun `recognized text is taken as read`() {
        assertThat(GaelicSupport.ocrWord("h-Alba")).isEqualTo("h-Alba")
        assertThat(GaelicSupport.ocrWord("dh'fhàg")).isEqualTo("dh'fhàg")
    }

    @Test
    fun `the russian interface names it scottish gaelic, the others as the system does`() {
        assertThat(GaelicSupport.nameIn(Locale.forLanguageTag("ru"))).isEqualTo("шотландский гэльский")
        assertThat(Language.SCOTTISH_GAELIC.displayName(Locale.forLanguageTag("ru"))).isEqualTo("Шотландский гэльский")
        assertThat(GaelicSupport.nameIn(Locale.ENGLISH)).isEqualTo("Scottish Gaelic")
        assertThat(IrishSupport.nameIn(Locale.forLanguageTag("ru"))).isEqualTo("ирландский")
    }

    @Test
    fun `gaelic's own code, flag and letters`() {
        assertThat(Language.of("gd")).isEqualTo(Language.SCOTTISH_GAELIC)
        assertThat(GaelicSupport.languageTag).isEqualTo("gd")
        assertThat(GaelicSupport.speechTag).isEqualTo("gd")
        assertThat(GaelicSupport.localeLanguages).containsExactly("gd")
        assertThat(GaelicSupport.codeLabel).isEqualTo("GD")
        assertThat(GaelicSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(GaelicSupport.flagCountries).containsExactly("gb-sct")
        assertThat(GaelicSupport.fontSubsets).containsExactly("latin")
        assertThat(GaelicSupport.fontLetters).isEqualTo("àèìòù")
        assertThat(GaelicSupport.fontSample.lowercase().toSet()).containsAtLeast('à', 'è', 'ì', 'ò', 'ù')
        assertThat(GaelicSupport.compoundLinks).isNull()
        assertThat(GaelicSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(GaelicSupport.linguaLibreItems).containsExactly("Q9314")
        assertThat(GaelicSupport.glyph).isEqualTo("G")
        assertThat(GaelicSupport.defaultScanLength).isEqualTo(16)
    }
}
