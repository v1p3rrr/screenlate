package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MalaySupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = MalaySupport.endsSentence(text, at)

    @Test
    fun `titles and company names do not end a sentence`() {
        assertThat(ends("Saya jumpa En. Ali")).isFalse()
        assertThat(ends("Syarikat Maju Sdn. Bhd. ialah", at = 18)).isFalse()
        assertThat(ends("Syarikat Maju Sdn. Bhd. ialah", at = 23)).isFalse()
        assertThat(ends("Dia pergi. Saya")).isTrue()
    }

    @Test
    fun `two letters stand in the bubble and the malaysian flag is the default`() {
        assertThat(MalaySupport.glyph).isEqualTo("MS")
        assertThat(MalaySupport.flagCountries).containsExactly("my")
        assertThat(MalaySupport.fontSubsets).containsExactly("latin")
    }
}
