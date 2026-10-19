package com.vpr.screenlate.languages

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import java.io.File
import java.util.Locale
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
        val categories = setupCategories(catalog, Language.RUSSIAN, glosses = listOf("en"), installed = emptyList())
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

        // Where ML Kit reads the language, no main model leaves it ML Kit's; a model the user ticked becomes main,
        // one ticked because it is installed does not.
        assertThat(mainModel(all, MainModelPick(null), mlKitReads = true)).isNull()
        assertThat(mainModel(withoutRecommended, pick = null, mlKitReads = true)).isNull()
        assertThat(mainModel(withoutRecommended, pick = null, mlKitReads = true, ticked = setOf("ru-best"))).isEqualTo("tesseract-best-rus")
        assertThat(mainModel(all, pick = null, mlKitReads = true, ticked = setOf("ru-fast", "ru-rec"))).isEqualTo("eslav")
    }

    @Test
    fun `beside ml kit no model is ticked by default and nothing is missing`() {
        val recognizer = model("de-rec", "de", "latin")
        val catalog = Catalog(listOf(recognizer), mapOf("de" to setOf(CatalogCategory.OCR_MODEL)))
        val builtIn = setupCategories(catalog, Language.GERMAN, glosses = listOf("en"), installed = emptyList(), mlKitReads = true)
            .single { it.category == CatalogCategory.OCR_MODEL }
        assertThat(builtIn.builtIn).isTrue()
        assertThat(builtIn.fixed).isFalse()
        assertThat(builtIn.ticked(builtIn.items.single(), emptyMap())).isFalse()
        assertThat(missingChoices(listOf(builtIn), emptyMap())).isEmpty()
        assertThat(setupDownloads(listOf(builtIn), emptyMap())).isEmpty()
        assertThat(setupDownloads(listOf(builtIn), mapOf("de-rec" to true)).map { it.id }).containsExactly("de-rec")

        val without = setupCategories(catalog, Language.GERMAN, glosses = listOf("en"), installed = emptyList())
            .single { it.category == CatalogCategory.OCR_MODEL }
        assertThat(without.ticked(without.items.single(), emptyMap())).isTrue()
    }

    @Test
    fun `models are listed on the download screen and an installed one stays ticked`() {
        val categories = setupCategories(Catalog(entries), Language.RUSSIAN, glosses = listOf("en"), installed = emptyList()) {
            it.model == "det"
        }
        val models = categories.single { it.category == CatalogCategory.OCR_MODEL }
        assertThat(models.mandatory).isFalse()
        assertThat(models.items.map { it.entry.id to it.installed }).containsExactly("ru-det" to true, "ru-rec" to false)
        assertThat(setupDownloads(categories, emptyMap()).map { it.id }).containsExactly("ru-rec")
    }

    @Test
    fun `models left by turned-off languages are listed under each language that has them`() {
        val installed = listOf(
            InstalledOcrModel("det", "5", "paddle", File("det")),
            InstalledOcrModel("eslav", "5", "paddle", File("eslav")),
            InstalledOcrModel("arabic", "5", "paddle", File("arabic")),
        )
        val left = leftoverModels(listOf("ja"), entries, installed, Locale.ENGLISH)
        assertThat(left.keys).containsExactly("ru", "uk", "ar")
        assertThat(left.getValue("ru").map { it.id }).containsExactly("det", "eslav").inOrder()
        assertThat(left.getValue("ar").map { it.id }).containsExactly("det", "arabic").inOrder()
        // Russian keeps the detector and its recognizer, which Ukrainian lists too.
        assertThat(leftoverModels(listOf("ru"), entries, installed, Locale.ENGLISH).mapValues { (_, models) -> models.map { it.id } })
            .containsExactly("ar", listOf("arabic"))
        assertThat(leftoverModels(listOf("ja"), entries, emptyList(), Locale.ENGLISH)).isEmpty()
    }

    private val latin = Language.SERBO_CROATIAN_LATIN
    private val cyrillic = Language.SERBO_CROATIAN_CYRILLIC

    @Test
    fun `a serbo-croatian entry brings its models, and the shared dictionaries only while the other entry is off`() {
        assertThat(fileCodes("sh-Latn", turnedOn = listOf(Language.JAPANESE, cyrillic))).containsExactly("sh-Latn")
        assertThat(fileCodes("sh-Latn", turnedOn = listOf(Language.JAPANESE))).containsExactly("sh-Latn", "sh")
        // The card of both entries stands for the dictionaries and each script's models.
        assertThat(fileCodes("sh", turnedOn = listOf(Language.JAPANESE))).containsExactly("sh-Latn", "sh", "sh-Cyrl")
        assertThat(fileCodes("sh", turnedOn = listOf(cyrillic))).containsExactly("sh-Latn")
        assertThat(fileCodes("ru", turnedOn = listOf(Language.JAPANESE))).containsExactly("ru")
        assertThat(fileCodes("xx", turnedOn = listOf(Language.JAPANESE))).containsExactly("xx")
    }

    @Test
    fun `the models of both serbo-croatian entries share one leftover card while both are off`() {
        assertThat(leftoverCode("sh-Latn", turnedOn = listOf(Language.JAPANESE))).isEqualTo("sh")
        assertThat(leftoverCode("sh-Latn", turnedOn = listOf(cyrillic))).isEqualTo("sh-Latn")
        assertThat(leftoverCode("ru", turnedOn = listOf(Language.JAPANESE))).isEqualTo("ru")
        assertThat(leftoverCode("xx", turnedOn = listOf(Language.JAPANESE))).isEqualTo("xx")

        val models = listOf(
            model("latn-det", "sh-Latn", "det"),
            model("latn-rec", "sh-Latn", "v6"),
            model("cyrl-det", "sh-Cyrl", "det"),
            model("cyrl-rec", "sh-Cyrl", "cyrillic"),
        )
        val installed = listOf("det", "v6", "cyrillic").map { InstalledOcrModel(it, "5", "paddle", File(it)) }
        val bothOff = leftoverCards(listOf(Language.JAPANESE), models, installed, Locale.ENGLISH)
        assertThat(bothOff.mapValues { (_, list) -> list.map { it.id } }).containsExactly("sh", listOf("det", "v6", "cyrillic"))
        // With the Cyrillic entry on, the Latin one keeps only the model the other does not read.
        val latinOff = leftoverCards(listOf(cyrillic), models, installed, Locale.ENGLISH)
        assertThat(latinOff.mapValues { (_, list) -> list.map { it.id } }).containsExactly("sh-Latn", listOf("v6"))
    }

    @Test
    fun `each serbo-croatian entry lists the shared dictionaries and its own models`() {
        val catalog = Catalog(
            listOf(
                CatalogEntry(
                    id = "sh-en", title = "sh-en", declaredCategory = CatalogCategory.MAIN, sourceLanguage = "sh",
                    targetLanguage = "en", recommended = true, downloadUrl = "https://example.org/sh-en.zip", downloadSize = 12_000_000,
                ),
                model("latn-rec", "sh-Latn", "v6"),
                model("cyrl-rec", "sh-Cyrl", "cyrillic"),
            ),
        )
        for ((entry, own) in listOf(latin to "latn-rec", cyrillic to "cyrl-rec")) {
            val ids = setupCategories(catalog, entry, glosses = listOf("en"), installed = emptyList())
                .flatMap { category -> category.items.map { it.entry.id } }
            assertThat(ids).containsExactly("sh-en", own)
        }
    }
}
