package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.model.Glossary
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FallbackDictionariesTest {

    private val czech = setOf("Wiktionary cs-en", "Wiktionary cs-ru")

    private fun found(expression: String, vararg dictionaries: String, matched: String = expression, respelled: Boolean = false) =
        LookupResult(
            matched = matched,
            deinflected = expression,
            respelled = respelled,
            term = TermEntry(expression, "", glossaries = dictionaries.map { Glossary(it, "[\"$expression\"]") }),
        )

    private fun candidates(
        written: List<LookupResult>,
        keyed: List<LookupResult> = emptyList(),
        fallback: Set<String> = czech,
        code: String? = "cs",
    ): List<LookupResult> = runBlocking {
        preferringOwn(written, fallback, code) { found, kept -> found + kept(keyed) }
    }

    @Test
    fun `a word the language's dictionaries have shows only their definitions`() {
        val shown = candidates(listOf(found("voda", "Wiktionary sk-en", "Wiktionary cs-en", "Wiktionary sk-ru")))
        assertThat(shown.single().term.glossaries.map { it.dictionary }).containsExactly("Wiktionary sk-en", "Wiktionary sk-ru").inOrder()
        assertThat(shown.single().fallbackLanguage).isNull()
    }

    @Test
    fun `words only the fallback dictionaries have are left out while any candidate has an entry of the language's own`() {
        // "v Bratislave": the Czech v beside a Slovak phrase.
        val shown = candidates(listOf(found("v Bratislave", "Wiktionary sk-en"), found("v", "Wiktionary cs-en", matched = "v")))
        assertThat(shown.map { it.term.expression }).containsExactly("v Bratislave")
    }

    @Test
    fun `without an entry of the language's own the fallback's entries are shown and marked`() {
        val shown = candidates(listOf(found("však", "Wiktionary cs-en", "Wiktionary cs-ru"), found("v", "Wiktionary cs-en")))
        assertThat(shown.map { it.term.expression to it.fallbackLanguage }).containsExactly("však" to "cs", "v" to "cs").inOrder()
        assertThat(shown.first().term.glossaries).hasSize(2)
    }

    @Test
    fun `a word typed without marks found through the keys keeps the fallback's same spelling out`() {
        // Slovak co for čo: Czech has co as written, the Slovak čo comes through the keys without marks.
        val shown = candidates(listOf(found("co", "Wiktionary cs-en"), found("čo", "Wiktionary sk-en", matched = "co", respelled = true)))
        assertThat(shown.map { it.term.expression }).containsExactly("čo")
        assertThat(shown.single().fallbackLanguage).isNull()
    }

    @Test
    fun `entries found through keyed spellings count as the language's own and lose the fallback's definitions`() {
        val shown = candidates(
            written = listOf(found("x", "Wiktionary cs-en")),
            keyed = listOf(found("y", "Wiktionary sk-en", "Wiktionary cs-en"), found("z", "Wiktionary cs-ru")),
        )
        assertThat(shown.map { it.term.expression }).containsExactly("y")
        assertThat(shown.single().term.glossaries.map { it.dictionary }).containsExactly("Wiktionary sk-en")
    }

    @Test
    fun `a language without a fallback keeps every result as found`() {
        val written = listOf(found("water", "Wiktionary en-ru"))
        val shown = candidates(written, fallback = emptySet(), code = null)
        assertThat(shown).isEqualTo(written)
        assertThat(candidates(emptyList(), fallback = emptySet(), code = null)).isEmpty()
    }

    @Test
    fun `nothing found anywhere stays empty`() {
        assertThat(candidates(emptyList())).isEmpty()
    }

    @Test
    fun `only slovak falls back, to czech`() {
        assertThat(Language.SLOVAK.support.fallbackLanguage).isEqualTo(Language.CZECH)
        assertThat(Language.entries.filter { it.support.fallbackLanguage != null }).containsExactly(Language.SLOVAK)
    }
}
