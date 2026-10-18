package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.io.File
import java.util.UUID
import org.junit.Test

class DamagedModelsTest {
    private fun entry(id: String, model: String) = CatalogEntry(
        id, id, installedTitle = id, kind = DictionaryKind.TERM, declaredCategory = CatalogCategory.OCR_MODEL,
        sourceLanguage = "ru", downloadUrl = "https://example.org/$id.zip", model = model, version = "1", engine = "paddle", sha256 = "hash",
    )

    private fun model(id: String) = InstalledOcrModel(id, "1", "paddle", File(id))

    private fun task(entry: String, state: ImportTask.State) =
        ImportTask(UUID.randomUUID(), entry, state, null, emptyList(), null, language = "ru", catalogEntry = entry)

    @Test
    fun `a damaged model is offered for download with an entry of its model`() {
        val entries = listOf(entry("paddle-detector-ru", "paddle-detector"), entry("paddle-cyrillic-ru", "paddle-cyrillic"))
        val damaged = damagedModels(listOf(model("paddle-cyrillic"), model("gone")), entries, emptyList())
        assertThat(damaged).containsExactly(
            DamagedModel("paddle-cyrillic", entries[1]),
            DamagedModel("gone", null),
        ).inOrder()
    }

    @Test
    fun `a model downloading again leaves the card until its download ends`() {
        val entries = listOf(entry("paddle-cyrillic-ru", "paddle-cyrillic"))
        val damaged = listOf(model("paddle-cyrillic"))
        assertThat(damagedModels(damaged, entries, listOf(task("paddle-cyrillic-ru", ImportTask.State.DOWNLOADING)))).isEmpty()
        assertThat(damagedModels(damaged, entries, listOf(task("paddle-cyrillic-ru", ImportTask.State.FAILED)))).hasSize(1)
    }
}
