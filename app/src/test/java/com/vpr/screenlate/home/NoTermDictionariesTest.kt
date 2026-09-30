package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import org.junit.Test

class NoTermDictionariesTest {
    private fun dictionary(title: String, language: String?, terms: Long = 10, enabled: Boolean = true) = DictionaryEntity(
        title = title,
        revision = "1",
        kind = DictionaryKind.TERM,
        sourceLanguage = language,
        targetLanguage = "en",
        frequencyMode = null,
        enabled = enabled,
        priority = 0,
        directory = title,
        termCount = terms,
        frequencyCount = 0,
        pitchCount = 0,
        kanjiCount = 0,
        mediaCount = 0,
        isUpdatable = false,
        indexUrl = null,
        downloadUrl = null,
        author = null,
        url = null,
        description = null,
        attribution = null,
        bundled = false,
        importedAt = 0,
    )

    @Test
    fun `term dictionaries of another language do not count`() {
        assertThat(noTermDictionaries(listOf(dictionary("zh", "zh")), Language.JAPANESE)).isTrue()
    }

    @Test
    fun `a term dictionary of the language or of no stated language counts when it is on`() {
        assertThat(noTermDictionaries(listOf(dictionary("ja", "ja")), Language.JAPANESE)).isFalse()
        assertThat(noTermDictionaries(listOf(dictionary("any", null)), Language.JAPANESE)).isFalse()
        assertThat(noTermDictionaries(listOf(dictionary("off", "ja", enabled = false)), Language.JAPANESE)).isTrue()
        assertThat(noTermDictionaries(listOf(dictionary("frequencies", "ja", terms = 0)), Language.JAPANESE)).isTrue()
    }
}
