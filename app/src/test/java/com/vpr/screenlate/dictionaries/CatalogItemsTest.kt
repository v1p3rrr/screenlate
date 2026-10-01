package com.vpr.screenlate.dictionaries

import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogItemsTest {
    private fun entry(title: String) = CatalogEntry(
        id = title, title = title, installedTitle = title, kind = DictionaryKind.TERM, sourceLanguage = "ja",
        downloadUrl = "https://example.org/$title.zip",
    )

    private fun dictionary(title: String) = DictionaryEntity(
        title = title, revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ja", targetLanguage = "en",
        frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0,
        pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
        author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
    )

    private fun task(name: String, state: ImportTask.State, percent: Int? = null) =
        ImportTask(UUID.randomUUID(), name, state, percent, titles = emptyList(), error = null)

    @Test
    fun `an entry gets its unfinished import, finished ones are left out`() {
        val downloading = task("A", ImportTask.State.DOWNLOADING, 40)
        val items = catalogItems(
            entries = listOf(entry("A"), entry("B"), entry("C")),
            dictionaries = listOf(dictionary("B")),
            tasks = listOf(downloading, task("B", ImportTask.State.SUCCEEDED), task("C", ImportTask.State.FAILED)),
        )

        assertEquals(downloading, items[0].task)
        assertFalse(items[0].installed)
        assertNull(items[1].task)
        assertTrue(items[1].installed)
        assertNull(items[2].task)
    }

    @Test
    fun `an update of an installed dictionary shows on its entry too`() {
        // Updates are named after the installed dictionary, whose title differs from the entry's.
        val entry = entry("Wiktionary (ja–en)").copy(installedTitle = "wty-ja-en")
        val update = task("wty-ja-en", ImportTask.State.QUEUED)
        val item = catalogItems(listOf(entry), listOf(dictionary("wty-ja-en")), listOf(update)).single()

        assertTrue(item.installed)
        assertEquals(update, item.task)
    }

    @Test
    fun `the ring is empty while queued, fills with a known percent and spins otherwise`() {
        assertEquals(0f, ringProgress(task("A", ImportTask.State.QUEUED, 30)))
        assertEquals(0.45f, ringProgress(task("A", ImportTask.State.DOWNLOADING, 45))!!, 0.0001f)
        assertEquals(1f, ringProgress(task("A", ImportTask.State.CONVERTING, 130))!!, 0.0001f)
        assertNull(ringProgress(task("A", ImportTask.State.DOWNLOADING)))
        assertNull(ringProgress(task("A", ImportTask.State.IMPORTING)))
    }
}
