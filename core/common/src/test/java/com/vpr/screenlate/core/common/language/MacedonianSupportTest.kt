package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class MacedonianSupportTest {

    private val support = MacedonianSupport

    private fun ends(text: String, at: Int = text.lastIndexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the usual sentence-final ones do`() {
        assertThat(ends("во 1991 г. тој")).isFalse()
        assertThat(ends("т.н. демократија")).isFalse()
        assertThat(ends("нпр. јаболка")).isFalse()
        assertThat(ends("5 илј. денари")).isFalse()
        assertThat(ends("44 г. пр. н. е. Цезар")).isFalse()
        assertThat(ends("Ова е мачка. Таа", at = 11)).isTrue()
        assertThat(ends("Таа е. Потоа")).isTrue()
        assertThat(ends("јаболка итн. Потоа")).isTrue()
    }

    @Test
    fun `low quotes open, the closing ones open nothing`() {
        assertThat(support.quotePairs['„']).isEqualTo('“')
        assertThat(support.quotePairs['‚']).isEqualTo('‘')
        assertThat(support.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `latin and other cyrillic look-alikes become the macedonian letters`() {
        assertThat(support.ocrWord("Sвезда")).isEqualTo("Ѕвезда")
        assertThat(support.ocrWord("sвезда")).isEqualTo("ѕвезда")
        assertThat(support.ocrWord("jас")).isEqualTo("јас")
        assertThat(support.ocrWord("сè")).isEqualTo("сѐ")
        assertThat(support.ocrWord("сé")).isEqualTo("сѐ")
        assertThat(support.ocrWord("сё")).isEqualTo("сѐ")
        assertThat(support.ocrWord("ґавол")).isEqualTo("ѓавол")
        assertThat(support.ocrWord("Їорѓи")).isEqualTo("Ѓорѓи")
        assertThat(support.ocrWord("й")).isEqualTo("ѝ")
        assertThat(support.ocrWord("кoј")).isEqualTo("кој")
        // A word of Latin letters only stays as read; a single letter has no other one to show its script.
        assertThat(support.ocrWord("Jazz")).isEqualTo("Jazz")
        assertThat(support.ocrWord("j")).isEqualTo("ј")
    }

    @Test
    fun `an L of the first word is tried as LJ, one place at a time, the soft sign taken in`() {
        assertThat(support.misreadSpellings("лубов е").map { it.text }).containsExactly("љубов е")
        assertThat(support.misreadSpellings("Лале и").map { it.text }).containsExactly("Љале и", "Лаље и").inOrder()
        assertThat(support.misreadSpellings("лллл")).hasSize(3)
        assertThat(support.misreadSpellings("мачка лале")).isEmpty()
        val soft = support.misreadSpellings("ЛЬубов е").single()
        assertThat(soft.text).isEqualTo("Љубов е")
        assertThat(soft.sourceLength(1)).isEqualTo(2)
        assertThat(soft.sourceLength(5)).isEqualTo(6)
        val plain = support.misreadSpellings("лубов").single()
        assertThat(plain.sourceLength(5)).isEqualTo(5)
    }

    @Test
    fun `the frequency list's latin letters inside cyrillic words are the macedonian ones`() {
        assertThat(support.frequencyWord("сè")).isEqualTo("сѐ")
        assertThat(support.frequencyWord("jас")).isEqualTo("јас")
        assertThat(support.frequencyWord("jazz")).isEqualTo("jazz")
        assertThat(support.frequencyWord("вода")).isEqualTo("вода")
    }

    @Test
    fun `the stress acute leaves headwords, the marks of the letters stay`() {
        val marks = support.headwordMarks!!
        assertThat(marks.marks).isEqualTo("\u0301")
        assertThat(support.inflectionRulesOnlyWithoutEntry).isFalse()
        assertThat(support.foldsMarks).isTrue()
        assertThat(support.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(support.flagCountries).containsExactly("mk")
        assertThat(support.glyph).isEqualTo("М")
        assertThat(support.codeLabel).isEqualTo("МК")
        assertThat(Language.of("mk")).isEqualTo(Language.MACEDONIAN)
    }
}
