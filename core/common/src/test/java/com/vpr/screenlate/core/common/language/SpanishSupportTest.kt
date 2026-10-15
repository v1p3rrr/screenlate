package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpanishSupportTest {

    @Test
    fun `angle quotes are a pair, inverted marks are not`() {
        assertThat(SpanishSupport.quotePairs).containsEntry('«', '»')
        assertThat(SpanishSupport.quotePairs).containsEntry('“', '”')
        assertThat(SpanishSupport.quotePairs.keys).containsNoneOf('¿', '¡')
    }

    @Test
    fun `abbreviations do not end a sentence`() {
        fun ends(text: String) = SpanishSupport.endsSentence(text, text.indexOf('.'))
        assertThat(ends("el Sr. García")).isFalse()
        assertThat(ends("libros, revistas, etc. y más")).isFalse()
        assertThat(ends("p. ej. esto")).isFalse()
        assertThat(ends("Fuimos al mar. Luego")).isTrue()
        assertThat(ends("Llegó tarde. Luego")).isTrue()
    }

    @Test
    fun `a word with accents and ñ is looked up from its start`() {
        assertThat(SpanishSupport.wordStartOffset("Hasta ma", "ñ")).isEqualTo(2)
        assertThat(SpanishSupport.wordStartOffset("¿Dó", "n")).isEqualTo(2)
    }

    @Test
    fun `transcription tags of Latin America and Spain are regions of the audio order`() {
        val flagMexico = String(Character.toChars(0x1F1F2)) + String(Character.toChars(0x1F1FD))
        val regions = TranscriptionRegions.of(listOf(flagMexico, "Latin-America", "Spain"), SpanishSupport.transcriptionRegionNames)
        assertThat(regions).containsExactly("mx", "am-lat", "es").inOrder()
        val latinAmerica = SpanishSupport.audioRegions.first()
        assertThat(latinAmerica.codes).containsAtLeast("am-lat", "mx", "ar", "us")
        assertThat(SpanishSupport.audioRegions.map { it.id }).containsExactly("latam", "es", "other").inOrder()
    }
}
