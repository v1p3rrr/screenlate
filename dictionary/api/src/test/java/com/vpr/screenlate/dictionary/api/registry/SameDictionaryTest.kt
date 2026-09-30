package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SameDictionaryTest {

    private fun dictionary(id: Long, title: String, bundled: Boolean = false, kind: DictionaryKind = DictionaryKind.TERM) =
        DictionaryEntity(
            id = id, title = title, revision = "1", kind = kind, sourceLanguage = "ja", targetLanguage = null,
            frequencyMode = null, enabled = true, priority = id.toInt(), directory = "d$id", termCount = 1,
            frequencyCount = 0, pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null,
            downloadUrl = null, author = null, url = null, description = null, attribution = null, bundled = bundled,
            importedAt = 0,
        )

    @Test
    fun `a new revision mark finds the installed dictionary`() {
        val jmdict = dictionary(1, "JMdict [2026-09-27]", bundled = true)
        assertThat(sameDictionary(listOf(jmdict), "JMdict [2026-10-04]", DictionaryKind.TERM, bundled = true))
            .isEqualTo(jmdict)
        assertThat(sameDictionary(listOf(jmdict), "JMdict [2026-10-04]", DictionaryKind.FREQUENCY, bundled = true))
            .isNull()
    }

    @Test
    fun `a bundled update of several copies takes the bundled one`() {
        val own = dictionary(1, "JMdict [2025-01-01]")
        val shipped = dictionary(2, "JMdict [2026-09-27]", bundled = true)
        val all = listOf(own, shipped)
        assertThat(sameDictionary(all, "JMdict [2026-10-04]", DictionaryKind.TERM, bundled = true)).isEqualTo(shipped)
        assertThat(sameDictionary(all, "JMdict [2026-10-04]", DictionaryKind.TERM, bundled = false)).isNull()
    }
}
