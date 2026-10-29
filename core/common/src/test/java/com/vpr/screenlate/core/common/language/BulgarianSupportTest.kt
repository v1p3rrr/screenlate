package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class BulgarianSupportTest {

    private val support = BulgarianSupport

    private fun ends(text: String, at: Int = text.lastIndexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the usual sentence-final ones do`() {
        assertThat(ends("през 1991 г. той")).isFalse()
        assertThat(ends("т.е. това")).isFalse()
        assertThat(ends("т. е. това")).isFalse()
        assertThat(ends("5 хил. души")).isFalse()
        assertThat(ends("вж. стр. 5", at = 2)).isFalse()
        assertThat(ends("44 г. пр. н. е. Цезар")).isFalse()
        assertThat(ends("44 г. пр.н.е. Цезар")).isFalse()
        assertThat(ends("Това е котка. Тя", at = 12)).isTrue()
        assertThat(ends("Тя е. Тогава")).isTrue()
        assertThat(ends("ябълки и т.н. Тогава")).isTrue()
        assertThat(ends("ябълки и др. Тогава")).isTrue()
    }

    @Test
    fun `low quotes open, the closing ones open nothing`() {
        assertThat(support.quotePairs['„']).isEqualTo('“')
        assertThat(support.quotePairs['‚']).isEqualTo('‘')
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `only the acute stress leaves headwords, the grave of the pronoun stays`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks).isEqualTo("\u0301")
        assertThat(marks.bases).contains("ъ")
    }

    @Test
    fun `the shared latin look-alikes are fixed, nothing of its own`() {
        assertThat(support.ocrWord("кoтка")).isEqualTo("котка")
        assertThat(support.ocrWord("Sвезда")).isEqualTo("Sвезда")
        assertThat(support.ocrWord("й")).isEqualTo("й")
        assertThat(support.misreadSpellings("лубов")).isEmpty()
    }

    @Test
    fun `endings are for words without entries, lingua libre comes first`() {
        assertThat(support.inflectionRulesOnlyWithoutEntry).isTrue()
        assertThat(support.foldsMarks).isTrue()
        assertThat(support.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(support.fontSubsets).containsExactly("latin", "cyrillic")
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("bg")
        assertThat(support.glyph).isEqualTo("Б")
        assertThat(support.codeLabel).isEqualTo("БГ")
        assertThat(Language.of("bg")).isEqualTo(Language.BULGARIAN)
        assertThat(Language.BULGARIAN.lookupDictionaryCodes).containsExactly("bg")
    }
}
