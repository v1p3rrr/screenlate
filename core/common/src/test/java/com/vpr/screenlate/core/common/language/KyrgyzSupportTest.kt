package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KyrgyzSupportTest {

    private val support = KyrgyzSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `abbreviations hold the sentence before a lowercase word or a number only`() {
        assertThat(ends("1991-ж. жайында")).isFalse()
        assertThat(ends("19-к. башында")).isFalse()
        assertThat(ends("алма ж. б. жемиштер", "алма ж. б".length)).isFalse()
        assertThat(ends("б. з. ч. 500-жылы", "б. з. ч".length)).isFalse()
        assertThat(ends("Мис. алма")).isFalse()
        assertThat(ends("проф. Асанов", "проф".length)).isFalse()
        // Before a capital they end it.
        assertThat(ends("Ал 1991-ж. Кийин")).isTrue()
        assertThat(ends("алма, өрүк ж. б. Анан", "алма, өрүк ж. б".length)).isTrue()
        assertThat(ends("Бул китеп. Ал")).isTrue()
    }

    @Test
    fun `the barred o read by OCR becomes the kyrgyz letter, the latin i stays`() {
        assertThat(support.ocrWord("к\u0275л")).isEqualTo("көл")
        assertThat(support.ocrWord("\u019Fзгөн")).isEqualTo("Өзгөн")
        assertThat(support.ocrWord("м\u0065кт\u0065п")).isEqualTo("мектеп")
        // Kyrgyz has no і: a Latin i stays for the lookup to find nothing.
        assertThat(support.ocrWord("кiтеп")).isEqualTo("кiтеп")
        assertThat(support.ocrWord("hit")).isEqualTo("hit")
        assertThat(support.frequencyWord("кiтеп")).isEqualTo("кiтеп")
    }

    @Test
    fun `headwords are keyed with the kyrgyz letters as their russian twins in both cases`() {
        val marks = support.headwordMarks!!
        assertThat(marks.everyHeadword).isTrue()
        assertThat(marks.marks).isEmpty()
        assertThat(marks.replacements).containsExactly(
            "ң", "н", "ө", "о", "ү", "у", "Ң", "Н", "Ө", "О", "Ү", "У",
        )
    }

    @Test
    fun `a word that loses some kyrgyz letters is looked up keyed when its spelling as written finds less`() {
        assertThat(support.keyedSpelling("Түштук жакта", 3)).isEqualTo("Туштук жакта")
        assertThat(support.keyedSpelling("түштүк", 6)).isNull()
        // Kazakh letters are no twins in Kyrgyz.
        assertThat(support.keyedSpelling("Қазакстан", 3)).isNull()
        assertThat(support.keepsWrittenLetters("Түштук", "түштүк")).isTrue()
        assertThat(support.keepsWrittenLetters("көл", "кол")).isFalse()
    }

    @Test
    fun `an ending or a particle after a hyphen is looked up with its hyphen as well`() {
        assertThat(support.partStarts("2007-жылы", 0, "2007-ж".length)).containsExactly(4, 5).inOrder()
        assertThat(support.partStarts("19-кылымдын", 0, "19-к".length)).containsExactly(2, 3).inOrder()
        assertThat(support.partStarts("СССР-дин", 0, "СССР-д".length)).containsExactly(4, 5).inOrder()
        assertThat(support.partStarts("ата-эне", 0, "ата-э".length)).containsExactly(3, 4).inOrder()
        assertThat(support.lookupStart("-жылы", latinAsNative = false)).isEqualTo(LookupStart.Any)
    }

    @Test
    fun `cyrillic with the extended letters, lingua libre first, the other scripts' forms left out`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(support.fontSubsets).containsExactly("latin", "cyrillic", "cyrillic-ext")
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("kg")
        assertThat(support.glyph).isEqualTo("Ң")
        assertThat(support.ignoredFormTags).containsExactly("Arabic", "romanization", "Yañalif")
        for (letter in "ңөү") assertThat(support.fontSample.lowercase()).contains(letter.toString())
    }
}
