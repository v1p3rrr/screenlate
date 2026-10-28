package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UkrainianSupportTest {

    private val support = UkrainianSupport

    private fun ends(text: String, at: Int = text.indexOf('.')) = support.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the usual sentence-final ones do`() {
        assertThat(ends("у 1991 р. він")).isFalse()
        assertThat(ends("т. зв. ефект")).isFalse()
        assertThat(ends("т. зв. ефект", at = 5)).isFalse()
        assertThat(ends("у т. ч. діти", at = 6)).isFalse()
        assertThat(ends("див. вище")).isFalse()
        assertThat(ends("на вул. Шевченка")).isFalse()
        assertThat(ends("Це кіт. Він")).isTrue()
        assertThat(ends("і т. д. Потім", at = 6)).isTrue()
    }

    @Test
    fun `the closing low quote opens nothing`() {
        assertThat(support.quotePairs['„']).isEqualTo('“')
        assertThat(support.quotePairs['«']).isEqualTo('»')
        assertThat(support.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `latin i and i with diaeresis become the ukrainian letters, alone as well`() {
        assertThat(support.ocrWord("мiсто")).isEqualTo("місто")
        assertThat(support.ocrWord("ïжак")).isEqualTo("їжак")
        assertThat(support.ocrWord("Ïжак")).isEqualTo("Їжак")
        // The conjunction і has no other letter to show its script.
        assertThat(support.ocrWord("i")).isEqualTo("і")
        assertThat(support.ocrWord("I,")).isEqualTo("І,")
        assertThat(support.ocrWord("Wi-Fi")).isEqualTo("Wi-Fi")
    }

    @Test
    fun `wordfreq words with an apostrophe get the other apostrophes`() {
        assertThat(support.frequencySpellings("п'ять")).containsExactly("п’ять", "пʼять")
        assertThat(support.frequencySpellings("вода")).isEmpty()
    }

    @Test
    fun `cyrillic is read by the cloud, fonts cover the modifier apostrophe, wiktionary comes first`() {
        assertThat(support.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(support.systemFonts.unicodeRange).contains("U+0400-052F")
        assertThat(support.systemFonts.unicodeRange).contains("U+02BC")
        assertThat(support.fontSubsets).containsExactly("latin", "cyrillic")
        assertThat(support.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
        assertThat(support.flagCountries).containsExactly("ua")
        assertThat(support.glyph).isEqualTo("У")
        assertThat(support.headwordMarks!!.bases).contains("ї")
    }
}
