package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TurkishSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = TurkishSupport.endsSentence(text, at)

    @Test
    fun `ordinals and abbreviations do not end a sentence`() {
        assertThat(ends("15. yüzyılda")).isFalse()
        assertThat(ends("Elma, armut vb. meyveler")).isFalse()
        assertThat(ends("Dr. Ayşe geldi")).isFalse()
        assertThat(ends("Yağmur yağıyor. Yarın")).isTrue()
        assertThat(ends("1923. Sonra")).isTrue()
    }

    @Test
    fun `abbreviations in capitals follow the turkish dotted i`() {
        val company = "ABC LTD. ŞTİ. Sonra"
        assertThat(ends(company)).isFalse()
        assertThat(ends(company, company.indexOf(". S"))).isFalse()
        // Read without the dot, as OCR may give it.
        assertThat(ends("ABC ŞTI. Sonra")).isFalse()
        assertThat(ends("DOÇ. DR. Ayşe")).isFalse()
        assertThat(ends("EVE GELDİ. Sonra")).isTrue()
    }

    @Test
    fun `a suffix after an apostrophe belongs to the name`() {
        assertThat(TurkishSupport.wordStartOffset("Dün İstanbul'", "a")).isEqualTo(9)
    }

    @Test
    fun `lingua libre comes first and fonts need the extended latin letters`() {
        assertThat(TurkishSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(TurkishSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(TurkishSupport.pronunciationSample).isNull()
    }
}
