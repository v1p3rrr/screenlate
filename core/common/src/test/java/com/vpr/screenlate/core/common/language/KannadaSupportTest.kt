package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import org.junit.Test

class KannadaSupportTest {

    private val support = KannadaSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the period, the question and exclamation marks and the danda end a sentence`() {
        assertThat(ends("ಅವನು ಮನೆಗೆ ಹೋದನು. ನಂತರ")).isTrue()
        assertThat(ends("ಎಲ್ಲಿಗೆ ಹೋಗುತ್ತೀಯ? ನಾನು", at = "ಎಲ್ಲಿಗೆ ಹೋಗುತ್ತೀಯ".length)).isTrue()
        assertThat(ends("ಸರ್ವೇ ಜನಾಃ ಸುಖಿನೋ ಭವಂತು। ಇದು", at = "ಸರ್ವೇ ಜನಾಃ ಸುಖಿನೋ ಭವಂತು".length)).isTrue()
    }

    @Test
    fun `abbreviations and latin letter names hold the sentence`() {
        assertThat(ends("ಡಾ. ರಾಜ್ ಬಂದರು")).isFalse()
        assertThat(ends("ಪ್ರೊ. ಶರ್ಮ")).isFalse()
        assertThat(ends("ಕ್ರಿ.ಪೂ. 300 ರಲ್ಲಿ", at = "ಕ್ರಿ.ಪೂ".length)).isFalse()
        assertThat(ends("5 ಕಿ.ಮೀ. ದೂರ", at = "5 ಕಿ.ಮೀ".length)).isFalse()
        assertThat(ends("ರೂ. 500 ಕೋಟಿ")).isFalse()
        assertThat(ends("ಶೇ. 20 ರಷ್ಟು")).isFalse()
        assertThat(ends("ಬಿ.ಜೆ.ಪಿ. ಪಕ್ಷ", at = "ಬಿ.ಜೆ.ಪಿ".length)).isFalse()
        assertThat(ends("ಎಚ್.ಡಿ. ದೇವೇಗೌಡ", at = "ಎಚ್.ಡಿ".length)).isFalse()
    }

    @Test
    fun `the months that are words or vowels hold the sentence before a number only`() {
        assertThat(ends("ಅ. 15 ರಂದು")).isFalse()
        assertThat(ends("ನ. 3 ರಂದು")).isFalse()
        assertThat(ends("ಆದರೆ ಅಮ್ಮ ಮಾತ್ರ ಆ.. ಮುಂದೆ")).isTrue()
        assertThat(ends("ಜ. 26 ರಂದು")).isFalse()
        assertThat(ends("ಡಿ. ಕೆ. ಶಿವಕುಮಾರ್")).isFalse()
    }

    @Test
    fun `one-syllable initials and chains of them hold the sentence, but for the word of dialogue`() {
        assertThat(ends("ಕೆ. ಶಿವರಾಮ್ ಮಾತನಾಡಿದರು")).isFalse()
        assertThat(ends("ಕು.ವೆಂ.ಪು. ಅವರ", at = "ಕು.ವೆಂ.ಪು".length)).isFalse()
        assertThat(ends("ಕ.ಸಾ.ಪ. ಅಧ್ಯಕ್ಷ", at = "ಕ.ಸಾ.ಪ".length)).isFalse()
        assertThat(ends("ಇಲ್ಲಿಗೆ ಬಾ. ನಾನು")).isTrue()
        assertThat(ends("ಅವನು ಬಂದನು. ಅವಳು")).isTrue()
    }

    @Test
    fun `vowel signs and the virama stay inside the aimed word`() {
        assertThat(support.wordStartOffset("ಈ ದಿನ ಪು", "ಸ್ತಕ")).isEqualTo("ಪು".length)
        assertThat(support.wordStartOffset("ಅವನು ಹೋ", "ದನು")).isEqualTo("ಹೋ".length)
        assertThat(support.lookupStart("ಿನಲ್ಲಿ", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("ಮರ", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `recognized text loses the joiners, typed text keeps them`() {
        assertThat(support.ocrLines(listOf(listOf("ಬ್ಯಾಂಕ್\u200Cನಲ್ಲಿ", "ಹೋದ\u200D"))))
            .containsExactly(listOf("ಬ್ಯಾಂಕ್ನಲ್ಲಿ", "ಹೋದ"))
        assertThat(support.ocrWord("ಬ್ಯಾಂಕ್\u200Cನಲ್ಲಿ")).isEqualTo("ಬ್ಯಾಂಕ್\u200Cನಲ್ಲಿ")
    }

    @Test
    fun `headwords with a joiner are also found without it`() {
        val marks = support.headwordMarks!!
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.marks).isEmpty()
        assertThat(marks.replacements).containsExactly("\u200C", "", "\u200D", "")
        assertThat(marks.endingReplacements).isEmpty()
    }

    @Test
    fun `the language's endings give way to entries, and the popup looks at 16 letters`() {
        assertThat(support.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(support.defaultScanLength).isEqualTo(16)
    }

    @Test
    fun `kannada is read by downloaded models, lingua libre's recordings come first`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.KANNADA)
        assertThat(support.languageTag).isEqualTo("kn")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
        assertThat(support.flagCountries).containsExactly("in")
        assertThat(support.glyph).isEqualTo("ಕ")
        assertThat(support.codeLabel).isEqualTo("KN")
    }

    @Test
    fun `the language's own texts are composed as dictionaries write them`() {
        val texts = listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph) +
            support.abbreviations + support.abbreviationsBeforeLowercase + support.initials + support.syllableSentenceEnds
        for (text in texts) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
            assertThat(text.none { it == '\u200C' || it == '\u200D' }).isTrue()
        }
    }
}
