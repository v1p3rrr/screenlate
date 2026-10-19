package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_BUNDLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_MODEL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_URL
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.overlay.install.InstallationStatus
import java.util.UUID
import org.junit.Test

class InstallingTest {
    private fun entry(id: String, category: CatalogCategory) = CatalogEntry(
        id, id, installedTitle = id, kind = DictionaryKind.TERM, declaredCategory = category,
        sourceLanguage = "ja", downloadUrl = "https://example.org/$id.zip",
    )

    private fun task(entry: String?, source: String, state: ImportTask.State = ImportTask.State.DOWNLOADING) =
        ImportTask(UUID.randomUUID(), entry.orEmpty(), state, null, emptyList(), null, source = source, language = "ja", catalogEntry = entry)

    private fun status(missing: Set<CatalogCategory>, tasks: List<ImportTask>) = InstallationStatus(
        Language.JAPANESE, missing, deviceReady = false, requiresDevice = false, tasks = tasks, failures = 0,
    )

    private val entries = listOf(entry("jitendex", CatalogCategory.MAIN), entry("paddle-v6", CatalogCategory.OCR_MODEL))

    @Test
    fun `a category being installed is not reported missing`() {
        val missing = setOf(CatalogCategory.MAIN, CatalogCategory.OCR_MODEL)

        assertThat(missingNotInstalling(status(missing, listOf(task("jitendex", SOURCE_URL))), entries))
            .containsExactly(CatalogCategory.OCR_MODEL)
        assertThat(missingNotInstalling(status(missing, listOf(task("paddle-v6", SOURCE_MODEL))), entries))
            .containsExactly(CatalogCategory.MAIN)
        assertThat(missingNotInstalling(status(missing, emptyList()), entries)).isEqualTo(missing)
        // The bundled install has no catalog entry and may bring anything.
        assertThat(missingNotInstalling(status(missing, listOf(task(null, SOURCE_BUNDLED))), entries)).isEmpty()
    }

    @Test
    fun `a model download alone does not install dictionaries`() {
        assertThat(installsDictionaries(listOf(task("paddle-v6", SOURCE_MODEL)))).isFalse()
        assertThat(installsDictionaries(listOf(task("paddle-v6", SOURCE_MODEL), task("jitendex", SOURCE_URL)))).isTrue()
        assertThat(installsDictionaries(listOf(task("jitendex", SOURCE_URL, ImportTask.State.SUCCEEDED)))).isFalse()
    }
}
