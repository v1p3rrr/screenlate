package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import org.junit.Test

class TeluguSupportTest {

    private val support = TeluguSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the period, the question and exclamation marks end a sentence, the danda does not`() {
        assertThat(ends("అతను ఇంటికి వెళ్ళాడు. తర్వాత")).isTrue()
        assertThat(ends("ఎక్కడికి వెళ్తున్నావు? నేను", at = "ఎక్కడికి వెళ్తున్నావు".length)).isTrue()
        assertThat(ends("అతను వచ్చాడు। తర్వాత", at = "అతను వచ్చాడు".length)).isFalse()
    }

    @Test
    fun `abbreviations and latin letter names hold the sentence`() {
        assertThat(ends("డా. రాజు వచ్చారు")).isFalse()
        assertThat(ends("ప్రొ. శర్మ")).isFalse()
        assertThat(ends("క్రీ.పూ. 300 లో", at = "క్రీ.పూ".length)).isFalse()
        assertThat(ends("5 కి.మీ. దూరం", at = "5 కి.మీ".length)).isFalse()
        assertThat(ends("రూ. 500 కోట్లు")).isFalse()
        assertThat(ends("ఉదా. చెట్టు")).isFalse()
        assertThat(ends("బి.జె.పి. పార్టీ", at = "బి.జె.పి".length)).isFalse()
        assertThat(ends("ఎన్.టి.ఆర్. నటించారు", at = "ఎన్.టి.ఆర్".length)).isFalse()
        assertThat(ends("వై.ఎస్. జగన్", at = "వై.ఎస్".length)).isFalse()
    }

    @Test
    fun `one-syllable initials and chains of them hold the sentence, but for the word of dialogue`() {
        assertThat(ends("కె. చంద్రశేఖర్ రావు మాట్లాడారు")).isFalse()
        assertThat(ends("చి. రమేష్")).isFalse()
        assertThat(ends("సా.శ.పూ. 300", at = "సా.శ.పూ".length)).isFalse()
        assertThat(ends("ఇక్కడికి రా. నేను")).isTrue()
        assertThat(ends("అతను వచ్చాడు. ఆమె")).isTrue()
    }

    @Test
    fun `vowel signs and the virama stay inside the aimed word`() {
        assertThat(support.wordStartOffset("ఈ రోజు పు", "స్తకం")).isEqualTo("పు".length)
        assertThat(support.wordStartOffset("అతను వె", "ళ్ళాడు")).isEqualTo("వె".length)
        assertThat(support.lookupStart("ిలో", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("చెట్టు", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `recognized text loses the joiners, typed text keeps them`() {
        assertThat(support.ocrLines(listOf(listOf("ఎన్\u200Cటీఆర్", "వచ్చారు\u200D"))))
            .containsExactly(listOf("ఎన్టీఆర్", "వచ్చారు"))
        assertThat(support.ocrWord("ఎన్\u200Cటీఆర్")).isEqualTo("ఎన్\u200Cటీఆర్")
    }

    @Test
    fun `headwords are also found under the modern spelling`() {
        val marks = support.headwordMarks!!
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.marks).isEmpty()
        assertThat(marks.replacements).containsExactly("\u0C01", "", "ఱ", "ర", "\u200C", "", "\u200D", "")
        assertThat(marks.endingReplacements).containsExactly("ము", "ం")
    }

    @Test
    fun `the language's endings give way to entries, and the popup looks at 16 letters`() {
        assertThat(support.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(support.defaultScanLength).isEqualTo(16)
    }

    @Test
    fun `telugu is read by downloaded models, lingua libre's recordings come first`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.TELUGU)
        assertThat(support.languageTag).isEqualTo("te")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
        assertThat(support.flagCountries).containsExactly("in")
        assertThat(support.glyph).isEqualTo("తె")
        assertThat(support.codeLabel).isEqualTo("TE")
    }

    @Test
    fun `the language's own texts are composed as dictionaries write them`() {
        val texts = listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph) +
            support.abbreviations + support.initials + support.syllableSentenceEnds
        for (text in texts) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
            assertThat(text.none { it == '\u200C' || it == '\u200D' || it == '\u0C01' }).isTrue()
        }
    }
}
