package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.DutchSupport
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CompoundsTest {

    private val links = listOf("s", "es", "n")

    /** Lengths of the [words] (lowercase) [text] starts with, as the engine's prefix matches. */
    private fun prefixes(words: Set<String>, text: String) = words.filter { text.lowercase().startsWith(it) }.map { it.length }

    private fun split(word: String, vararg words: String): List<String>? = runBlocking {
        Compounds.split(word, links) { prefixes(words.toSet(), it) }?.map { word.substring(it.first, it.last + 1) }
    }

    @Test
    fun `the longest word comes first and the rest is split the same way`() {
        assertThat(split("Haustürschlüssel", "hau", "haus", "tür", "schlüssel")).containsExactly("Haus", "tür", "schlüssel")
            .inOrder()
        assertThat(split("Haustürschlüssel", "haus", "haustür", "tür", "schlüssel")).containsExactly("Haustür", "schlüssel")
            .inOrder()
    }

    @Test
    fun `a linking element is skipped only when the rest needs it`() {
        assertThat(split("Arbeitszimmer", "arbeit", "zimmer")).containsExactly("Arbeit", "zimmer").inOrder()
        assertThat(split("Hundesteuer", "hund", "hunde", "steuer", "teuer", "este")).containsExactly("Hunde", "steuer")
            .inOrder()
        assertThat(split("Sonnenschein", "sonne", "schein")).containsExactly("Sonne", "schein").inOrder()
    }

    @Test
    fun `dutch links are skipped the same way`() {
        fun dutch(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, DutchSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        assertThat(dutch("stadsdeel", "stad", "deel")).containsExactly("stad", "deel").inOrder()
        assertThat(dutch("boekenkast", "boek", "kast")).containsExactly("boek", "kast").inOrder()
        assertThat(dutch("pannekoek", "pan", "panne", "koek")).containsExactly("panne", "koek").inOrder()
    }

    @Test
    fun `a shorter first word is tried when the longer one leaves a rest without words`() {
        assertThat(split("Wasserflasche", "wasser", "wasserf", "flasche")).containsExactly("Wasser", "flasche").inOrder()
    }

    @Test
    fun `no split for a word the parts do not cover or for short parts`() {
        assertThat(split("Müller", "müll")).isNull()
        assertThat(split("Ölkanne", "öl", "kanne")).isNull()
        assertThat(split("Hausxyz", "haus")).isNull()
    }

    @Test
    fun `the word ends at the first character that is not a letter`() {
        assertThat(Compounds.word("Haustür, sagte er")).isEqualTo("Haustür")
        assertThat(Compounds.word("3. Oktober")).isEmpty()
    }

    private fun result(matched: String, expression: String) =
        LookupResult(matched = matched, deinflected = expression, term = TermEntry(expression, ""))

    @Test
    fun `parts carry the whole word as their match and a word with an entry is not split`() = runBlocking {
        val dictionary = mapOf("haus" to "Haus", "tür" to "Tür")
        val lookup: suspend (String) -> List<LookupResult> = { text ->
            dictionary.filterKeys { text.lowercase().startsWith(it) }.map { (key, word) -> result(text.take(key.length), word) }
        }
        val parts = compoundParts("Haustür ist", listOf(result("Haus", "Haus")), links, lookup)
        assertThat(parts?.map { part -> part.map { it.term.expression } }).containsExactly(listOf("Haus"), listOf("Tür"))
            .inOrder()
        assertThat(parts?.flatten()?.map { it.matched }).containsExactly("Haustür", "Haustür")
        assertThat(compoundParts("Haustür ist", listOf(result("Haustür", "Haustür")), links, lookup)).isNull()
    }

    @Test
    fun `capitalized headwords come first and keep their order`() {
        val results = listOf(result("lampe", "lampen"), result("lampe", "Lampe"), result("lampe", "lampe"), result("lampe", "Lampen"))
        assertThat(results.capitalizedFirst().map { it.term.expression }).containsExactly("Lampe", "Lampen", "lampen", "lampe")
            .inOrder()
    }
}
