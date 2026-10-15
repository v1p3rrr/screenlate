package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItalianSupportTest {

    @Test
    fun `a word after an elided article is looked up from after the apostrophe`() {
        assertThat(ItalianSupport.wordStartOffset("dell'", "a")).isEqualTo(0)
        assertThat(ItalianSupport.wordStartOffset("l’ac", "q")).isEqualTo(2)
        assertThat(ItalianSupport.wordStartOffset("quest'", "a")).isEqualTo(0)
        assertThat(ItalianSupport.wordStartOffset("C'", "è")).isEqualTo(0)
    }

    @Test
    fun `angle quotes are a pair and abbreviations do not end a sentence`() {
        assertThat(ItalianSupport.quotePairs).containsEntry('«', '»')
        fun ends(text: String) = ItalianSupport.endsSentence(text, text.indexOf('.'))
        assertThat(ends("Il sig. Rossi arriva")).isFalse()
        assertThat(ends("frutta, ecc. e verdura")).isFalse()
        assertThat(ends("Piove. Domani")).isTrue()
    }

    @Test
    fun `Italian has no audio regions`() {
        assertThat(ItalianSupport.audioRegions).isEmpty()
    }
}
