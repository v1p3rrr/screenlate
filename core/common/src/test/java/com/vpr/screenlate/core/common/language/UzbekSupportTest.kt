package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class UzbekSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = UzbekSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the ones that close a list or follow a number only before a lowercase word`() {
        assertThat(ends("prof. Karimov")).isFalse()
        assertThat(ends("dots. Aliyev")).isFalse()
        assertThat(ends("Toshkent sh. Navoiy koʻch. 5", "Toshkent sh".length)).isFalse()
        assertThat(ends("Toshkent sh. Navoiy koʻch. 5", "Toshkent sh. Navoiy koʻch".length)).isFalse()
        assertThat(ends("Navoiy ko'ch. 5", "Navoiy ko'ch".length)).isFalse()
        assertThat(ends("Navoiy ko`ch. 5", "Navoiy ko`ch".length)).isFalse()
        assertThat(ends("Navoiy ko’ch. 5", "Navoiy ko’ch".length)).isFalse()
        // Other words with an apostrophe end a sentence.
        assertThat(ends("Bu soʻz. Keyin", "Bu soʻz".length)).isTrue()
        assertThat(ends("Bu so'z. Keyin", "Bu so'z".length)).isTrue()
        assertThat(ends("V asr mil. av. yashagan", "V asr mil".length)).isFalse()
        assertThat(ends("V asr mil. av. yashagan", "V asr mil. av".length)).isFalse()
        assertThat(ends("kitoblar, daftarlar va h.k. olindi", "kitoblar, daftarlar va h.k".length)).isFalse()
        assertThat(ends("kitoblar, daftarlar va h.k. Keyin", "kitoblar, daftarlar va h.k".length)).isTrue()
        assertThat(ends("500 mln. soʻm", "500 mln".length)).isFalse()
        assertThat(ends("Narxi 500 mln. Keyin", "Narxi 500 mln".length)).isTrue()
        assertThat(ends("2020 y. yanvar", "2020 y".length)).isFalse()
        assertThat(ends("Uy katta. Bogʻ kichik")).isTrue()
    }

    @Test
    fun `the turned comma, the apostrophe and the characters typed for them stay inside a word`() {
        assertThat(UzbekSupport.wordStartOffset("so'", "z")).isEqualTo(3)
        assertThat(UzbekSupport.wordStartOffset("so‘", "z")).isEqualTo(3)
        assertThat(UzbekSupport.wordStartOffset("so`", "z")).isEqualTo(3)
        assertThat(UzbekSupport.wordStartOffset("ta´", "til")).isEqualTo(3)
        assertThat(UzbekSupport.wordStartOffset("soʻ", "z")).isEqualTo(3)
        assertThat(UzbekSupport.wordStartOffset("taʼ", "lim")).isEqualTo(3)
        // Elsewhere ‘ opens a quote.
        assertThat(EnglishSupport.wordStartOffset("so‘", "z")).isEqualTo(0)
    }

    @Test
    fun `headwords are keyed with their apostrophes as the text is written and without the turned comma of o and g`() {
        val marks = UzbekSupport.headwordMarks!!
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.bases).isEqualTo("og")
        assertThat(marks.marks.toSet()).containsAtLeast('ʻ', '\'', '‘', '’', '`', '´', 'ʼ')
        // o' g' and the like are written oʻ gʻ, the other apostrophes ʼ.
        assertThat(marks.replacements).containsEntry("o'", "oʻ")
        assertThat(marks.replacements).containsEntry("G‘", "Gʻ")
        assertThat(marks.replacements).containsEntry("oʼ", "oʻ")
        assertThat(marks.replacements).containsEntry("'", "ʼ")
        assertThat(marks.replacements).containsEntry("`", "ʼ")
        assertThat(marks.replacements).doesNotContainKey("ʼ")
        assertThat(marks.replacements).hasSize(29)
    }

    @Test
    fun `an ending after a hyphen is looked up with the hyphen after a number or an abbreviation in capitals`() {
        assertThat(UzbekSupport.partStarts("2020-yil", 0, "2020-y".length)).containsExactly(4, 5).inOrder()
        assertThat(UzbekSupport.partStarts("BMT-da", 0, "BMT-d".length)).containsExactly(3, 4).inOrder()
        // A hyphen after a word joins a pair.
        assertThat(UzbekSupport.partStarts("ijtimoiy-iqtisodiy", 0, "ijtimoiy-i".length)).containsExactly(9)
    }

    @Test
    fun `english quotes and guillemets`() {
        assertThat(UzbekSupport.quotePairs).containsEntry('“', '”')
        assertThat(UzbekSupport.quotePairs).containsEntry('«', '»')
    }

    @Test
    fun `rules only for words without an entry, no compounds, plain case pairs`() {
        assertThat(UzbekSupport.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(UzbekSupport.compoundLinks).isNull()
        assertThat(UzbekSupport.fallbackLanguage).isNull()
        assertThat(Language.UZBEK.lookupDictionaryCodes).containsExactly("uz")
        assertThat(UzbekSupport.defaultScanLength).isEqualTo(16)
        // I is the capital of i, not of ı: MIL. is mil.
        assertThat(ends("V ASR MIL. AV. yashagan", "V ASR MIL".length)).isFalse()
    }

    @Test
    fun `uzbek's own code, flag and letters`() {
        assertThat(Language.of("uz")).isEqualTo(Language.UZBEK)
        assertThat(UzbekSupport.languageTag).isEqualTo("uz")
        assertThat(UzbekSupport.localeLanguages).containsExactly("uz")
        assertThat(UzbekSupport.codeLabel).isEqualTo("UZ")
        assertThat(UzbekSupport.ocrScript).isEqualTo(OcrScript.LATIN)
        assertThat(UzbekSupport.flagCountries).containsExactly("uz")
        assertThat(UzbekSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(UzbekSupport.fontSample.toSet()).containsAtLeast('ʻ', 'ʼ', 'q', 'x')
        assertThat(UzbekSupport.systemFonts.unicodeRange).contains("U+02BB-02BC")
        assertThat(UzbekSupport.defaultAudioSources)
            .containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
        assertThat(UzbekSupport.glyph).isEqualTo("O")
    }
}
