package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.util.UUID
import org.junit.Test

class MissingDictionariesTest {
    private fun entry(id: String) = CatalogEntry(id, id, kind = DictionaryKind.TERM, sourceLanguage = "ja", downloadUrl = "https://example.org/$id.zip")

    private fun missing(title: String, entry: CatalogEntry?) = MissingDictionary(
        DictionaryEntity(
            title = title, revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ja", targetLanguage = "en",
            frequencyMode = null, enabled = true, priority = 0, directory = title, termCount = 1, frequencyCount = 0,
            pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
            author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
        ),
        entry,
    )

    private fun task(entry: String, state: ImportTask.State) =
        ImportTask(UUID.randomUUID(), entry, state, null, emptyList(), null, language = "ja", catalogEntry = entry)

    @Test
    fun `a dictionary whose download is queued or running leaves the card`() {
        val jitendex = missing("Jitendex", entry("jitendex"))
        val jmdict = missing("JMdict", entry("jmdict"))
        val own = missing("Own file", null)
        val all = listOf(jitendex, jmdict, own)
        assertThat(missingNotDownloading(all, listOf(task("jitendex", ImportTask.State.QUEUED)))).containsExactly(jmdict, own).inOrder()
        assertThat(missingNotDownloading(all, listOf(task("jmdict", ImportTask.State.DOWNLOADING)))).containsExactly(jitendex, own).inOrder()
    }

    @Test
    fun `a finished or failed download brings the dictionary back to the card`() {
        val jitendex = missing("Jitendex", entry("jitendex"))
        assertThat(missingNotDownloading(listOf(jitendex), listOf(task("jitendex", ImportTask.State.FAILED)))).containsExactly(jitendex)
        assertThat(missingNotDownloading(listOf(jitendex), listOf(task("jitendex", ImportTask.State.SUCCEEDED)))).containsExactly(jitendex)
    }
}
