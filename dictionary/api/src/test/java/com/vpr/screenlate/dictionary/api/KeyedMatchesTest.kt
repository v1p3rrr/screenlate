package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.EnglishSupport
import com.vpr.screenlate.core.common.language.KazakhSupport
import com.vpr.screenlate.core.common.language.KyrgyzSupport
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.model.Transform
import kotlinx.coroutines.runBlocking
import org.junit.Test

class KeyedMatchesTest {

    /** Headwords by the texts they are keyed under: their own spelling and the one with every Kazakh letter's twin. */
    private val keyed = mapOf(
        "қаз" to listOf("қаз"), "каз" to listOf("қаз"),
        "Казакстан" to listOf("Қазақстан"), "оң" to listOf("оң"), "он" to listOf("он", "оң", "өң"),
        "онтустик" to listOf("оңтүстік"), "туштук" to listOf("түштүк", "түшүү"),
    )

    /** What a lookup by words finds from the start of a text: each key it starts with, the longest first. */
    private fun engine(text: String): List<LookupResult> = keyed.keys.filter { text.startsWith(it) }
        .sortedByDescending { it.length }
        .flatMap { key -> keyed.getValue(key).map { LookupResult(matched = key, deinflected = key, term = TermEntry(it, "")) } }

    private fun matches(text: String, support: LanguageSupport = KazakhSupport): List<Pair<String, String>> = runBlocking {
        keyedMatches(text, engine(text), support) { engine(it) }.map { it.term.expression to it.matched }
    }

    @Test
    fun `a word that keeps some kazakh letters and loses others is found through the keys`() {
        assertThat(matches("Қазакстан туралы")).containsExactly("Қазақстан" to "Қазакстан")
        assertThat(matches("оңтустік жақта")).containsExactly("оңтүстік" to "оңтустік")
    }

    @Test
    fun `a word found only through an ending also shows the keyed words as long, but no word found already`() {
        // Kyrgyz Түштук: түшүү through the past ending's twin spelling as written, түштүк through the keys.
        val past = LookupResult(
            matched = "Түштук", deinflected = "түшүү", trace = listOf(Transform("past")), term = TermEntry("түшүү", ""),
        )
        val keyed = runBlocking { keyedMatches("Түштук", listOf(past), KyrgyzSupport) { engine(it.lowercase()) } }
        assertThat(keyed.map { it.term.expression to it.matched }).containsExactly("түштүк" to "Түштук")
        // One more step than the written match, so it sorts after it.
        assertThat(keyed.single().preprocessorSteps).isGreaterThan(past.preprocessorSteps)
    }

    @Test
    fun `nothing is added when the word is found as written or writes no kazakh letter`() {
        // "оң" finds оң as written, so өң, keyed under он as well, does not show; nor in a longer word.
        assertThat(matches("оң жақ")).isEmpty()
        assertThat(matches("оңтустік жақта").map { it.first }).doesNotContain("өң")
        assertThat(matches("Казакстан")).isEmpty()
    }

    @Test
    fun `a headword with another letter where the text writes a kazakh one is left out`() {
        assertThat(KazakhSupport.keepsWrittenLetters("Қазакстан", "Қазақстан")).isTrue()
        assertThat(KazakhSupport.keepsWrittenLetters("қазакстанның", "Қазақстан")).isTrue()
        assertThat(KazakhSupport.keepsWrittenLetters("оң", "он")).isFalse()
        assertThat(KazakhSupport.keepsWrittenLetters("оң", "өң")).isTrue()
    }

    @Test
    fun `languages without the keys look nothing up again`() {
        assertThat(EnglishSupport.keyedSpelling("Қазакстан", 0)).isNull()
        assertThat(matches("Қазакстан", EnglishSupport)).isEmpty()
    }
}
