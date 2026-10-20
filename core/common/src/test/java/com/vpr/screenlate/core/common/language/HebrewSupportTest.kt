package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.text.Normalizer
import java.util.Locale
import org.junit.Test

class HebrewSupportTest {

    private val support = HebrewSupport

    private fun ends(text: String, at: Int) = support.endsSentence(text, at)

    @Test
    fun `a sentence ends at the full stop and the question mark`() {
        assertThat(ends("הלכתי הביתה. אחר כך", "הלכתי הביתה".length)).isTrue()
        assertThat(ends("מה שלומך? טוב", "מה שלומך".length)).isTrue()
        assertThat(ends("ספר, מחברת", "ספר".length)).isFalse()
        assertThat(ends("3.14", 1)).isFalse()
    }

    @Test
    fun `the geresh, the gershayim, their ascii quotes and the maqaf stay inside the aimed word`() {
        assertThat(support.wordStartOffset("צה״", "ל")).isEqualTo(3)
        assertThat(support.wordStartOffset("צה\"", "ל")).isEqualTo(3)
        assertThat(support.wordStartOffset("ג׳", "ירפה")).isEqualTo(2)
        assertThat(support.wordStartOffset("ג'", "ירפה")).isEqualTo(2)
        assertThat(support.wordStartOffset("בית־", "ספר")).isEqualTo(4)
        // The word after an abbreviation is a word of its own.
        assertThat(support.wordStartOffset("רח׳ ", "הרצל")).isEqualTo(0)
        assertThat(support.wordStartOffset("ד״ר ", "כהן")).isEqualTo(0)
    }

    @Test
    fun `vowel points stay inside the aimed word, quotes and spaces before it do not`() {
        assertThat(support.wordStartOffset("הַיֶּ", "לֶד")).isEqualTo(5)
        assertThat(support.wordStartOffset("שלום ", "עולם")).isEqualTo(0)
        assertThat(support.wordStartOffset("שלום,", "עולם")).isEqualTo(0)
        // An opening quote after a space joins nothing.
        assertThat(support.wordStartOffset("אמר \"", "שלום")).isEqualTo(0)
        assertThat(support.wordStartOffset("אמר ״", "שלום")).isEqualTo(0)
    }

    @Test
    fun `pointed forms are keyed under their letters, punctuation of the hebrew block is no point`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks).isEqualTo(HEBREW_POINTS)
        for (point in listOf('ְ', 'ַ', 'ּ', 'ׁ', 'ׂ', 'ֽ', 'ׇ', '֑', '֯')) assertThat(marks.marks).contains(point.toString())
        // Maqaf, paseq, sof pasuq and nun hafukha.
        for (punctuation in listOf('־', '׀', '׃', '׆')) assertThat(marks.marks).doesNotContain(punctuation.toString())
        assertThat(marks.bases).contains("א")
        assertThat(marks.bases).contains("ת")
        assertThat(marks.bases).contains("ך")
        assertThat(marks.bases).doesNotContain("״")
        // Words carry no points, forms do; the lemmas forms name lose theirs, and forms without a Hebrew letter go.
        assertThat(marks.everyHeadword).isFalse()
        assertThat(marks.lemmasAsWritten).isFalse()
        assertThat(marks.formsInScript).isTrue()
        assertThat(marks.replacements).isEmpty()
    }

    @Test
    fun `wordfreq's ascii quotes between hebrew letters become the gershayim and the geresh`() {
        assertThat(support.frequencyWord("צה\"ל")).isEqualTo("צה״ל")
        assertThat(support.frequencyWord("ג'ירפה")).isEqualTo("ג׳ירפה")
        assertThat(support.frequencyWord("שלום")).isEqualTo("שלום")
        assertThat(support.frequencyWord("\"שלום")).isEqualTo("\"שלום")
        assertThat(support.frequencyWord("rock'n'roll")).isEqualTo("rock'n'roll")
    }

    @Test
    fun `modern israeli transcriptions go first`() {
        assertThat(support.firstTranscription(listOf("Modern-Israeli", "sg"))).isTrue()
        assertThat(support.firstTranscription(listOf("Modern Israeli, Sephardi"))).isTrue()
        assertThat(support.firstTranscription(listOf("reconstructed Biblical"))).isFalse()
        assertThat(support.firstTranscription(listOf("Tiberian", "Ashkenazi"))).isFalse()
        assertThat(support.firstTranscription(emptyList())).isFalse()
        assertThat(EnglishSupport.firstTranscription(listOf("Modern-Israeli"))).isFalse()
    }

    @Test
    fun `recordings are looked for with the word, pointed titles found too, voices read the word`() {
        assertThat(support.recordings("כלב", "")).containsExactly(RecordingTitle("he", "כלב"))
        assertThat(support.recordingMarks).isEqualTo(HEBREW_POINTS)
        assertThat(support.linguaLibreItems).containsExactly("Q9288")
        assertThat(support.speechText("כלב", "")).isEqualTo("כלב")
        assertThat(support.ocrWord("צה\"ל")).isEqualTo("צה\"ל")
    }

    @Test
    fun `hebrew runs right to left, has its own fonts and takes 24 characters from the aim`() {
        assertThat(support.rightToLeft).isTrue()
        assertThat(support.ocrScript).isEqualTo(OcrScript.HEBREW)
        assertThat(support.fontSubsets).containsExactly("hebrew")
        assertThat(support.systemFonts.sans).isEmpty()
        assertThat(support.systemFonts.serif).containsExactly("NotoSerifHebrew-Regular", "Noto Serif Hebrew Regular")
        assertThat(support.systemFonts.unicodeRange).contains("U+0590-05FF")
        assertThat(support.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
        assertThat(support.wikidataId).isEqualTo("Q9288")
        assertThat(support.flagCountries).containsExactly("il")
        assertThat(support.glyph).isEqualTo("א")
        assertThat(support.defaultScanLength).isEqualTo(24)
        assertThat(support.speechTag).isEqualTo("he")
        assertThat(support.quotePairs['„']).isEqualTo('”')
    }

    @Test
    fun `the word, the reading that repeats it and the sentence are right-to-left anki text`() {
        assertThat(support.rightToLeftMarkers)
            .containsExactly("expression", "reading", "sentence", "cloze-prefix", "cloze-body", "cloze-suffix")
    }

    @Test
    fun `the language is called Hebrew as the phone names it`() {
        assertThat(support.nameIn(Locale.ENGLISH)).isEqualTo("Hebrew")
        val russian = Locale.forLanguageTag("ru")
        assertThat(support.nameIn(russian)).isEqualTo(Locale.forLanguageTag("he").getDisplayLanguage(russian))
    }

    @Test
    fun `the language's own texts are composed`() {
        for (text in listOf(support.fontSample, support.translationSample, support.audioTestWord, support.glyph)) {
            assertThat(Normalizer.normalize(text, Normalizer.Form.NFC)).isEqualTo(text)
        }
    }
}
