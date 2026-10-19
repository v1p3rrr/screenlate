package com.vpr.screenlate.dictionaries

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdate
import java.io.File
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

    private fun model(id: String, model: String, version: String, language: String = "ru") = CatalogEntry(
        id, id, installedTitle = id, kind = DictionaryKind.TERM, declaredCategory = CatalogCategory.OCR_MODEL,
        sourceLanguage = language, downloadUrl = "https://example.org/$id.zip", model = model, version = version, engine = "paddle", sha256 = "hash",
    )

    @Test
    fun `an installed model whose catalog entry names another version has an update`() {
        val entries = listOf(model("paddle-cyrillic-ru", "paddle-cyrillic", "2"), model("paddle-detector-ru", "paddle-detector", "1"))
        val installed = listOf(
            InstalledOcrModel("paddle-cyrillic", "1", "paddle", File("c")),
            InstalledOcrModel("paddle-detector", "1", "paddle", File("d")),
            InstalledOcrModel("tesseract-fast-rus", "1", "tesseract", File("t")),
        )
        assertThat(modelUpdates(entries, installed)).containsExactly(ModelUpdate(entries[0], "1"))
    }

    @Test
    fun `a model update names every language that reads with it`() {
        val entries = listOf(
            model("paddle-cyrillic-ru", "paddle-cyrillic", "2"),
            model("paddle-cyrillic-uk", "paddle-cyrillic", "2", language = "uk"),
            model("paddle-cyrillic-be", "paddle-cyrillic", "2", language = "be"),
        )
        val installed = listOf(InstalledOcrModel("paddle-cyrillic", "1", "paddle", File("c")))
        assertThat(modelUpdates(entries, installed).single().languages).containsExactly("ru", "uk", "be").inOrder()
    }

    @Test
    fun `started model updates leave the check too`() {
        val update = ModelUpdate(model("paddle-cyrillic-ru", "paddle-cyrillic", "2"), "1")
        assertEquals(UpdateCheck(emptyList(), listOf(update)), UpdateCheck(listOf(a), listOf(update)).without(listOf(a)))
        assertNull(UpdateCheck(listOf(a), listOf(update)).without(listOf(a), listOf(update)))
    }
}
