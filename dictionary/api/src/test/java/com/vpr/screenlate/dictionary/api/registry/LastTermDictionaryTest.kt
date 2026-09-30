package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LastTermDictionaryTest {

    private fun dictionary(
        id: Long,
        terms: Long = 1,
        enabled: Boolean = true,
        source: String? = "ja",
    ) = DictionaryEntity(
        id = id, title = "d$id", revision = "1", kind = DictionaryKind.TERM, sourceLanguage = source, targetLanguage = null,
        frequencyMode = null, enabled = enabled, priority = id.toInt(), directory = "d$id", termCount = terms,
        frequencyCount = if (terms == 0L) 1 else 0, pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false,
        indexUrl = null, downloadUrl = null, author = null, url = null, description = null, attribution = null,
        bundled = false, importedAt = 0,
    )

    @Test
    fun `the only enabled dictionary with definitions is the last one`() {
        val jmdict = dictionary(1)
        val frequencies = dictionary(2, terms = 0)
        assertThat(jmdict.isLastTermDictionary(listOf(jmdict, frequencies))).isTrue()
        assertThat(frequencies.isLastTermDictionary(listOf(jmdict, frequencies))).isFalse()
    }

    @Test
    fun `another enabled dictionary with definitions for the language frees it`() {
        val jmdict = dictionary(1)
        val jitendex = dictionary(2)
        assertThat(jmdict.isLastTermDictionary(listOf(jmdict, jitendex))).isFalse()
    }

    @Test
    fun `a switched off one or one for another language does not count`() {
        val jmdict = dictionary(1)
        val off = dictionary(2, enabled = false)
        val english = dictionary(3, source = "en")
        assertThat(jmdict.isLastTermDictionary(listOf(jmdict, off, english))).isTrue()
    }

    @Test
    fun `a dictionary without a language counts for every language`() {
        val jmdict = dictionary(1)
        val any = dictionary(2, source = null)
        assertThat(jmdict.isLastTermDictionary(listOf(jmdict, any))).isFalse()
        assertThat(any.isLastTermDictionary(listOf(jmdict, any))).isFalse()
    }

    @Test
    fun `a switched off dictionary can always be deleted`() {
        val off = dictionary(1, enabled = false)
        assertThat(off.isLastTermDictionary(listOf(off))).isFalse()
    }

    @Test
    fun `an import that switches off every dictionary with definitions keeps the first one on`() {
        val jmdict = dictionary(1)
        val jitendex = dictionary(2)
        val frequencies = dictionary(3, terms = 0)
        val after = listOf(jitendex.copy(enabled = false), jmdict.copy(enabled = false), frequencies.copy(enabled = false))
        assertThat(keptTermDictionaries(listOf(jmdict, jitendex, frequencies), after).map { it.id }).containsExactly(2L)
    }

    @Test
    fun `an import that leaves one on or finds the language empty before keeps nothing`() {
        val jmdict = dictionary(1)
        val jitendex = dictionary(2)
        val off = dictionary(3, enabled = false)
        assertThat(keptTermDictionaries(listOf(jmdict, jitendex), listOf(jmdict.copy(enabled = false), jitendex))).isEmpty()
        assertThat(keptTermDictionaries(listOf(off), listOf(off))).isEmpty()
    }

    @Test
    fun `a dictionary that was off before is not switched on`() {
        val jmdict = dictionary(1)
        val off = dictionary(2, enabled = false)
        val after = listOf(off, jmdict.copy(enabled = false))
        assertThat(keptTermDictionaries(listOf(jmdict, off), after).map { it.id }).containsExactly(1L)
    }

    @Test
    fun `one kept dictionary without a language covers every language`() {
        val any = dictionary(1, source = null)
        val english = dictionary(2, source = "en")
        val after = listOf(any.copy(enabled = false), english.copy(enabled = false))
        assertThat(keptTermDictionaries(listOf(any, english), after).map { it.id }).containsExactly(1L)
    }
}
