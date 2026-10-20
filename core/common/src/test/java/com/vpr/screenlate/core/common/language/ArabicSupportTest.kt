package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import java.util.Locale
import org.junit.Test

class ArabicSupportTest {

    private val support = ArabicSupport

    private fun ends(text: String, at: Int) = support.endsSentence(text, at)

    @Test
    fun `the arabic question mark ends a sentence, the arabic comma and semicolon do not`() {
        assertThat(ends("هل أنت بخير؟ نعم", "هل أنت بخير".length)).isTrue()
        assertThat(ends("ذهب إلى البيت. ثم", "ذهب إلى البيت".length)).isTrue()
        assertThat(ends("يا له من يوم! نعم", "يا له من يوم".length)).isTrue()
        assertThat(ends("كتب، ثم قرأ", "كتب".length)).isFalse()
        assertThat(ends("كتب؛ ثم قرأ", "كتب".length)).isFalse()
    }

    @Test
    fun `titles and a page before a name or a number keep the sentence going`() {
        assertThat(ends("قال د. أحمد", "قال د".length)).isFalse()
        assertThat(ends("انظر ص. ١٢", "انظر ص".length)).isFalse()
        val era = "سنة ٣٠٠ ق.م. بنيت"
        assertThat(ends(era, era.indexOf(". "))).isFalse()
        // A word that ends in one of the letters is no abbreviation.
        assertThat(ends("بدأ. ثم", "بدأ".length)).isTrue()
    }

    @Test
    fun `vowel marks, shadda and tatweel stay inside the aimed word`() {
        assertThat(support.wordStartOffset("الكِتَ", "ابُ")).isEqualTo(6)
        assertThat(support.wordStartOffset("مُدَرِّ", "سَة")).isEqualTo(7)
        assertThat(support.wordStartOffset("عـ", "ربي")).isEqualTo(2)
        assertThat(support.wordStartOffset("ذهب إلى ", "البيت")).isEqualTo(0)
        // The Arabic comma is not part of a word; Arabic-Indic digits are.
        assertThat(support.wordStartOffset("كتب،", "ثم")).isEqualTo(0)
        assertThat(support.wordStartOffset("١٢", "٣")).isEqualTo(2)
        assertThat(support.lookupStart("كتاب", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(support.lookupStart("\u064Eب", latinAsNative = false)).isNull()
    }

    @Test
    fun `headwords are keyed without vowel marks, with final ya and ha and then with bare alifs`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks.toSet()).containsAtLeast('\u064B', '\u064E', '\u0650', '\u0651', '\u0652', '\u0670')
        // The hamza above and below are parts of أ إ ؤ ئ, which the replacements write as a bare alif or keep.
        assertThat(marks.marks.toSet()).containsNoneOf('\u0654', '\u0655')
        assertThat(marks.bases.toSet()).containsAtLeast('ا', 'ب', 'ي', 'ى', 'ة', 'ک', 'ی', 'پ')
        // Tatweel is a letter, so marks drawn on it are dropped too.
        assertThat(marks.bases.toSet()).contains('ـ')
        assertThat(marks.bases.toSet()).doesNotContain('\u064E')
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.replacements).containsExactly("ى", "ي", "ة", "ه")
        assertThat(marks.furtherReplacements).containsExactly("أ", "ا", "إ", "ا", "آ", "ا", "ٱ", "ا")
        assertThat(marks.lemmasAsWritten).isFalse()
    }

    @Test
    fun `persian kaf and yeh read in an arabic word are written as arabic ones`() {
        assertThat(support.ocrWord("کتاب")).isEqualTo("كتاب")
        assertThat(support.ocrWord("فی")).isEqualTo("في")
        assertThat(support.ocrWord("على")).isEqualTo("على")
        assertThat(support.ocrWord("كتاب")).isEqualTo("كتاب")
        assertThat(support.ocrWord("کی").length).isEqualTo(2)
    }

    @Test
    fun `guillemets, curly quotes and brackets pair`() {
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs['“']).isEqualTo('”')
        assertThat(support.quotePairs['(']).isEqualTo(')')
    }

    @Test
    fun `arabic runs right to left, offers arabic fonts and takes 24 characters from the aim`() {
        assertThat(support.rightToLeft).isTrue()
        assertThat(support.ocrScript).isEqualTo(OcrScript.ARABIC)
        assertThat(support.fontSubsets).containsExactly("arabic")
        assertThat(support.systemFonts.sans).isEmpty()
        assertThat(support.systemFonts.serif.take(2)).containsExactly("NotoNaskhArabic", "Noto Naskh Arabic").inOrder()
        assertThat(support.systemFonts.unicodeRange).startsWith("U+0600-06FF")
        assertThat(support.defaultAudioSources).containsExactly("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH").inOrder()
        assertThat(support.wikidataId).isEqualTo("Q13955")
        assertThat(support.flagCountries).containsExactly("sa")
        assertThat(support.glyph).isEqualTo("ض")
        assertThat(support.defaultScanLength).isEqualTo(24)
        assertThat(support.speechTag).isEqualTo("ar")
    }

    @Test
    fun `the word, the sentence and the vowelled reading are right-to-left anki text, recordings may add harakat`() {
        assertThat(support.rightToLeftMarkers)
            .containsExactly("expression", "reading", "sentence", "cloze-prefix", "cloze-body", "cloze-suffix")
        assertThat(support.rightToLeftMarkers).containsNoneOf("glossary", "phonetic-transcriptions", "sentence-translation")
        assertThat(support.recordingMarks).isEqualTo(support.headwordMarks!!.marks)
        // Lingua Libre's dialect items stay out.
        assertThat(support.linguaLibreItems).containsExactly("Q13955")
    }

    @Test
    fun `the language is called Arabic as the phone names it`() {
        assertThat(support.nameIn(Locale.ENGLISH)).isEqualTo("Arabic")
        val russian = Locale.forLanguageTag("ru")
        assertThat(support.nameIn(russian)).isEqualTo(Locale.forLanguageTag("ar").getDisplayLanguage(russian))
    }

    @Test
    fun `the language's own texts are composed`() {
        for (text in listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph)) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
        }
    }
}
