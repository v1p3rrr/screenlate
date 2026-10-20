package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.VietnameseSupport
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EarlierStartsTest {

    /** The [words] [text] starts with from an index, the longest first, as a lookup by words finds them. */
    private fun found(text: String, aimedAt: Int, vararg words: String): List<Pair<String, Int>> = runBlocking {
        fromEarlierStarts(text, aimedAt, VietnameseSupport.wordStarts(text, aimedAt)) { start ->
            words.filter { text.startsWith(it, start) }
                .sortedByDescending { it.length }
                .map { LookupResult(matched = it, deinflected = it, term = TermEntry(it, "")) }
        }.map { it.term.expression to it.start }
    }

    @Test
    fun `a word reaching the aimed syllable comes before the syllable's own entries`() {
        assertThat(found("các học sinh giỏi", 8, "các", "học", "học sinh", "sinh", "sinh viên"))
            .containsExactly("học sinh" to 4, "sinh" to 8).inOrder()
    }

    @Test
    fun `the earliest start comes first`() {
        assertThat(found("nhà vệ sinh", 7, "nhà vệ sinh", "nhà", "vệ sinh", "sinh"))
            .containsExactly("nhà vệ sinh" to 0, "vệ sinh" to 4, "sinh" to 7).inOrder()
    }

    /** [reachingAimedCharacter] over [results] found from the word's start, the [words] found as in [found]. */
    private fun reaching(
        text: String,
        results: List<String>,
        aimedCharAt: Int,
        inner: List<Int>,
        vararg words: String,
    ): List<Pair<String, Int>> = runBlocking {
        val shown = results.map { LookupResult(matched = it, deinflected = it, term = TermEntry(it, "")) }
        reachingAimedCharacter(shown, aimedCharAt, inner) { start ->
            words.filter { text.startsWith(it, start) }
                .sortedByDescending { it.length }
                .map { LookupResult(matched = it, deinflected = it, term = TermEntry(it, "")) }
        }.map { it.term.expression to it.start }
    }

    @Test
    fun `a word joined by the segmenter that the dictionary lacks is found from inside when aimed at its later part`() {
        // ក្រុង|ភ្នំ|ពេញ: clusters start at 0, 4, 5, 9 and 11; aimed at ញ (11), ក្រុង stops at 5.
        val text = "ក្រុងភ្នំពេញជា"
        assertThat(reaching(text, listOf("ក្រុង"), 11, listOf(4, 5, 9, 11), "ក្រុង", "ភ្នំពេញ", "ភ្នំ", "ពេញ"))
            .containsExactly("ភ្នំពេញ" to 5, "ក្រុង" to 0).inOrder()
        // Aimed at the first part, the word's own results stay.
        assertThat(reaching(text, listOf("ក្រុង"), 4, listOf(4), "ក្រុង", "ភ្នំពេញ")).containsExactly("ក្រុង" to 0)
        // Nothing from inside reaches the aim either: the results stay as they are.
        assertThat(reaching(text, listOf("ក្រុង"), 11, listOf(4, 5, 9, 11), "ក្រុង", "ភ្នំ"))
            .containsExactly("ក្រុង" to 0)
        // Nothing found from the word's start at all.
        assertThat(reaching(text, emptyList(), 10, listOf(4, 5, 9), "ពេញ")).containsExactly("ពេញ" to 9)
    }

    /** [withHyphenatedParts] over [results] found from the word's start, the [words] found as in [found]. */
    private fun withParts(
        text: String,
        results: List<String>,
        aimedCharAt: Int,
        parts: List<Int>,
        vararg words: String,
    ): List<Pair<String, Int>> = runBlocking {
        val shown = results.map { LookupResult(matched = it, deinflected = it, term = TermEntry(it, "")) }
        withHyphenatedParts(shown, aimedCharAt, parts) { start ->
            words.filter { text.startsWith(it, start) }
                .sortedByDescending { it.length }
                .map { LookupResult(matched = it, deinflected = it, term = TermEntry(it, "")) }
        }.map { it.term.expression to it.start }
    }

    @Test
    fun `a later part of a hyphenated word shows the whole word, then the part, not the first part`() {
        val text = "state-owned firms"
        val aimed = "state-o".length
        assertThat(withParts(text, listOf("state-owned", "state"), aimed, listOf(6), "state-owned", "state", "owned"))
            .containsExactly("state-owned" to 0, "owned" to 6).inOrder()
        assertThat(withParts(text, listOf("state"), aimed, listOf(6), "state", "owned")).containsExactly("owned" to 6)
        // Each part up to the aimed one, the earlier first; words that stop before the aim are left out.
        assertThat(withParts("mother-in-law", listOf("mother"), 11, listOf(7, 10), "mother", "in-law", "in", "law"))
            .containsExactly("in-law" to 7, "law" to 10).inOrder()
        // A suffix entry with its hyphen comes before the ending alone: 1990-ға.
        assertThat(withParts("1990-ға", emptyList(), 5, listOf(4, 5), "-ға", "ға"))
            .containsExactly("-ға" to 4, "ға" to 5).inOrder()
        // The part finds nothing: the whole word only (бала-шаға aimed at шағамен, which has no entry).
        assertThat(withParts("бала-шағамен", listOf("бала-шаға", "бала"), 7, listOf(5), "бала-шаға", "бала"))
            .containsExactly("бала-шаға" to 0)
        // Nothing reaches the aim: the results stay as they are.
        assertThat(withParts(text, listOf("state"), aimed, listOf(6), "state")).containsExactly("state" to 0)
    }

    @Test
    fun `a match covers the characters from its start to its end`() {
        val result = LookupResult(matched = "ពេញ", deinflected = "ពេញ", term = TermEntry("ពេញ", ""), start = 9)
        assertThat(result.covers(9)).isTrue()
        assertThat(result.covers(11)).isTrue()
        assertThat(result.covers(8)).isFalse()
        assertThat(result.covers(12)).isFalse()
    }

    @Test
    fun `words start after runs of spaces`() {
        assertThat(VietnameseSupport.wordStarts("học  sinh giỏi", 11)).containsExactly(0, 5, 10).inOrder()
        assertThat(VietnameseSupport.wordStarts(" học", 1)).isEmpty()
        assertThat(VietnameseSupport.wordStarts("sinh", 0)).isEmpty()
    }
}
