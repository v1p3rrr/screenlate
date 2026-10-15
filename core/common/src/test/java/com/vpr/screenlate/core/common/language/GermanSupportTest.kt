package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GermanSupportTest {

    private fun ends(text: String, at: Int = text.indexOf('.')) = GermanSupport.endsSentence(text, at)

    @Test
    fun `low quotes close with the English opening quote`() {
        assertThat(GermanSupport.quotePairs).containsEntry('„', '“')
        assertThat(GermanSupport.quotePairs).containsEntry('»', '«')
        assertThat(GermanSupport.quotePairs.keys).doesNotContain('“')
    }

    @Test
    fun `ordinals and abbreviations do not end a sentence`() {
        assertThat(ends("Am 3. Oktober")).isFalse()
        assertThat(ends("im 19. Jahrhundert")).isFalse()
        assertThat(ends("Obst, z.B. Äpfel")).isFalse()
        assertThat(ends("Obst, z. B. Äpfel", 7)).isFalse()
        assertThat(ends("Obst, z. B. Äpfel", 10)).isFalse()
        assertThat(ends("Es regnet. Morgen")).isTrue()
    }

    @Test
    fun `a year or a word with digits at the end of a sentence ends it`() {
        assertThat(ends("Er kam 2020. Dann")).isTrue()
        assertThat(ends("Version A4. Dann")).isTrue()
    }

    @Test
    fun `standard German counts as Germany and regional variants as no region`() {
        val names = GermanSupport.transcriptionRegionNames
        assertThat(TranscriptionRegions.of(listOf("std", "Sing"), names)).containsExactly("de")
        assertThat(TranscriptionRegions.of(listOf("🇩🇪⬆️", "Southern-Germany", "🇦🇹"), names)).containsExactly("at")
        assertThat(GermanSupport.audioRegions.map { it.id }).containsExactly("de", "other", "at", "ch").inOrder()
    }

    @Test
    fun `compound words with a hyphen stay one word`() {
        assertThat(GermanSupport.wordStartOffset("E-Ma", "i")).isEqualTo(4)
    }
}
