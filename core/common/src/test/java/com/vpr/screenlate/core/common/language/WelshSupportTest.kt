package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class WelshSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = WelshSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, a name's title only before a lowercase word`() {
        assertThat(ends("ffrwythau, e.e. afalau", at = 13)).isFalse()
        assertThat(ends("ffrwythau, e.e. Afalau Enlli", at = 13)).isFalse()
        assertThat(ends("y llynedd, h.y. yn 2019", at = 13)).isFalse()
        assertThat(ends("gweler hefyd cf. Jones")).isFalse()
        assertThat(ends("yr ail arg. 1995")).isFalse()
        assertThat(ends("gan y Parch. John Williams")).isFalse()
        assertThat(ends("John Jones, Ysw. a'i wraig")).isFalse()
        assertThat(ends("John Jones, Ysw. Mae'r tŷ")).isTrue()
        assertThat(ends("Mae'r tŷ yn fawr. Mae'r ardd")).isTrue()
    }

    @Test
    fun `the apostrophes of mae'r and fe'i stay inside the word, and welsh has no elisions`() {
        assertThat(WelshSupport.wordStartOffset("mae'", "r")).isEqualTo(4)
        assertThat(WelshSupport.wordStartOffset("Fe’", "i")).isEqualTo(3)
        assertThat(WelshSupport.elisions).isEmpty()
    }

    @Test
    fun `mutation rows take the rule names, a word without marks finds marked headwords only without its own entry`() {
        assertThat(WelshSupport.formTagRules["soft"]).isEqualTo("soft mutation")
        assertThat(WelshSupport.formTagRules["lenition"]).isEqualTo("soft mutation")
        assertThat(WelshSupport.formTagRules["nasalization"]).isEqualTo("nasal mutation")
        assertThat(WelshSupport.formTagRules["aspirate mutation"]).isEqualTo("aspirate mutation")
        assertThat(WelshSupport.formTagRules["prothesis-h"]).isEqualTo("h-prothesis")
        assertThat(WelshSupport.formTagRules.values.toSet())
            .containsExactly("soft mutation", "nasal mutation", "aspirate mutation", "h-prothesis")
        assertThat(WelshSupport.ignoredFormTags).containsExactly("unmutated")
        assertThat(WelshSupport.respelledOnlyWithoutEntry).isTrue()
        assertThat(WelshSupport.inflectionRulesOnlyWithoutEntry).isFalse()
        val marks = requireNotNull(WelshSupport.headwordMarks)
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.marks).isEqualTo("\u0302\u0300\u0301\u0308")
        assertThat(marks.bases).isEqualTo("aeiouwy")
        assertThat(WelshSupport.fallbackLanguage).isNull()
        assertThat(Language.WELSH.lookupDictionaryCodes).containsExactly("cy")
    }

    @Test
    fun `recordings are also searched without the circumflex`() {
        assertThat(WelshSupport.recordings("dŵr", "").map { it.title }).containsExactly("dŵr", "dwr").inOrder()
        assertThat(WelshSupport.recordings("Tŷ", "").map { it.title }).containsExactly("Tŷ", "Ty").inOrder()
        assertThat(WelshSupport.recordings("cath", "").map { it.title }).containsExactly("cath")
        assertThat(WelshSupport.recordings("dŵr", "").map { it.prefix }).containsExactly("cy", "cy")
    }

    @Test
    fun `recognized text is taken as read`() {
        assertThat(WelshSupport.ocrWord("tý")).isEqualTo("tý")
        assertThat(WelshSupport.ocrWord("mae'r")).isEqualTo("mae'r")
    }

    @Test
    fun `welsh's own code, name, flag and letters`() {
        assertThat(Language.of("cy")).isEqualTo(Language.WELSH)
        assertThat(WelshSupport.languageTag).isEqualTo("cy")
        assertThat(WelshSupport.speechTag).isEqualTo("cy")
        assertThat(WelshSupport.localeLanguages).containsExactly("cy")
        assertThat(WelshSupport.nameIn(Locale.ENGLISH)).isEqualTo("Welsh")
        assertThat(WelshSupport.codeLabel).isEqualTo("CY")
        assertThat(WelshSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(WelshSupport.flagCountries).containsExactly("gb-wls")
        assertThat(WelshSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(WelshSupport.fontLetters).isEqualTo("ŵŷ")
        assertThat(WelshSupport.fontSample).contains("ŵ")
        assertThat(WelshSupport.fontSample).contains("ŷ")
        assertThat(WelshSupport.compoundLinks).isNull()
        assertThat(WelshSupport.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
            .inOrder()
        assertThat(WelshSupport.linguaLibreItems).containsExactly("Q9309")
        assertThat(WelshSupport.glyph).isEqualTo("C")
    }
}
