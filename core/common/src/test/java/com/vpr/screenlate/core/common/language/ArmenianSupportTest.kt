package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import java.util.Locale
import org.junit.Test

class ArmenianSupportTest {

    private val support = ArmenianSupport

    private fun ends(text: String, at: Int) = support.endsSentence(text, at)

    @Test
    fun `the full stop and the colon end a sentence, the period does not`() {
        assertThat(ends("Նա եկավ։ Հետո", "Նա եկավ".length)).isTrue()
        assertThat(ends("Նա եկավ: Հետո", "Նա եկավ".length)).isTrue()
        assertThat(ends("Ի՞նչ ես անում! Արի", "Ի՞նչ ես անում".length)).isTrue()
        // The period is the Armenian colon (mijaket).
        assertThat(ends("Նա ասաց. «Արի»", "Նա ասաց".length)).isFalse()
        // A colon between digits is a time.
        assertThat(ends("ժամը 10:30-ին", "ժամը 10".length)).isFalse()
        assertThat(ends("Նա եկավ։", "Նա եկավ".length)).isTrue()
    }

    @Test
    fun `the marks on a stressed vowel and the armenian hyphen stay inside the aimed word`() {
        assertThat(support.wordStartOffset("Ինչո՞", "ւ ես")).isEqualTo(5)
        assertThat(support.wordStartOffset("կարդա", "՛")).isEqualTo(5)
        assertThat(support.wordStartOffset("հայ֊", "ռուսական")).isEqualTo(4)
        // The comma ՝ and the full stop ։ are not part of a word.
        assertThat(support.wordStartOffset("այո՝", "ոչ")).isEqualTo(0)
        assertThat(support.lookupStart("ջուր", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `wiktionary forms are keyed without the marks after any armenian letter`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks.toSet()).containsExactly('\u055B', '\u055C', '\u055E')
        assertThat(marks.bases.toSet()).containsAtLeastElementsIn(('ա'..'ֆ').toList())
        assertThat(marks.bases).contains("և")
        assertThat(marks.everyHeadword).isFalse()
    }

    @Test
    fun `guillemets and brackets pair`() {
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs).doesNotContainKey('“')
    }

    @Test
    fun `armenian is read by tesseract, the page keeps the phone's sans-serif, lookups take 24 characters`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.ARMENIAN)
        assertThat(support.systemFonts.sans).isEmpty()
        assertThat(support.systemFonts.serif.first()).isEqualTo("NotoSerifArmenian-Regular")
        assertThat(support.systemFonts.unicodeRange).contains("U+0530-058F")
        assertThat(support.fontSubsets).isEmpty()
        assertThat(support.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
        assertThat(support.flagCountries).containsExactly("am")
        assertThat(support.glyph).isEqualTo("Հ")
        assertThat(support.defaultScanLength).isEqualTo(24)
    }

    @Test
    fun `the language is called Armenian as the phone names it`() {
        assertThat(support.nameIn(Locale.ENGLISH)).isEqualTo("Armenian")
        val russian = Locale.forLanguageTag("ru")
        assertThat(support.nameIn(russian)).isEqualTo(Locale.forLanguageTag("hy").getDisplayLanguage(russian))
    }

    @Test
    fun `the language's own texts are composed`() {
        for (text in listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph)) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
        }
    }
}
