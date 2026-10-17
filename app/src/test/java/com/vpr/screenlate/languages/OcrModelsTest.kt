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
    fun `a language reads with its model entries, each model once and no required one`() {
        val tesseract = model("ru-tess", "ru", "tesseract-fast-rus").copy(engine = "tesseract", recommended = false, file = "tessdata/rus.traineddata")
        val recognizer = model("ru-rec", "ru", "eslav").copy(requires = listOf("det"))
        val models = languageModels(
            "ru",
            listOf(model("ru-det", "ru", "det"), recognizer, tesseract, model("ru-rec-again", "ru", "eslav"), model("uk-rec", "uk", "eslav")),
        )
        assertThat(models.map { Triple(it.id, it.engine, it.requires) })
            .containsExactly(Triple("eslav", "paddle", listOf("det")), Triple("tesseract-fast-rus", "tesseract", emptyList<String>())).inOrder()
        assertThat(models.map { it.recommended }).containsExactly(true, false).inOrder()
        assertThat(tesseract.modelSpec().file).isEqualTo("tessdata/rus.traineddata")
    }

    @Test
    fun `ticked models that read alone get a main switch, the recommended one on`() {
        val fast = model("ru-fast", "ru", "tesseract-fast-rus").copy(engine = "tesseract", recommended = false)
        val best = model("ru-best", "ru", "tesseract-best-rus").copy(engine = "tesseract", recommended = false)
        val recognizer = model("ru-rec", "ru", "eslav").copy(requires = listOf("det"))
        val catalog = Catalog(listOf(model("ru-det", "ru", "det"), recognizer, fast, best))
        val categories = setupCategories(catalog, "ru", glosses = listOf("en"), installed = emptyList())
        val required = setOf("det")

        // The detector is ticked and downloaded but never main.
        val ticked = mainModelCandidates(categories, emptyMap(), required)
        assertThat(ticked.map { it.model }).containsExactly("eslav")
        assertThat(mainModel(ticked, pick = null, mlKitReads = false)).isEqualTo("eslav")

        val all = mainModelCandidates(categories, mapOf("ru-fast" to true, "ru-best" to true), required)
        assertThat(all.map { it.model }).containsExactly("eslav", "tesseract-fast-rus", "tesseract-best-rus").inOrder()
        assertThat(mainModel(all, MainModelPick("tesseract-best-rus"), mlKitReads = false)).isEqualTo("tesseract-best-rus")
        // Without ML Kit one model stays main; a pick that is no longer ticked gives way to the default.
        assertThat(mainModel(all, MainModelPick(null), mlKitReads = false)).isEqualTo("eslav")
        val withoutRecommended = mainModelCandidates(categories, mapOf("ru-rec" to false, "ru-best" to true), required)
        assertThat(mainModel(withoutRecommended, MainModelPick("eslav"), mlKitReads = false)).isEqualTo("tesseract-best-rus")

        // Where ML Kit reads the language, no main model leaves it ML Kit's.
        assertThat(mainModel(all, MainModelPick(null), mlKitReads = true)).isNull()
        assertThat(mainModel(withoutRecommended, pick = null, mlKitReads = true)).isNull()
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
