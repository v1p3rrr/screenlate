package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import org.junit.Test

class HindiSupportTest {

    private val support = HindiSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the danda and the double danda end a sentence, as a period does`() {
        assertThat(ends("वह आया। फिर", at = "वह आया".length)).isTrue()
        assertThat(ends("राम राम॥ अब", at = "राम राम".length)).isTrue()
        assertThat(ends("वह आया. फिर")).isTrue()
        assertThat(ends("क्या? हाँ", at = "क्या".length)).isTrue()
    }

    @Test
    fun `abbreviations and initials hold the sentence, letter names that are words end it`() {
        assertThat(ends("डॉ. शर्मा आए")).isFalse()
        assertThat(ends("सन 300 ई.पू. में", at = "सन 300 ई.पू".length)).isFalse()
        assertThat(ends("5 कि.मी. दूर", at = "5 कि.मी".length)).isFalse()
        assertThat(ends("ए.पी.जे. अब्दुल कलाम", at = "ए.पी.जे".length)).isFalse()
        assertThat(ends("आई.आई.टी. में", at = "आई.आई.टी".length)).isFalse()
        assertThat(ends("बी. आर. अम्बेडकर")).isFalse()
        assertThat(ends("बी. आर. अम्बेडकर", at = "बी. आर".length)).isFalse()
        assertThat(ends("वह आई. फिर")).isTrue()
        assertThat(ends("उसने चाय पी. फिर")).isTrue()
    }

    @Test
    fun `vowel signs, nukta, virama and joiners stay inside the aimed word`() {
        assertThat(support.wordStartOffset("प\u0922\u093C", "ा")).isEqualTo(3)
        assertThat(support.wordStartOffset("आज कि", "ताब")).isEqualTo(2)
        assertThat(support.wordStartOffset("आज क्ष", "त्रिय")).isEqualTo(3)
        assertThat(support.wordStartOffset("र्\u200D", "य")).isEqualTo(3)
        assertThat(support.wordStartOffset("क्\u200C", "ष")).isEqualTo(3)
        assertThat(support.lookupStart("ाना", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("पानी", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `curly quotes and brackets pair`() {
        assertThat(support.quotePairs['‘']).isEqualTo('’')
        assertThat(support.quotePairs['“']).isEqualTo('”')
    }

    @Test
    fun `wordfreq's anusvara also ranks the candrabindu spelling, and the other way round`() {
        assertThat(support.frequencySpellings("हूं")).containsExactly("हूँ")
        assertThat(support.frequencySpellings("माँ")).containsExactly("मां")
        assertThat(support.frequencySpellings("पानी")).isEmpty()
    }

    @Test
    fun `every headword is also found without nukta and with anusvara for candrabindu`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks).isEqualTo("\u093C")
        assertThat(marks.bases).startsWith("क")
        assertThat(marks.bases).endsWith("ह")
        assertThat(marks.bases).hasLength(0x0939 - 0x0915 + 1)
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.replacements).containsExactly("\u0901", "\u0902")
    }

    @Test
    fun `devanagari is read on the device, the page keeps the phone's sans-serif`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.DEVANAGARI)
        assertThat(support.systemFonts.sans).isEmpty()
        assertThat(support.systemFonts.serif.first()).isEqualTo("NotoSerifDevanagari-Regular")
        assertThat(support.systemFonts.unicodeRange).contains("U+0900-097F")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("in")
        assertThat(support.glyph).isEqualTo("हि")
    }

    @Test
    fun `the language's own texts are composed as dictionaries write them`() {
        val texts = listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph) +
            support.abbreviations + support.initials
        for (text in texts) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
            assertThat(text.none { it in '\u0958'..'\u095F' }).isTrue()
        }
    }
}
