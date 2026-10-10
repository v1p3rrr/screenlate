package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import org.junit.Test

class IndexLanguageTest {
    private fun dictionary(title: String, source: String?, kind: DictionaryKind = DictionaryKind.PITCH) = DictionaryEntity(
        id = 1, title = title, revision = "1", kind = kind, sourceLanguage = source, targetLanguage = null,
        frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 0, frequencyCount = 0,
        pitchCount = 1, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
        author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
    )

    private val catalog = listOf(
        CatalogEntry(
            id = "en-ipa", title = "Transcription", installedTitle = "wty-en-ipa", kind = DictionaryKind.PITCH,
            sourceLanguage = "en", downloadUrl = "https://example.org/en-ipa.zip",
        ),
    )

    @Test
    fun `a merged dictionary's all names no language`() {
        assertThat(indexLanguage("en")).isEqualTo("en")
        assertThat(indexLanguage("all")).isNull()
        assertThat(indexLanguage("")).isNull()
        assertThat(indexLanguage(null)).isNull()
    }

    @Test
    fun `a dictionary registered under all takes its catalog entry's language, or none`() {
        assertThat(withoutIndexAll(dictionary("wty-en-ipa", "all"), catalog).sourceLanguage).isEqualTo("en")
        assertThat(withoutIndexAll(dictionary("wty-fr-ipa", "all"), catalog).sourceLanguage).isNull()
        val japanese = dictionary("wty-en-ipa", "ja")
        assertThat(withoutIndexAll(japanese, catalog)).isSameInstanceAs(japanese)
    }
}
