package com.vpr.screenlate.dictionaries

import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryLinksTest {
    private fun dictionary(url: String? = null, downloadUrl: String? = null) = DictionaryEntity(
        title = "Dict", revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ja", targetLanguage = "en",
        frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0,
        pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = downloadUrl,
        author = null, url = url, description = null, attribution = null, bundled = false, importedAt = 0,
    )

    private val entry = CatalogEntry(
        id = "dict", title = "Dict", installedTitle = "Dict", kind = DictionaryKind.TERM, sourceLanguage = "ja",
        downloadUrl = "https://example.org/dict.zip", homepage = "https://example.org",
    )

    @Test
    fun `the dictionary's own links come first`() {
        val links = DictionaryLinks.of(dictionary("https://own.example", "https://own.example/d.zip"), entry)
        assertEquals(DictionaryLinks("https://own.example", "https://own.example/d.zip"), links)
    }

    @Test
    fun `the catalog fills missing links`() {
        assertEquals(DictionaryLinks("https://example.org", "https://example.org/dict.zip"), DictionaryLinks.of(dictionary(), entry))
    }

    @Test
    fun `only web links are kept, and a download equal to the website is shown once`() {
        assertTrue(DictionaryLinks.of(dictionary("mailto:a@b", "file:///x.zip"), null).isEmpty)
        assertEquals(
            DictionaryLinks("https://example.org", null),
            DictionaryLinks.of(dictionary("https://example.org", "https://example.org"), null),
        )
    }
}
