package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PortugueseSupportTest {

    @Test
    fun `Brazil is the default flag and Portugal the second`() {
        assertThat(PortugueseSupport.flagCountries).containsExactly("br", "pt").inOrder()
    }

    @Test
    fun `regional accents count as their country`() {
        val names = PortugueseSupport.transcriptionRegionNames
        assertThat(TranscriptionRegions.of(listOf("🇧🇷RdJ", "🇵🇹"), names)).containsExactly("br", "pt").inOrder()
        assertThat(TranscriptionRegions.of(listOf("European", "Southern"), names)).containsExactly("pt")
        assertThat(PortugueseSupport.audioRegions.map { it.id }).containsExactly("br", "pt", "other").inOrder()
    }

    @Test
    fun `abbreviations do not end a sentence and d' is elided`() {
        fun ends(text: String) = PortugueseSupport.endsSentence(text, text.indexOf('.'))
        assertThat(ends("A Sra. Silva chegou")).isFalse()
        assertThat(ends("frutas, p. ex. maçãs")).isFalse()
        assertThat(ends("Chove. Amanhã")).isTrue()
        assertThat(PortugueseSupport.wordStartOffset("gota d'á", "g")).isEqualTo(1)
    }
}
