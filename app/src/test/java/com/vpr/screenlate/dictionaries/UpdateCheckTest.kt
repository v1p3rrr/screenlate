package com.vpr.screenlate.dictionaries

import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {
    private fun update(title: String) = DictionaryUpdate(
        DictionaryEntity(
            title = title, revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ja", targetLanguage = "en",
            frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0,
            pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = true, indexUrl = null, downloadUrl = null,
            author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
        ),
        revision = "2",
        downloadUrl = "https://example.org/$title.zip",
    )

    private val a = update("A")
    private val b = update("B")

    @Test
    fun `started updates leave the list`() {
        assertEquals(UpdateCheck(listOf(b)), UpdateCheck(listOf(a, b)).without(listOf(a)))
    }

    @Test
    fun `the check closes once every update has started instead of saying all are up to date`() {
        assertNull(UpdateCheck(listOf(a, b)).without(listOf(a, b)))
        assertNull(UpdateCheck(listOf(a)).without(listOf(a)))
    }
}
