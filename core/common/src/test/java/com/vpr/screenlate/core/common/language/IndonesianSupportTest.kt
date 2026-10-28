package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndonesianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = IndonesianSupport.endsSentence(text, at)

    @Test
    fun `abbreviations do not end a sentence, dll does`() {
        assertThat(ends("Tinggal di Jl. Merdeka")).isFalse()
        assertThat(ends("Lihat hlm. 5")).isFalse()
        assertThat(ends("Kepada Yth. Bapak")).isFalse()
        assertThat(ends("Buah, sayur, dll. Lalu")).isTrue()
    }

    @Test
    fun `reduplicated words stay whole`() {
        assertThat(IndonesianSupport.wordStartOffset("rumah-ru", "m")).isEqualTo(8)
    }

    @Test
    fun `two letters stand in the bubble and fonts need only basic latin`() {
        assertThat(IndonesianSupport.glyph).isEqualTo("I")
        assertThat(IndonesianSupport.fontSubsets).containsExactly("latin")
        assertThat(IndonesianSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
    }
}
