package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BelarusianSupportTest {

    private val support = BelarusianSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the usual sentence-final ones do`() {
        assertThat(ends("у 1991 г. ён")).isFalse()
        assertThat(ends("г. зн. гэта")).isFalse()
        assertThat(ends("г. зн. гэта", at = 5)).isFalse()
        assertThat(ends("напр. яблыкі")).isFalse()
        assertThat(ends("Гэта кот. Ён")).isTrue()
        assertThat(ends("і г. д. Потым", at = 6)).isTrue()
    }

    @Test
    fun `the closing low quote opens nothing`() {
        assertThat(support.quotePairs['„']).isEqualTo('“')
        assertThat(support.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `latin i and u with breve become the belarusian letters, alone as well`() {
        assertThat(support.ocrWord("мiнск")).isEqualTo("мінск")
        assertThat(support.ocrWord("праŭда")).isEqualTo("праўда")
        assertThat(support.ocrWord("Ŭ")).isEqualTo("Ў")
        assertThat(support.ocrWord("i")).isEqualTo("і")
        assertThat(support.ocrWord("y")).isEqualTo("у")
        assertThat(support.ocrWord("Wi-Fi")).isEqualTo("Wi-Fi")
    }

    @Test
    fun `cyrillic is read by the cloud, wiktionary comes first, there is no frequency list to respell`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(support.fontSubsets).containsExactly("latin", "cyrillic")
        assertThat(support.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
        assertThat(support.flagCountries).containsExactly("by")
        assertThat(support.glyph).isEqualTo("Б")
        assertThat(support.headwordMarks!!.bases).contains("ы")
        assertThat(support.frequencySpellings("сям'я")).isEmpty()
    }
}
