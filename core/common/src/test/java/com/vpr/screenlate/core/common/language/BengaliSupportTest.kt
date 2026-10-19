package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import java.util.Locale
import org.junit.Test

class BengaliSupportTest {

    private val support = BengaliSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `the danda and the double danda end a sentence, as a period does`() {
        assertThat(ends("সে এল। তারপর", at = "সে এল".length)).isTrue()
        assertThat(ends("হরি হরি॥ এখন", at = "হরি হরি".length)).isTrue()
        assertThat(ends("সে এল. তারপর")).isTrue()
        assertThat(ends("কেন? জানি না", at = "কেন".length)).isTrue()
    }

    @Test
    fun `abbreviations and initials hold the sentence, letter names that are words end it`() {
        assertThat(ends("ড. ইউনূস এলেন")).isFalse()
        assertThat(ends("মো. রহিম এলেন")).isFalse()
        assertThat(ends("৫ কি.মি. দূরে", at = "৫ কি.মি".length)).isFalse()
        assertThat(ends("এ.কে. ফজলুল হক", at = "এ.কে".length)).isFalse()
        assertThat(ends("বি.এন.পি. নেতা", at = "বি.এন.পি".length)).isFalse()
        assertThat(ends("এ. কে. ফজলুল হক")).isFalse()
        assertThat(ends("এ. কে. ফজলুল হক", at = "এ. কে".length)).isFalse()
        assertThat(ends("তুমি এস. আমি যাব")).isTrue()
        assertThat(ends("সে কি ও. তারপর", at = "সে কি ও".length)).isTrue()
    }

    @Test
    fun `vowel signs, nukta, virama and joiners stay inside the aimed word`() {
        assertThat(support.wordStartOffset("প\u09A1\u09BC", "া")).isEqualTo(3)
        assertThat(support.wordStartOffset("আজ ব", "ইটা")).isEqualTo(1)
        assertThat(support.wordStartOffset("আজ ক্ষ", "ত্রিয়")).isEqualTo(3)
        assertThat(support.wordStartOffset("র্\u200D", "য")).isEqualTo(3)
        assertThat(support.wordStartOffset("ক্\u200C", "ষ")).isEqualTo(3)
        assertThat(support.lookupStart("োকা", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("পানি", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `curly quotes and brackets pair`() {
        assertThat(support.quotePairs['‘']).isEqualTo('’')
        assertThat(support.quotePairs['“']).isEqualTo('”')
    }

    @Test
    fun `bengali is read on the device, the page keeps the phone's sans-serif`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.BENGALI)
        assertThat(support.systemFonts.sans).isEmpty()
        assertThat(support.systemFonts.serif.first()).isEqualTo("NotoSerifBengali-Regular")
        assertThat(support.systemFonts.unicodeRange).contains("U+0980-09FE")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("bd")
        assertThat(support.glyph).isEqualTo("বা")
        // ড়, ঢ় and য় are letters of their own: text does not leave out their nukta.
        assertThat(support.headwordMarks).isNull()
    }

    @Test
    fun `the language is called Bengali in English and Vietnamese, the other locales name it as the phone does`() {
        assertThat(support.nameIn(Locale.ENGLISH)).isEqualTo("Bengali")
        assertThat(support.nameIn(Locale.forLanguageTag("vi"))).isEqualTo("tiếng Bengal")
        val russian = Locale.forLanguageTag("ru")
        assertThat(support.nameIn(russian)).isEqualTo(Locale.forLanguageTag("bn").getDisplayLanguage(russian))
    }

    @Test
    fun `the language's own texts are composed as dictionaries write them`() {
        val texts = listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph) +
            support.abbreviations + support.initials
        for (text in texts) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
            // NFC writes য় (U+09DF), ড় and ঢ় as the letter and nukta.
            assertThat(text.none { it in "\u09DC\u09DD\u09DF" }).isTrue()
        }
    }
}
