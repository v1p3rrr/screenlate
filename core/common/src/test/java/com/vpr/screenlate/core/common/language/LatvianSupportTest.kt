package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatvianSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = LatvianSupport.endsSentence(text, at)

    @Test
    fun `ordinals, years before a lowercase word and abbreviations do not end a sentence`() {
        assertThat(ends("18. novembrī")).isFalse()
        assertThat(ends("1918. gada rudenī")).isFalse()
        assertThat(ends("Tas bija 1918.  gadā")).isFalse()
        assertThat(ends("20. gs. sākumā", at = 6)).isFalse()
        assertThat(ends("Āboli, bumbieri u.c. augļi", at = 19)).isFalse()
        assertThat(ends("Tas notika 1918. Pēc tam")).isTrue()
        assertThat(ends("Tas notika 1918.")).isTrue()
        assertThat(ends("Koks aug. Tad")).isTrue()
    }

    @Test
    fun `low quotes close with the high opening quote`() {
        assertThat(LatvianSupport.quotePairs['„']).isEqualTo('“')
        assertThat(LatvianSupport.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `lingua libre comes first and fonts need the extended latin letters`() {
        assertThat(LatvianSupport.defaultAudioSources.first()).isEqualTo("LINGUA_LIBRE")
        assertThat(LatvianSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(LatvianSupport.pronunciationSample).isNull()
    }
}
