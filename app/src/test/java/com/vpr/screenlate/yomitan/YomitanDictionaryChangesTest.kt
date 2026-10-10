package com.vpr.screenlate.yomitan

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import org.junit.Test

class YomitanDictionaryChangesTest {
    @Test
    fun `English import preserves Japanese switches and priority slots`() {
        val installed = listOf(dictionary(1, "Japanese", "ja"), dictionary(2, "English A", "en"),
            dictionary(3, "Japanese frequency", "ja", frequency = true), dictionary(4, "English B", "en"))
        val changes = dictionaryChanges(profile("English B" to true, "Japanese" to false,
            "English A" to false, "Japanese frequency" to false), Language.ENGLISH, installed)

        assertThat(changes.ordered.map { it.id }).containsExactly(1L, 4L, 3L, 2L).inOrder()
        assertThat(changes.switches).containsExactly(4L, true, 2L, false)
        assertThat(changes.missing).isEmpty()
        assertThat(changes.ordered.filter { it.sourceLanguage == "ja" }).containsExactly(installed[0], installed[2]).inOrder()
    }

    @Test
    fun `sort frequency dictionary cannot come from another language`() {
        val japanese = dictionary(1, "Japanese frequency", "ja", frequency = true)
        val english = dictionary(2, "English frequency [2026-10-11]", "en", frequency = true)
        val profile = profile().copy(sortFrequencyDictionary = japanese.title)
        assertThat(dictionaryChanges(profile, Language.ENGLISH, listOf(japanese, english)).sort).isNull()
        assertThat(dictionaryChanges(profile.copy(sortFrequencyDictionary = "English frequency [2026-10-10]"),
            Language.ENGLISH, listOf(japanese, english)).sort).isEqualTo(english)
    }

    @Test
    fun `revision matching stays within the language and absent dictionaries are reported`() {
        val installed = listOf(dictionary(1, "Shared name [2026-10-11]", "ja"),
            dictionary(2, "Shared name [2026-10-10]", "en"), dictionary(3, "Unstated language", null))
        val changes = dictionaryChanges(profile("Shared name [2026-10-09]" to false,
            "Missing" to true, "Unstated language" to false), Language.ENGLISH, installed)
        assertThat(changes.switches).containsExactly(2L, false, 3L, false)
        assertThat(changes.missing).containsExactly("Missing")
    }

    private fun profile(vararg dictionaries: Pair<String, Boolean>) = YomitanSettings.parse(
        """{"options":{"profiles":[{"name":"English","options":{"general":{"language":"en"}}}]}}""",
    ).profiles.single().copy(dictionaries = dictionaries.map { YomitanSettings.Dictionary(it.first, it.second) })

    private fun dictionary(id: Long, title: String, source: String?, frequency: Boolean = false) = DictionaryEntity(
        id = id, title = title, revision = "1", kind = if (frequency) DictionaryKind.FREQUENCY else DictionaryKind.TERM,
        sourceLanguage = source, targetLanguage = null, frequencyMode = null, enabled = true, priority = id.toInt(),
        directory = title, termCount = if (frequency) 0 else 1, frequencyCount = if (frequency) 1 else 0,
        pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
        author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
    )
}
