package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KazakhSupportTest {

    private val support = KazakhSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `abbreviations hold the sentence before a lowercase word or a number only`() {
        assertThat(ends("2007 ж. қазанда")).isFalse()
        assertThat(ends("XX ғ. басында")).isFalse()
        assertThat(ends("б.з.д. 500 жылы", "б.з.д".length)).isFalse()
        assertThat(ends("дәптер т.б. заттар", "дәптер т.б".length)).isFalse()
        assertThat(ends("Мыс. алма")).isFalse()
        // Before a capital they end it: a year closes many sentences.
        assertThat(ends("Туған жылы – 1991 ж. Кейін")).isTrue()
        assertThat(ends("Бұл кітап. Ол")).isTrue()
    }

    @Test
    fun `the closing low quote opens nothing`() {
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs['„']).isEqualTo('“')
        assertThat(support.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `latin i, h and the schwa read by OCR become the kazakh letters, latin words stay`() {
        assertThat(support.ocrWord("кiтап")).isEqualTo("кітап")
        assertThat(support.ocrWord("Iле")).isEqualTo("Іле")
        assertThat(support.ocrWord("шаhар")).isEqualTo("шаһар")
        assertThat(support.ocrWord("\u0259лем")).isEqualTo("әлем")
        assertThat(support.ocrWord("\u018Fке")).isEqualTo("Әке")
        assertThat(support.ocrWord("м\u0065кт\u0065п")).isEqualTo("мектеп")
        assertThat(support.ocrWord("hit")).isEqualTo("hit")
        assertThat(support.ocrWord("Wi-Fi")).isEqualTo("Wi-Fi")
    }

    @Test
    fun `the frequency list takes latin i, h and the schwa inside cyrillic words as the kazakh letters`() {
        assertThat(support.frequencyWord("кiтап")).isEqualTo("кітап")
        assertThat(support.frequencyWord("\u0259лем")).isEqualTo("әлем")
        assertThat(support.frequencyWord("hit")).isEqualTo("hit")
        assertThat(support.frequencyWord("кітап")).isEqualTo("кітап")
    }

    @Test
    fun `headwords are keyed with the kazakh letters as their russian twins in both cases`() {
        val marks = support.headwordMarks!!
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.marks).isEmpty()
        assertThat(marks.replacements).hasSize(18)
        assertThat(marks.replacements).containsEntry("қ", "к")
        assertThat(marks.replacements).containsEntry("Қ", "К")
        assertThat(marks.replacements).containsEntry("ұ", "у")
        assertThat(marks.replacements).containsEntry("ү", "у")
        assertThat(marks.replacements).containsEntry("І", "И")
        assertThat(marks.replacements).containsEntry("һ", "х")
    }

    @Test
    fun `a word that loses some kazakh letters is looked up keyed when its spelling as written finds less`() {
        assertThat(support.keyedSpelling("Қазакстан туралы", 3)).isEqualTo("Казакстан туралы")
        assertThat(support.keyedSpelling("Қазақстанның", 12)).isNull()
        // No Kazakh letter in the first word: the keys already hold its spelling.
        assertThat(support.keyedSpelling("Казакстан ғой", 0)).isNull()
        assertThat(support.keepsWrittenLetters("Қазакстан", "Қазақстан")).isTrue()
        assertThat(support.keepsWrittenLetters("оң", "он")).isFalse()
    }

    @Test
    fun `an ending after digits or an abbreviation is looked up with its hyphen as well`() {
        assertThat(support.partStarts("1990-ға дейін", 0, "1990-ғ".length)).containsExactly(4, 5).inOrder()
        assertThat(support.partStarts("АҚШ-қа", 0, "АҚШ-қ".length)).containsExactly(3, 4).inOrder()
        assertThat(support.partStarts("ҚазМҰУ-да", 0, "ҚазМҰУ-д".length)).containsExactly(6, 7).inOrder()
        assertThat(support.partStarts("км-ге", 0, "км-г".length)).containsExactly(2, 3).inOrder()
        assertThat(support.partStarts("2007-жылы", 0, "2007-ж".length)).containsExactly(4, 5).inOrder()
        // After a word, only the part: сондай-ақ, Әл-Фараби.
        assertThat(support.partStarts("сондай-ақ", 0, "сондай-а".length)).containsExactly(7)
        assertThat(support.partStarts("Әл-Фараби", 0, "Әл-Ф".length)).containsExactly(3)
        assertThat(support.lookupStart("-ға дейін", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(support.lookupStart("- ға", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("-", latinAsNative = false)).isNull()
        assertThat(support.lookupStart("ға", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `cyrillic with the extended letters, lingua libre first, the other scripts' forms left out`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(support.fontSubsets).containsExactly("latin", "cyrillic", "cyrillic-ext")
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("kz")
        assertThat(support.glyph).isEqualTo("Қ")
        assertThat(support.ignoredFormTags).containsExactly("Arabic", "romanization", "Yañalif")
        for (letter in "әғқңөұүһі") assertThat(support.fontSample.lowercase()).contains(letter.toString())
    }
}
