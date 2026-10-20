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

    private fun ocrLine(vararg words: String) = CzechSupport.ocrLines(listOf(words.toList())).single()

    @Test
    fun `the l of the era that ocr reads as a capital I is written l`() {
        assertThat(ocrLine("300", "př.", "n.", "I.", "Potom")).containsExactly("300", "př.", "n.", "l.", "Potom").inOrder()
        // Across a line break, before a comma and in parentheses.
        assertThat(CzechSupport.ocrLines(listOf(listOf("roku", "300", "n."), listOf("I.,", "kdy"))))
            .containsExactly(listOf("roku", "300", "n."), listOf("l.,", "kdy")).inOrder()
        assertThat(ocrLine("(př.", "(n.", "I.)")).containsExactly("(př.", "(n.", "l.)").inOrder()
        // A capital I elsewhere stays: a Roman numeral, after a word that only ends in n.
        assertThat(ocrLine("Karel", "I.", "a", "Plán", "I.")).containsExactly("Karel", "I.", "a", "Plán", "I.").inOrder()
        assertThat(ocrLine("Jan.", "I.", "Ina")).containsExactly("Jan.", "I.", "Ina").inOrder()
        // The written l keeps the sentence going.
        val fixed = ocrLine("300", "př.", "n.", "I.", "Potom").joinToString(" ")
        assertThat(ends(fixed, at = fixed.indexOf("l.") + 1)).isFalse()
    }

    @Test
    fun `a capital I written after n in app text is taken as written`() {
        // A Roman ordinal before a lowercase word, a letter of its own before a capital.
        val ordinal = "300 př. n. I. a rostlo"
        assertThat(ends(ordinal, at = ordinal.indexOf("I.") + 1)).isFalse()
        val capital = "300 př. n. I. Potom"
        assertThat(ends(capital, at = capital.indexOf("I.") + 1)).isTrue()
        assertThat(ends("Plán I. Potom")).isTrue()
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
