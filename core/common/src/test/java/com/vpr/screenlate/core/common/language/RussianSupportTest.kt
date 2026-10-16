package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RussianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = RussianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, the usual sentence-final ones do`() {
        assertThat(ends("т. е. это")).isFalse()
        assertThat(ends("т. е. это", at = 4)).isFalse()
        assertThat(ends("в 1999 г. он")).isFalse()
        assertThat(ends("XX в. было")).isFalse()
        assertThat(ends("см. выше")).isFalse()
        assertThat(ends("Это кот. Он")).isTrue()
        assertThat(ends("и т. д. Потом", at = 6)).isTrue()
        assertThat(ends("Я дал им. Он")).isTrue()
    }

    @Test
    fun `the closing low quote opens nothing`() {
        assertThat(RussianSupport.quotePairs['„']).isEqualTo('“')
        assertThat(RussianSupport.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `wiktionary comes first, cyrillic is read by the cloud and fonts cover stress marks`() {
        assertThat(RussianSupport.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
        assertThat(RussianSupport.ocrScript).isEqualTo(OcrScript.CYRILLIC)
        assertThat(RussianSupport.fontSubsets).containsExactly("latin", "cyrillic")
        assertThat(RussianSupport.systemFonts.unicodeRange).contains("U+0300-036F")
        assertThat(RussianSupport.systemFonts.unicodeRange).contains("U+0400-052F")
    }
}
