package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class IrishSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = IrishSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, those that end one often only before a lowercase word`() {
        assertThat(ends("torthaí, m.sh. úlla", at = 13)).isFalse()
        assertThat(ends("torthaí, m.sh. Úlla Árann", at = 13)).isFalse()
        assertThat(ends("eile, m.s. níor aontaigh", at = 9)).isFalse()
        assertThat(ends("féach lch. 12")).isFalse()
        assertThat(ends("Acht uimh. 3")).isFalse()
        assertThat(ends("ag an Br. Seán")).isFalse()
        assertThat(ends("labhair an Dr. Ó Sé")).isFalse()
        assertThat(ends("as Co. Chiarraí")).isFalse()
        assertThat(ends("ag 8 r.n. agus", at = 8)).isFalse()
        assertThat(ends("ag 8 i.n. Beidh", at = 8)).isTrue()
        assertThat(ends("úlla, piorraí srl. agus eile")).isFalse()
        assertThat(ends("úlla, piorraí srl. Ansin")).isTrue()
        assertThat(ends("Gael Linn Teo. agus")).isFalse()
        assertThat(ends("Gael Linn Teo. Tá")).isTrue()
        assertThat(ends("Tá an teach mór. Tá an gairdín")).isTrue()
    }

    @Test
    fun `elided d', m' and b' start a new word, other apostrophes stay inside it`() {
        assertThat(IrishSupport.wordStartOffset("d'fh", "ág")).isEqualTo(2)
        assertThat(IrishSupport.wordStartOffset("D’fh", "ág")).isEqualTo(2)
        assertThat(IrishSupport.wordStartOffset("m'athai", "r")).isEqualTo(5)
        assertThat(IrishSupport.wordStartOffset("b'fhéidi", "r")).isEqualTo(6)
        // A tap on the elided letter looks the whole word up from its start.
        assertThat(IrishSupport.wordStartOffset("", "d'fhág")).isEqualTo(0)
        assertThat(IrishSupport.wordStartOffset("rock'n'rol", "l")).isEqualTo(10)
    }

    @Test
    fun `mutation rows take the rule names, and words are looked up with their marks as written`() {
        assertThat(IrishSupport.formTagRules["eclipsis"]).isEqualTo("eclipsis")
        assertThat(IrishSupport.formTagRules["lenition"]).isEqualTo("lenition")
        assertThat(IrishSupport.formTagRules["lenited form"]).isEqualTo("lenition")
        assertThat(IrishSupport.formTagRules["prothesis-h"]).isEqualTo("h-prothesis")
        assertThat(IrishSupport.formTagRules.values.toSet()).containsExactly("lenition", "eclipsis", "h-prothesis")
        assertThat(IrishSupport.ignoredFormTags).containsExactly("radical", "radical lenition")
        assertThat(IrishSupport.respelledOnlyWithoutEntry).isFalse()
        assertThat(IrishSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        assertThat(IrishSupport.headwordMarks).isNull()
        assertThat(IrishSupport.fallbackLanguage).isNull()
        assertThat(Language.IRISH.lookupDictionaryCodes).containsExactly("ga")
    }

    @Test
    fun `recognized text is taken as read`() {
        assertThat(IrishSupport.ocrWord("nGaillimh")).isEqualTo("nGaillimh")
        assertThat(IrishSupport.ocrWord("d'fhág")).isEqualTo("d'fhág")
    }

    @Test
    fun `irish's own code, name, flag and letters`() {
        assertThat(Language.of("ga")).isEqualTo(Language.IRISH)
        assertThat(IrishSupport.languageTag).isEqualTo("ga")
        assertThat(IrishSupport.speechTag).isEqualTo("ga")
        assertThat(IrishSupport.localeLanguages).containsExactly("ga")
        assertThat(IrishSupport.nameIn(Locale.ENGLISH)).isEqualTo("Irish")
        assertThat(IrishSupport.codeLabel).isEqualTo("GA")
        assertThat(IrishSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(IrishSupport.flagCountries).containsExactly("ie")
        assertThat(IrishSupport.fontSubsets).containsExactly("latin")
        assertThat(IrishSupport.fontLetters).isEqualTo("áéíóú")
        assertThat(IrishSupport.fontSample.toSet()).containsAtLeast('á', 'é', 'í', 'ó', 'ú')
        assertThat(IrishSupport.compoundLinks).isNull()
        assertThat(IrishSupport.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(IrishSupport.linguaLibreItems).containsExactly("Q9142")
        assertThat(IrishSupport.glyph).isEqualTo("G")
        assertThat(IrishSupport.defaultScanLength).isEqualTo(16)
    }
}
