package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import org.junit.Test

class OutdatedCopiesTest {
    private fun entry(id: String, installedTitle: String, indexUrl: String?, oldTitles: List<String> = emptyList()) =
        CatalogEntry(
            id = id, title = id, installedTitle = installedTitle, oldTitles = oldTitles, kind = DictionaryKind.TERM,
            sourceLanguage = "ja", indexUrl = indexUrl, downloadUrl = "https://example.org/$id.zip",
        )

    private fun installed(title: String, indexUrl: String? = null, updatable: Boolean = indexUrl != null) = DictionaryEntity(
        title = title, revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ja", targetLanguage = null,
        frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0,
        pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = updatable, indexUrl = indexUrl, downloadUrl = null,
        author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
    )

    private val jmdict = entry("jmdict", "JMdict [", "https://example.org/jmdict.json", oldTitles = listOf("JMdict"))

    @Test
    fun `a copy without an update address is outdated while the entry has one`() {
        val old = installed("JMdict")
        assertThat(outdatedCopies(listOf(jmdict), listOf(old))).containsExactly(old, jmdict)
        // A copy that says it updates but names no index cannot update itself either.
        val noIndex = installed("JMdict [2024-01-01]", updatable = true)
        assertThat(outdatedCopies(listOf(jmdict), listOf(noIndex))).containsExactly(noIndex, jmdict)
    }

    @Test
    fun `nothing is outdated next to a copy that updates itself, or without an address in the catalog`() {
        val current = installed("JMdict [2026-10-01]", "https://example.org/jmdict.json")
        assertThat(outdatedCopies(listOf(jmdict), listOf(installed("JMdict"), current))).isEmpty()
        assertThat(outdatedCopies(listOf(jmdict), listOf(current))).isEmpty()
        val kolobok = entry("kolobok", "Колобок", indexUrl = null)
        assertThat(outdatedCopies(listOf(kolobok), listOf(installed("Колобок 400k")))).isEmpty()
    }

    @Test
    fun `old titles match whole or dated, never as a prefix of another dictionary`() {
        assertThat(jmdict.matches(null, "JMdict")).isTrue()
        assertThat(jmdict.matches(null, "JMdict [2024-01-01]")).isTrue()
        assertThat(jmdict.matches(null, "JMdict (Russian)")).isFalse()
        assertThat(jmdict.matches(null, "JMdictExtra")).isFalse()
    }
}
