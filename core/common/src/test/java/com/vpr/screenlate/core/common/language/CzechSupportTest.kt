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
        // A Roman ordinal before a lowercase word; before a capital it may end the sentence.
        assertThat(ends("Karel IV. byl král")).isFalse()
        assertThat(ends("Vládl Karel IV. Potom")).isTrue()
    }

    @Test
    fun `the l of the era read as a capital I still keeps the sentence going`() {
        val read = "300 př. n. I. a rostlo"
        assertThat(ends(read, at = read.indexOf("I.") + 1)).isFalse()
        assertThat(ends("Plán I. Potom")).isTrue()
        // German pairs no l, so a capital I stays a letter of its own there (before a lowercase word it is a Roman
        // ordinal).
        val capital = "300 př. n. I. Potom"
        assertThat(ends(capital, at = capital.indexOf("I.") + 1)).isFalse()
        assertThat(GermanSupport.endsSentence(capital, capital.indexOf("I.") + 1)).isTrue()
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
