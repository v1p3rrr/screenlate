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

    @Test
    fun `words start after runs of spaces`() {
        assertThat(VietnameseSupport.wordStarts("học  sinh giỏi", 11)).containsExactly(0, 5, 10).inOrder()
        assertThat(VietnameseSupport.wordStarts(" học", 1)).isEmpty()
        assertThat(VietnameseSupport.wordStarts("sinh", 0)).isEmpty()
    }
}
