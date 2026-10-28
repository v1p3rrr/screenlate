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
        assertThat(ends("Plán N. potom")).isTrue()
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
    fun `the l of the era is written l however the engine splits the words`() {
        // Cloud recognition gives each period as a word of its own, also across a line break.
        assertThat(ocrLine("300", "př", ".", "n", ".", "I", ".", "se")).containsExactly("300", "př", ".", "n", ".", "l", ".", "se").inOrder()
        assertThat(CzechSupport.ocrLines(listOf(listOf("300", "př", ".", "n", "."), listOf("I", ".", "a"))))
            .containsExactly(listOf("300", "př", ".", "n", "."), listOf("l", ".", "a")).inOrder()
        // ML Kit may join the era into one word.
        assertThat(ocrLine("300", "př.", "n.I.", "se")).containsExactly("300", "př.", "n.l.", "se").inOrder()
        assertThat(ocrLine("př.n.I.")).containsExactly("př.n.l.").inOrder()
        // A word that only ends in n, a capital I without a period after it, and an I that is not the era's stay.
        assertThat(ocrLine("Jan", ".", "I", ".", "Ina")).containsExactly("Jan", ".", "I", ".", "Ina").inOrder()
        assertThat(ocrLine("n.", "Ina")).containsExactly("n.", "Ina").inOrder()
        assertThat(ocrLine("n.", "II.")).containsExactly("n.", "II.").inOrder()
        assertThat(ocrLine("n.", "Il.")).containsExactly("n.", "Il.").inOrder()
    }

    @Test
    fun `a word ml kit glues to the era's last period is parted from it`() {
        assertThat(ocrLine("300", "př.", "n.I.a", "se")).containsExactly("300", "př.", "n.l. a", "se").inOrder()
        assertThat(ocrLine("př.n.l.Potom")).containsExactly("př.n.l. Potom").inOrder()
        // Across a line break: "n." ends a line, "l.a" starts the next; and with each period a word of its own.
        assertThat(CzechSupport.ocrLines(listOf(listOf("roku", "300", "n."), listOf("I.a", "kdy"))))
            .containsExactly(listOf("roku", "300", "n."), listOf("l. a", "kdy")).inOrder()
        assertThat(ocrLine("n", ".", "l.se")).containsExactly("n", ".", "l. se").inOrder()
        // The parted era keeps the sentence going.
        val fixed = ocrLine("300", "př.", "n.I.a", "se").joinToString(" ")
        assertThat(ends(fixed, at = fixed.indexOf("l.") + 1)).isFalse()
        // The era before a comma or apart from the next word, a word that only ends in n, other letters: they stay.
        assertThat(ocrLine("n.l.,", "a")).containsExactly("n.l.,", "a").inOrder()
        assertThat(ocrLine("n.l.", "a")).containsExactly("n.l.", "a").inOrder()
        assertThat(ocrLine("Jan.I.a")).containsExactly("Jan.I.a").inOrder()
        assertThat(ocrLine("n.o.a", "s.l.a")).containsExactly("n.o.a", "s.l.a").inOrder()
    }

    @Test
    fun `the era written without spaces does not end a sentence`() {
        val joined = "v roce 300 n.l. vznikl"
        assertThat(ends(joined, at = joined.indexOf("l.") + 1)).isFalse()
        val before = "v roce 300 př.n.l. Potom"
        assertThat(ends(before, at = before.indexOf("l.") + 1)).isFalse()
    }

    @Test
    fun `a capital I written after n in app text is taken as written`() {
        // A Roman ordinal before a lowercase word, an initial before a capital.
        val ordinal = "300 př. n. I. a rostlo"
        assertThat(ends(ordinal, at = ordinal.indexOf("I.") + 1)).isFalse()
        val capital = "300 př. n. I. Potom"
        assertThat(ends(capital, at = capital.indexOf("I.") + 1)).isFalse()
        assertThat(ends("Plán I.")).isTrue()
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
