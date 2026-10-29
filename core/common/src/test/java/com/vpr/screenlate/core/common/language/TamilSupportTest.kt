package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import org.junit.Test

class TamilSupportTest {

    private val support = TamilSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the period, the question and exclamation marks end a sentence, the danda does not`() {
        assertThat(ends("அவர் வீட்டுக்குச் சென்றார். பின்னர்")).isTrue()
        assertThat(ends("எங்கே போகிறாய்? நான்", at = "எங்கே போகிறாய்".length)).isTrue()
        assertThat(ends("அவர் வந்தார்। பின்னர்", at = "அவர் வந்தார்".length)).isFalse()
    }

    @Test
    fun `abbreviations and latin letter names hold the sentence`() {
        assertThat(ends("திரு. ராமன் வந்தார்")).isFalse()
        assertThat(ends("திருமதி. லட்சுமி")).isFalse()
        assertThat(ends("கி.மு. 300 இல்", at = "கி.மு".length)).isFalse()
        assertThat(ends("5 கி.மீ. தூரம்", at = "5 கி.மீ".length)).isFalse()
        assertThat(ends("ரூ. 500 கோடி")).isFalse()
        assertThat(ends("எ.கா. மரம்", at = "எ.கா".length)).isFalse()
        assertThat(ends("இரா. செழியன்")).isFalse()
        assertThat(ends("ஏ. ஆர். ரஹ்மான்", at = "ஏ. ஆர்".length)).isFalse()
        assertThat(ends("பி.ஜே.பி. கட்சி", at = "பி.ஜே.பி".length)).isFalse()
        assertThat(ends("எம்.ஜி.ஆர். நடித்தார்", at = "எம்.ஜி.ஆர்".length)).isFalse()
    }

    @Test
    fun `months hold the sentence before a number only`() {
        assertThat(ends("ஜன. 15 அன்று")).isFalse()
        assertThat(ends("மார்ச். 3 அன்று")).isFalse()
        assertThat(ends("அது ஜன. அவர்")).isTrue()
    }

    @Test
    fun `one-syllable initials and chains of them hold the sentence, but for the words of dialogue`() {
        assertThat(ends("க. சண்முகம் பேசினார்")).isFalse()
        assertThat(ends("மு. கருணாநிதி")).isFalse()
        assertThat(ends("தி.மு.க. அரசு", at = "தி.மு.க".length)).isFalse()
        assertThat(ends("இங்கே வா. நான்")).isTrue()
        assertThat(ends("வெளியே போ. இப்போது")).isTrue()
        assertThat(ends("யார்? நீ. அவன்", at = "யார்? நீ".length)).isTrue()
    }

    @Test
    fun `vowel signs and the pulli stay inside the aimed word`() {
        assertThat(support.wordStartOffset("இன்று வீ", "ட்டில்")).isEqualTo("வீ".length)
        assertThat(support.wordStartOffset("அவர் பெ", "ண்")).isEqualTo("பெ".length)
        assertThat(support.lookupStart("ிடம்", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("மரம்", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `recognized text loses the joiners, typed text keeps them`() {
        assertThat(support.ocrLines(listOf(listOf("அவன்\u200C", "வந்தான்\u200D"))))
            .containsExactly(listOf("அவன்", "வந்தான்"))
        assertThat(support.ocrWord("அவன்\u200C")).isEqualTo("அவன்\u200C")
    }

    @Test
    fun `the language's endings give way to entries, and the popup looks at 24 letters`() {
        assertThat(support.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(support.headwordMarks).isNull()
        assertThat(support.defaultScanLength).isEqualTo(24)
    }

    @Test
    fun `tamil is read by downloaded models, wiktionary's recordings come first`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.TAMIL)
        assertThat(support.languageTag).isEqualTo("ta")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH").inOrder()
        assertThat(support.flagCountries).containsExactly("in")
        assertThat(support.glyph).isEqualTo("த")
        assertThat(support.codeLabel).isEqualTo("TA")
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
