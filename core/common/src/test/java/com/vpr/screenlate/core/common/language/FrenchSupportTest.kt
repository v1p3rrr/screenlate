package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrenchSupportTest {

    @Test
    fun `a word after an elided article is looked up from after the apostrophe`() {
        assertThat(FrenchSupport.wordStartOffset("de l'", "e")).isEqualTo(0)
        assertThat(FrenchSupport.wordStartOffset("de l'ea", "u")).isEqualTo(2)
        assertThat(FrenchSupport.wordStartOffset("Qu’", "i")).isEqualTo(0)
        assertThat(FrenchSupport.wordStartOffset("jusqu'", "à")).isEqualTo(0)
    }

    @Test
    fun `apostrophes inside a word keep it whole`() {
        assertThat(FrenchSupport.wordStartOffset("aujourd'h", "u")).isEqualTo(9)
        assertThat(FrenchSupport.wordStartOffset("presqu'î", "l")).isEqualTo(8)
        // English has no elisions: "don't" stays one word.
        assertThat(EnglishSupport.wordStartOffset("don'", "t")).isEqualTo(4)
    }

    @Test
    fun `guillemets are a pair and abbreviations do not end a sentence`() {
        assertThat(FrenchSupport.quotePairs).containsEntry('«', '»')
        fun ends(text: String) = FrenchSupport.endsSentence(text, text.indexOf('.'))
        assertThat(ends("M. Dupont arrive")).isFalse()
        assertThat(ends("des fruits, p. ex. des pommes")).isFalse()
        assertThat(ends("Il pleut. Demain")).isTrue()
    }

    @Test
    fun `Paris and Quebec are regions of the audio order`() {
        val names = FrenchSupport.transcriptionRegionNames
        assertThat(TranscriptionRegions.of(listOf("Quebec", "Paris"), names)).containsExactly("ca", "fr").inOrder()
        assertThat(FrenchSupport.audioRegions.map { it.id }).containsExactly("fr", "ca", "other").inOrder()
        assertThat(FrenchSupport.audioRegions.first().codes).contains("fr-paris")
    }
}
