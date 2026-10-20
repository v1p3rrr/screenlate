package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import java.util.Locale
import org.junit.Test

class PersianSupportTest {

    private val support = PersianSupport

    private fun ends(text: String, at: Int) = support.endsSentence(text, at)

    @Test
    fun `the arabic question mark ends a sentence, the arabic comma and semicolon do not`() {
        assertThat(ends("حالت خوب است؟ بله", "حالت خوب است".length)).isTrue()
        assertThat(ends("به خانه رفت. بعد", "به خانه رفت".length)).isTrue()
        assertThat(ends("کتاب، دفتر", "کتاب".length)).isFalse()
        assertThat(ends("آمد؛ ولی", "آمد".length)).isFalse()
    }

    @Test
    fun `a page, a volume and a reference before a number keep the sentence going`() {
        assertThat(ends("نگاه کنید ص. ۱۲", "نگاه کنید ص".length)).isFalse()
        assertThat(ends("ج. ۲", "ج".length)).isFalse()
        val see = "ر.ک. فصل دوم"
        assertThat(ends(see, see.indexOf(". "))).isFalse()
        assertThat(ends("سال ۵۰۰ ق.م. بود", "سال ۵۰۰ ق.م".length)).isFalse()
        assertThat(ends("رفتم. بعد", "رفتم".length)).isTrue()
    }

    @Test
    fun `the zero-width non-joiner and vowel marks stay inside the aimed word`() {
        assertThat(support.wordStartOffset("کتاب\u200C", "ها")).isEqualTo(5)
        assertThat(support.wordStartOffset("می\u200Cخو", "اهم")).isEqualTo(5)
        assertThat(support.wordStartOffset("کِتا", "ب")).isEqualTo(4)
        assertThat(support.wordStartOffset("به ", "خانه")).isEqualTo(0)
        assertThat(support.wordStartOffset("کتاب،", "دفتر")).isEqualTo(0)
        // Persian digits are part of a number.
        assertThat(support.wordStartOffset("۱۴", "۰۳")).isEqualTo(2)
    }

    @Test
    fun `a verb prefix or an ending written apart starts the lookup at the word before`() {
        assertThat(support.earlierStartOffset("من می ", "خواهم بروم")).isEqualTo("می ".length)
        assertThat(support.earlierStartOffset("نمی ", "دانم")).isEqualTo("نمی ".length)
        // Read with the Arabic yeh.
        assertThat(support.earlierStartOffset("مي ", "روم")).isEqualTo("مي ".length)
        assertThat(support.earlierStartOffset("این کتاب ", "ها را")).isEqualTo("کتاب ".length)
        assertThat(support.earlierStartOffset("کتاب ", "های من")).isEqualTo("کتاب ".length)
        assertThat(support.earlierStartOffset("بزرگ ", "ترین")).isEqualTo("بزرگ ".length)
        assertThat(support.earlierStartOffset("بزرگ ", "تر.")).isEqualTo("بزرگ ".length)
    }

    @Test
    fun `other words before the aimed one, and words that only start like an ending, stay apart`() {
        assertThat(support.earlierStartOffset("من ", "خواهم")).isEqualTo(0)
        assertThat(support.earlierStartOffset("کتاب ", "هوا")).isEqualTo(0)
        assertThat(support.earlierStartOffset("کتاب ", "ترس")).isEqualTo(0)
        // A word that ends in می is not the prefix; nor is می at a sentence end before the aimed word.
        assertThat(support.earlierStartOffset("سمی ", "خواهم")).isEqualTo(0)
        assertThat(support.earlierStartOffset("می", "خواهم")).isEqualTo(0)
        assertThat(support.earlierStartOffset("می. ", "خواهم")).isEqualTo(0)
    }

    @Test
    fun `headwords are keyed with persian letters and without the zero-width non-joiner`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks).isEqualTo(ArabicSupport.headwordMarks!!.marks)
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.replacements).containsExactly("ي", "ی", "ك", "ک", "ى", "ی", "ۀ", "ه", "\u200C", "")
        assertThat(marks.furtherReplacements).isEmpty()
        // Lemmas are named as headwords are written.
        assertThat(marks.lemmasAsWritten).isTrue()
        assertThat(marks.formsInScript).isTrue()
    }

    @Test
    fun `arabic yeh, kaf and alif maqsura read in a persian word are written as persian ones`() {
        assertThat(support.ocrWord("كتاب")).isEqualTo("کتاب")
        assertThat(support.ocrWord("يك")).isEqualTo("یک")
        assertThat(support.ocrWord("مى")).isEqualTo("می")
        assertThat(support.ocrWord("پنجره")).isEqualTo("پنجره")
        assertThat(support.ocrWord("يكى").length).isEqualTo(3)
    }

    @Test
    fun `wordfreq words get persian letters, and a spelling without the zero-width non-joiner`() {
        assertThat(support.frequencyWord("اين")).isEqualTo("این")
        assertThat(support.frequencyWord("كه")).isEqualTo("که")
        assertThat(support.frequencyWord("خانه")).isEqualTo("خانه")
        assertThat(support.frequencySpellings("آن\u200Cها")).containsExactly("آنها")
        assertThat(support.frequencySpellings("آنها")).isEmpty()
    }

    @Test
    fun `recordings are looked for with persian and with arabic letters, voices read the word`() {
        assertThat(support.recordings("کتاب", "kitāb"))
            .containsExactly(RecordingTitle("fa", "کتاب"), RecordingTitle("fa", "كتاب")).inOrder()
        assertThat(support.recordings("آب", "āb")).containsExactly(RecordingTitle("fa", "آب"))
        assertThat(support.speechText("کتاب", "kitāb")).isEqualTo("کتاب")
        assertThat(support.recordingMarks).isEqualTo(support.headwordMarks!!.marks)
        assertThat(support.linguaLibreItems).containsExactly("Q9168")
    }

    @Test
    fun `persian runs right to left, shares the arabic fonts and takes 24 characters from the aim`() {
        assertThat(support.rightToLeft).isTrue()
        assertThat(support.ocrScript).isEqualTo(OcrScript.ARABIC)
        assertThat(support.fontSubsets).containsExactly("arabic")
        assertThat(support.systemFonts).isEqualTo(ArabicSupport.systemFonts)
        assertThat(support.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
        assertThat(support.wikidataId).isEqualTo("Q9168")
        assertThat(support.flagCountries).containsExactly("ir")
        assertThat(support.glyph).isEqualTo("پ")
        assertThat(support.defaultScanLength).isEqualTo(24)
        assertThat(support.speechTag).isEqualTo("fa")
        assertThat(support.quotePairs['«']).isEqualTo('»')
    }

    @Test
    fun `the word and the sentence are right-to-left anki text, the latin reading is not`() {
        assertThat(support.rightToLeftMarkers)
            .containsExactly("expression", "sentence", "cloze-prefix", "cloze-body", "cloze-suffix")
    }

    @Test
    fun `the language is called Persian as the phone names it`() {
        assertThat(support.nameIn(Locale.ENGLISH)).isEqualTo("Persian")
        val russian = Locale.forLanguageTag("ru")
        assertThat(support.nameIn(russian)).isEqualTo(Locale.forLanguageTag("fa").getDisplayLanguage(russian))
    }

    @Test
    fun `the language's own texts are composed`() {
        for (text in listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph)) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
        }
    }
}
