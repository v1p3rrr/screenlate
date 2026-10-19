package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CzechSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = CzechSupport.endsSentence(text, at)

    @Test
    fun `ordinals, the era and abbreviations do not end a sentence`() {
        assertThat(ends("Dne 5. května")).isFalse()
        assertThat(ends("v 19. st. se", at = 8)).isFalse()
        assertThat(ends("Ovoce, např. jablka")).isFalse()
        assertThat(ends("v roce 300 př. n. l. vznikl", at = 13)).isFalse()
        assertThat(ends("v roce 300 př. n. l. vznikl", at = 16)).isFalse()
        assertThat(ends("v roce 300 př. n. l. vznikl", at = 19)).isFalse()
        assertThat(ends("Řekl to nám. Pak")).isTrue()
        assertThat(ends("Stalo se to v roce 1990. Pak")).isTrue()
        assertThat(ends("Plán N. Potom")).isTrue()
    }

    @Test
    fun `low quotes close with the high opening quote`() {
        assertThat(CzechSupport.quotePairs['„']).isEqualTo('“')
        assertThat(CzechSupport.quotePairs['»']).isEqualTo('«')
        assertThat(CzechSupport.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `wiktionary comes first and fonts need the extended latin letters`() {
        assertThat(CzechSupport.defaultAudioSources.first()).isEqualTo("WIKTIONARY")
        assertThat(CzechSupport.fontSubsets).containsExactly("latin", "latin-ext")
        assertThat(CzechSupport.compoundLinks).isNull()
    }
}
