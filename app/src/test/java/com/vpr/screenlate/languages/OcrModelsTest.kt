package com.vpr.screenlate.languages

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import org.junit.Test

class OcrModelsTest {
    private fun model(id: String, source: String, model: String) = CatalogEntry(
        id = id, title = id, declaredCategory = CatalogCategory.OCR_MODEL, sourceLanguage = source, model = model,
        engine = "paddle", version = "5", sha256 = "ab", recommended = true, downloadUrl = "https://example.org/$model.zip",
        downloadSize = 8_000_000,
    )

    private val entries = listOf(
        model("ru-det", "ru", "det"),
        model("ru-rec", "ru", "eslav"),
        model("uk-det", "uk", "det"),
        model("uk-rec", "uk", "eslav"),
        model("ar-det", "ar", "det"),
        model("ar-rec", "ar", "arabic"),
    )

    @Test
    fun `a model shared with another turned-on language stays`() {
        assertThat(modelsOnlyFor("ar", others = listOf("ru"), entries)).containsExactly("arabic")
        assertThat(modelsOnlyFor("ru", others = listOf("uk"), entries)).isEmpty()
        assertThat(modelsOnlyFor("ru", others = listOf("ja"), entries)).containsExactly("det", "eslav")
    }

    @Test
    fun `models are listed on the download screen and an installed one stays ticked`() {
        val categories = setupCategories(Catalog(entries), "ru", glosses = listOf("en"), installed = emptyList()) {
            it.model == "det"
        }
        val models = categories.single { it.category == CatalogCategory.OCR_MODEL }
        assertThat(models.mandatory).isFalse()
        assertThat(models.items.map { it.entry.id to it.installed }).containsExactly("ru-det" to true, "ru-rec" to false)
        assertThat(setupDownloads(categories, emptyMap()).map { it.id }).containsExactly("ru-rec")
    }
}
