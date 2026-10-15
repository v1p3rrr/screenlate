package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ConvertedLatinAimTest {

    /** Starts with the lengths [matches] gives for each offset (none elsewhere), and the offsets asked. */
    private fun start(word: String, aimed: Int, matches: Map<Int, Int>): Pair<Int, List<Int>> {
        val asked = mutableListOf<Int>()
        val start = runBlocking {
            ConvertedLatinAim.start(JapaneseSupport, word, aimed) { offset ->
                asked += offset
                matches[offset] ?: 0
            }
        }
        return start to asked
    }

    @Test
    fun `the word under the aim starts the lookup`() {
        // jidou | jisho: 自動 and 辞書.
        val words = mapOf(0 to 5, 5 to 5)
        assertThat(start("jidoujisho", 2, words)).isEqualTo(0 to listOf(0))
        assertThat(start("jidoujisho", 5, words)).isEqualTo(5 to listOf(0, 5))
        assertThat(start("jidoujisho", 9, words)).isEqualTo(5 to listOf(0, 5))
    }

    @Test
    fun `nothing found before the aim starts at the syllable under it`() {
        assertThat(start("jidoujisho", 8, mapOf(0 to 5)).first).isEqualTo(7)
        assertThat(start("jidoujisho", 6, emptyMap()).first).isEqualTo(5)
        assertThat(start("screenlate", 7, emptyMap()).first).isEqualTo(6)
    }

    @Test
    fun `the aim far into the word starts at its syllable`() {
        val single = (0 until 20).associateWith { 1 }
        val (start, asked) = start("aiueoaiueoaiueoaiueo", 12, single)
        assertThat(start).isEqualTo(12)
        assertThat(asked).hasSize(ConvertedLatinAim.MAX_SEGMENTS)
    }

    @Test
    fun `syllables follow the romaji rules`() {
        fun syllable(word: String, aimed: Int) = ConvertedLatinAim.syllableStart(JapaneseSupport, word, aimed)
        assertThat(syllable("jidoujisho", 0)).isEqualTo(0)
        assertThat(syllable("jidoujisho", 3)).isEqualTo(2)
        assertThat(syllable("jidoujisho", 4)).isEqualTo(4)
        assertThat(syllable("jidoujisho", 9)).isEqualTo(7)
        // っ from a doubled consonant, ん before a vowel.
        assertThat(syllable("kitte", 3)).isEqualTo(3)
        assertThat(syllable("kitte", 2)).isEqualTo(2)
        assertThat(syllable("konnichiha", 3)).isEqualTo(3)
        assertThat(syllable("konnichiha", 6)).isEqualTo(5)
    }
}
