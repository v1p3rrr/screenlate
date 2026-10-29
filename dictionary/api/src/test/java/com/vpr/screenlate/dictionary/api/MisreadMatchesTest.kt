package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.BulgarianSupport
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MacedonianSupport
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MisreadMatchesTest {

    private val headwords = listOf("љубов", "љала", "лаља", "лале")

    /** What a lookup by words finds from the start of a text: each headword it starts with, case aside. */
    private fun engine(text: String): List<LookupResult> = headwords.filter { text.lowercase().startsWith(it) }
        .map { LookupResult(matched = text.take(it.length), deinflected = it, term = TermEntry(it, "")) }

    private fun matches(text: String, support: LanguageSupport = MacedonianSupport): List<Pair<String, String>> = runBlocking {
        misreadMatches(text, support, { engine(it) }) { it }.map { it.term.expression to it.matched }
    }

    @Test
    fun `an L read for LJ finds the word, matched over the text as read`() {
        assertThat(matches("лубов е тука")).containsExactly("љубов" to "лубов")
        assertThat(matches("Лубов")).containsExactly("љубов" to "Лубов")
    }

    @Test
    fun `LJ read as L and the soft sign covers both letters`() {
        assertThat(matches("ЛЬубов е")).containsExactly("љубов" to "ЛЬубов")
    }

    @Test
    fun `the first spelling that finds something is shown`() {
        // лала: Љ in the first place finds љала, so лаља is not tried.
        assertThat(matches("лала")).containsExactly("љала" to "лала")
    }

    @Test
    fun `nothing for a word without an L or a language without misread letters`() {
        assertThat(matches("мачка")).isEmpty()
        assertThat(matches("лубов", BulgarianSupport)).isEmpty()
    }

    @Test
    fun `what the shown filter drops does not stop the next spelling`() {
        val found = runBlocking {
            misreadMatches("лала", MacedonianSupport, { engine(it) }) { results -> results.filter { it.term.expression != "љала" } }
        }
        assertThat(found.map { it.term.expression }).containsExactly("лаља")
    }
}
