package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
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

    private fun DictionaryEntity.isLast(
        all: List<DictionaryEntity>,
        missing: Set<Long> = emptySet(),
        languages: List<Language> = listOf(Language.JAPANESE),
    ) = isLastTermDictionary(all, languages) { it.id !in missing }

    private fun kept(
        before: List<DictionaryEntity>,
        after: List<DictionaryEntity>,
        missing: Set<Long> = emptySet(),
        languages: List<Language> = listOf(Language.JAPANESE),
    ) = keptTermDictionaries(before, after, languages) { it.id !in missing }

    @Test
    fun `the only enabled dictionary with definitions is the last one`() {
        val jmdict = dictionary(1)
        val frequencies = dictionary(2, terms = 0)
        assertThat(jmdict.isLast(listOf(jmdict, frequencies))).isTrue()
        assertThat(frequencies.isLast(listOf(jmdict, frequencies))).isFalse()
    }

    @Test
    fun `another enabled dictionary with definitions for the language frees it`() {
        val jmdict = dictionary(1)
        val jitendex = dictionary(2)
        assertThat(jmdict.isLast(listOf(jmdict, jitendex))).isFalse()
    }

    @Test
    fun `a switched off one or one for another language does not count`() {
        val jmdict = dictionary(1)
        val off = dictionary(2, enabled = false)
        val english = dictionary(3, source = "en")
        assertThat(jmdict.isLast(listOf(jmdict, off, english))).isTrue()
    }

    @Test
    fun `a dictionary without a language counts for every language`() {
        val jmdict = dictionary(1)
        val any = dictionary(2, source = null)
        assertThat(jmdict.isLast(listOf(jmdict, any))).isFalse()
        assertThat(any.isLast(listOf(jmdict, any))).isFalse()
    }

    @Test
    fun `only turned-on languages count`() {
        val jmdict = dictionary(1)
        val any = dictionary(2, source = null)
        val both = listOf(Language.JAPANESE, Language.ENGLISH)
        assertThat(any.isLast(listOf(jmdict, any), languages = both)).isTrue()
        val english = dictionary(3, source = "en")
        assertThat(english.isLast(listOf(jmdict, english))).isFalse()
        assertThat(english.isLast(listOf(jmdict, english), languages = both)).isTrue()
        val after = listOf(jmdict, english.copy(enabled = false))
        assertThat(kept(listOf(jmdict, english), after)).isEmpty()
        assertThat(kept(listOf(jmdict, english), after, languages = both).map { it.id }).containsExactly(3L)
    }

    @Test
    fun `a switched off dictionary can always be deleted`() {
        val off = dictionary(1, enabled = false)
        assertThat(off.isLast(listOf(off))).isFalse()
    }

    @Test
    fun `an import that switches off every dictionary with definitions keeps the first one on`() {
        val jmdict = dictionary(1)
        val jitendex = dictionary(2)
        val frequencies = dictionary(3, terms = 0)
        val after = listOf(jitendex.copy(enabled = false), jmdict.copy(enabled = false), frequencies.copy(enabled = false))
        assertThat(kept(listOf(jmdict, jitendex, frequencies), after).map { it.id }).containsExactly(2L)
    }

    @Test
    fun `an import that leaves one on or finds the language empty before keeps nothing`() {
        val jmdict = dictionary(1)
        val jitendex = dictionary(2)
        val off = dictionary(3, enabled = false)
        assertThat(kept(listOf(jmdict, jitendex), listOf(jmdict.copy(enabled = false), jitendex))).isEmpty()
        assertThat(kept(listOf(off), listOf(off))).isEmpty()
    }

    @Test
    fun `a dictionary that was off before is not switched on`() {
        val jmdict = dictionary(1)
        val off = dictionary(2, enabled = false)
        val after = listOf(off, jmdict.copy(enabled = false))
        assertThat(kept(listOf(jmdict, off), after).map { it.id }).containsExactly(1L)
    }

    @Test
    fun `one kept dictionary without a language covers every language`() {
        val any = dictionary(1, source = null)
        val english = dictionary(2, source = "en")
        val after = listOf(any.copy(enabled = false), english.copy(enabled = false))
        assertThat(kept(listOf(any, english), after).map { it.id }).containsExactly(1L)
    }

    @Test
    fun `a dictionary whose files are gone does not count`() {
        val jmdict = dictionary(1)
        val gone = dictionary(2)
        assertThat(jmdict.isLast(listOf(jmdict, gone), missing = setOf(2L))).isTrue()
        assertThat(gone.isLast(listOf(gone), missing = setOf(2L))).isFalse()
        val after = listOf(gone, jmdict.copy(enabled = false))
        assertThat(kept(listOf(jmdict, gone), after, missing = setOf(2L)).map { it.id }).containsExactly(1L)
    }
}
