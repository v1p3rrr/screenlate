package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import org.junit.Test

class MarathiSupportTest {

    private val support = MarathiSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the period, the danda and the double danda end a sentence`() {
        assertThat(ends("तो घरी आला. मग")).isTrue()
        assertThat(ends("तो घरी आला। मग", at = "तो घरी आला".length)).isTrue()
        assertThat(ends("राम राम॥ आता", at = "राम राम".length)).isTrue()
        assertThat(ends("काय? हो", at = "काय".length)).isTrue()
    }

    @Test
    fun `abbreviations and latin letter names hold the sentence, आय ends it`() {
        assertThat(ends("डॉ. जोशी आले")).isFalse()
        assertThat(ends("प्रा. देशमुख म्हणाले")).isFalse()
        assertThat(ends("सौ. पाटील आल्या")).isFalse()
        assertThat(ends("उदा. घर, पाणी")).isFalse()
        assertThat(ends("इ.स. 1818 मध्ये", at = "इ.स".length)).isFalse()
        assertThat(ends("इ.स.पू. 300 मध्ये", at = "इ.स.पू".length)).isFalse()
        assertThat(ends("5 कि.मी. अंतर", at = "5 कि.मी".length)).isFalse()
        assertThat(ends("ए.पी.जे. अब्दुल कलाम", at = "ए.पी.जे".length)).isFalse()
        assertThat(ends("आय.आय.टी. मुंबई", at = "आय.आय.टी".length)).isFalse()
        assertThat(ends("व्ही. शांताराम")).isFalse()
        assertThat(ends("त्याची आय. मग")).isTrue()
    }

    @Test
    fun `one-syllable initials and chains of them hold the sentence`() {
        assertThat(ends("पु. ल. देशपांडे")).isFalse()
        assertThat(ends("पु. ल. देशपांडे", at = "पु. ल".length)).isFalse()
        assertThat(ends("ग. दि. माडगूळकर")).isFalse()
        assertThat(ends("ह.भ.प. महाराज", at = "ह.भ.प".length)).isFalse()
        assertThat(ends("प.पू. स्वामी", at = "प.पू".length)).isFalse()
        assertThat(ends("श्री. ज्ञा. कुलकर्णी", at = "श्री. ज्ञा".length)).isFalse()
        assertThat(ends("र्\u200Dय. मग", at = 4)).isFalse()
    }

    @Test
    fun `words of two syllables and one-syllable words of dialogue end the sentence`() {
        assertThat(ends("हे माझे घर आहे. तिथे")).isTrue()
        assertThat(ends("मी जातो. तो")).isTrue()
        assertThat(ends("हो. मी")).isTrue()
        assertThat(ends("कोण आले? मी. तू")).isTrue()
        assertThat(ends("चहा घ्या. आणि")).isTrue()
        assertThat(ends("निघून जा. आता")).isTrue()
        assertThat(ends("वर्ष 2020. मग")).isTrue()
    }

    @Test
    fun `vowel signs, nukta, virama and joiners stay inside the aimed word`() {
        assertThat(support.wordStartOffset("आज कि", "ताब")).isEqualTo(2)
        assertThat(support.wordStartOffset("तऱ्", "हा")).isEqualTo(3)
        assertThat(support.wordStartOffset("र्\u200D", "य")).isEqualTo(3)
        assertThat(support.lookupStart("ाचा", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("पाणी", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `the import leaves out the labels of verb tables and keys no headword again`() {
        val marks = support.headwordMarks!!
        assertThat(marks.droppedForms).containsAtLeast("होकारार्थी", "नकारार्थी", "Affirmative", "Negative")
        assertThat(marks.marks).isEmpty()
        assertThat(marks.everyHeadword).isFalse()
        assertThat(marks.replacements).isEmpty()
        assertThat(support.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(support.frequencySpellings("हूं")).isEmpty()
    }

    @Test
    fun `devanagari is read on the device, the page is tagged marathi`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.DEVANAGARI)
        assertThat(support.languageTag).isEqualTo("mr")
        assertThat(support.systemFonts).isEqualTo(HindiSupport.systemFonts)
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("in")
        assertThat(support.glyph).isEqualTo("म")
    }

    @Test
    fun `the language's own texts are composed as dictionaries write them`() {
        val texts = listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph) +
            support.abbreviations + support.initials + support.syllableSentenceEnds
        for (text in texts) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
            assertThat(text.none { it in '\u0958'..'\u095F' }).isTrue()
        }
    }
}
