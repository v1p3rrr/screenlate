package com.vpr.screenlate.languages

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionaries.catalogTargetOrder
import com.vpr.screenlate.dictionary.api.catalog.BUILD_FORMS
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.home.InstallProgress
import com.vpr.screenlate.home.installProgress
import java.util.Locale
import java.util.UUID
import org.junit.Test

class LanguageSetupTest {
    private fun entry(
        id: String,
        target: String?,
        category: CatalogCategory? = null,
        kind: DictionaryKind = DictionaryKind.TERM,
        recommended: Boolean = false,
        size: Long = 1_000_000,
        source: String = "en",
        url: String = "https://example.org/$id.zip",
    ) = CatalogEntry(
        id = id, title = id, installedTitle = id, kind = kind, declaredCategory = category, sourceLanguage = source,
        targetLanguage = target, recommended = recommended, downloadUrl = url, downloadSize = size,
    )

    private fun installed(title: String, source: String? = "en", terms: Long = 1, kind: DictionaryKind = DictionaryKind.TERM) =
        DictionaryEntity(
            id = title.hashCode().toLong(), title = title, revision = "1", kind = kind, sourceLanguage = source,
            targetLanguage = null, frequencyMode = null, enabled = true, priority = 0, directory = title, termCount = terms,
            frequencyCount = 0, pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null,
            downloadUrl = null, author = null, url = null, description = null, attribution = null, bundled = false,
            importedAt = 0,
        )

    private val catalog = Catalog(
        listOf(
            entry("en-ru", "ru", CatalogCategory.MAIN, recommended = true, size = 5_000_000),
            entry("en-en", "en", CatalogCategory.MAIN, recommended = true, size = 100_000_000),
            entry("en-de", "de", CatalogCategory.MAIN, recommended = true),
            entry("en-ru-glossary", "ru", CatalogCategory.GLOSSARY),
            entry("en-ipa", null, CatalogCategory.PRONUNCIATION, kind = DictionaryKind.PITCH, recommended = true, size = 4_000_000),
            entry("ja-en", "en", source = "ja", recommended = true),
        ),
    )

    @Test
    fun `the interface language and English are offered, the user fills a slot that would repeat or be missing`() {
        val available = listOf("ru", "en", "de")
        assertThat(glossSlots("en", "ru", available)).containsExactly("ru", "en").inOrder()
        assertThat(glossSlots("en", "en", available)).containsExactly(null, "en").inOrder()
        assertThat(glossSlots("ja", "ja", available)).containsExactly(null, "en").inOrder()
        assertThat(glossSlots("en", "vi", available)).containsExactly(null, "en").inOrder()
    }

    @Test
    fun `the slot the user fills offers the other gloss languages of the language`() {
        val order = catalogTargetOrder("en", Locale.ENGLISH)
        assertThat(glossChoices(catalog.entries, "en", taken = listOf("en"), order)).containsExactly("ru", "de").inOrder()
    }

    @Test
    fun `categories hold the dictionaries into the gloss languages and those without one`() {
        val categories = setupCategories(catalog, Language.ENGLISH, listOf("ru", "en"), installed = emptyList())
        assertThat(categories.map { it.category })
            .containsExactly(CatalogCategory.MAIN, CatalogCategory.PRONUNCIATION, CatalogCategory.GLOSSARY).inOrder()
        val main = categories.first()
        assertThat(main.items.map { it.entry.id }).containsExactly("en-ru", "en-en").inOrder()
        assertThat(main.mandatory).isTrue()
        assertThat(main.fixed).isFalse()
    }

    private val slovak = Catalog(
        listOf(
            entry("sk-en", "en", CatalogCategory.MAIN, recommended = true, source = "sk"),
            entry("cs-en", "en", CatalogCategory.MAIN, recommended = true, source = "cs"),
            entry("cs-ru", "ru", CatalogCategory.MAIN, recommended = true, source = "cs"),
            entry("cs-de", "de", CatalogCategory.MAIN, recommended = true, source = "cs"),
            entry("cs-en-glossary", "en", CatalogCategory.GLOSSARY, source = "cs"),
            entry("cs-freq", null, CatalogCategory.FREQUENCY, kind = DictionaryKind.FREQUENCY, recommended = true, source = "cs"),
        ),
    )

    @Test
    fun `slovak lists the czech main dictionaries into its gloss languages after its own, unticked`() {
        val categories = setupCategories(slovak, Language.SLOVAK, listOf("ru", "en"), installed = emptyList())
        assertThat(categories.map { it.category }).containsExactly(CatalogCategory.MAIN)
        val main = categories.single()
        assertThat(main.items.map { it.entry.id to it.fallback })
            .containsExactly("sk-en" to false, "cs-ru" to true, "cs-en" to true).inOrder()
        // The only Slovak dictionary stays the fixed choice; the Czech ones are ticked only by the user.
        assertThat(main.fixed).isTrue()
        assertThat(main.items.filter { main.ticked(it, emptyMap()) }.map { it.entry.id }).containsExactly("sk-en")
        assertThat(setupDownloads(categories, emptyMap()).map { it.id }).containsExactly("sk-en")
        assertThat(setupDownloads(categories, mapOf("cs-en" to true)).map { it.id }).containsExactly("sk-en", "cs-en")
    }

    @Test
    fun `a czech dictionary does not satisfy slovak's mandatory category, an installed one stays ticked`() {
        val catalog = Catalog(slovak.entries + entry("sk-ru", "ru", CatalogCategory.MAIN, recommended = true, source = "sk"))
        val categories = setupCategories(catalog, Language.SLOVAK, listOf("ru", "en"), installed = listOf(installed("cs-ru", source = "cs")))
        val choices = mapOf("sk-en" to false, "sk-ru" to false, "cs-en" to true)
        assertThat(missingChoices(categories, choices)).containsExactly(CatalogCategory.MAIN)
        assertThat(missingChoices(categories, choices + ("sk-ru" to true))).isEmpty()
        val main = categories.single()
        assertThat(main.items.single { it.entry.id == "cs-ru" }.let { main.ticked(it, emptyMap()) }).isTrue()
    }

    @Test
    fun `languages without a fallback list only their own dictionaries`() {
        val categories = setupCategories(slovak, Language.CZECH, listOf("ru", "en"), installed = emptyList())
        assertThat(categories.flatMap { it.items }.none { it.fallback }).isTrue()
        assertThat(setupCategories(slovak, Language.POLISH, listOf("en"), installed = emptyList()).flatMap { it.items }).isEmpty()
    }

    @Test
    fun `recommended dictionaries are ticked, the user's ticks win, an empty mandatory category is missing`() {
        val categories = setupCategories(catalog, Language.ENGLISH, listOf("ru", "en"), installed = emptyList())
        assertThat(setupDownloads(categories, emptyMap()).map { it.id }).containsExactly("en-ru", "en-en", "en-ipa").inOrder()

        val choices = mapOf("en-ru" to false, "en-en" to false, "en-ru-glossary" to true)
        assertThat(missingChoices(categories, choices)).containsExactly(CatalogCategory.MAIN)
        assertThat(setupDownloads(categories, choices).map { it.id }).containsExactly("en-ipa", "en-ru-glossary").inOrder()
    }

    @Test
    fun `the language's own edition is listed after the gloss languages and ticked only when chosen as one`() {
        val dutch = Catalog(
            listOf(
                entry("nl-nl", "nl", CatalogCategory.MAIN, recommended = true, source = "nl"),
                entry("nl-en", "en", CatalogCategory.MAIN, recommended = true, source = "nl"),
                entry("nl-fr", "fr", CatalogCategory.MAIN, recommended = true, source = "nl"),
            ),
        )

        val listed = setupCategories(dutch, Language.DUTCH, listOf("en"), installed = emptyList())
        assertThat(listed.single().items.map { it.entry.id }).containsExactly("nl-en", "nl-nl").inOrder()
        assertThat(listed.single().fixed).isFalse()
        assertThat(setupDownloads(listed, emptyMap()).map { it.id }).containsExactly("nl-en")
        assertThat(setupDownloads(listed, mapOf("nl-nl" to true)).map { it.id }).containsExactly("nl-en", "nl-nl").inOrder()

        val chosen = setupCategories(dutch, Language.DUTCH, listOf("nl", "en"), installed = emptyList())
        assertThat(setupDownloads(chosen, emptyMap()).map { it.id }).containsExactly("nl-nl", "nl-en").inOrder()
    }

    @Test
    fun `a required model is not listed and is downloaded once with the ticked models that need it`() {
        fun model(id: String, model: String, size: Long, recommended: Boolean = false, requires: List<String> = emptyList()) =
            CatalogEntry(
                id = id, title = id, declaredCategory = CatalogCategory.OCR_MODEL, sourceLanguage = "en",
                recommended = recommended, downloadUrl = "https://example.org/$model.zip", downloadSize = size,
                sha256 = "0".repeat(64), model = model, engine = "paddle", requires = requires, version = "1",
            )
        val models = Catalog(
            listOf(
                model("latin-en", "latin", 7, recommended = true, requires = listOf("detector")),
                model("v6-en", "v6", 18, requires = listOf("detector")),
                model("detector-en", "detector", 4),
            ),
        )
        val categories = setupCategories(models, Language.ENGLISH, listOf("en"), installed = emptyList())
        val items = categories.single { it.category == CatalogCategory.OCR_MODEL }.items
        assertThat(items.map { it.entry.id }).containsExactly("latin-en", "v6-en").inOrder()
        assertThat(setupDownloads(categories, emptyMap()).map { it.id }).containsExactly("detector-en", "latin-en").inOrder()
        assertThat(setupDownloads(categories, mapOf("v6-en" to true)).map { it.id })
            .containsExactly("detector-en", "latin-en", "v6-en").inOrder()
        val withDetector = setupCategories(models, Language.ENGLISH, listOf("en"), installed = emptyList()) { it.model == "detector" }
        assertThat(setupDownloads(withDetector, emptyMap()).map { it.id }).containsExactly("latin-en")
    }

    @Test
    fun `a single mandatory item is fixed and installed items are not downloaded again`() {
        val withoutOwnEdition = Catalog(catalog.entries.filterNot { it.id == "en-en" })
        val categories = setupCategories(withoutOwnEdition, Language.ENGLISH, listOf("de"), installed = listOf(installed("en-ipa", kind = DictionaryKind.PITCH)))
        val main = categories.first { it.category == CatalogCategory.MAIN }
        assertThat(main.fixed).isTrue()
        assertThat(main.ticked(main.items.single(), mapOf("en-de" to false))).isTrue()
        assertThat(setupDownloads(categories, mapOf("en-de" to false)).map { it.id }).containsExactly("en-de")
    }

    @Test
    fun `selected installed dictionaries are enabled without downloads including disabled copies`() {
        val main = installed("en-ru").copy(enabled = false)
        val pronunciation = installed("en-ipa", kind = DictionaryKind.PITCH).copy(enabled = false)
        val unrelated = installed("Own English Dictionary").copy(enabled = false)
        val installed = listOf(main, pronunciation, unrelated)
        val categories = setupCategories(catalog, Language.ENGLISH, listOf("ru"), installed)

        assertThat(missingChoices(categories, emptyMap())).isEmpty()
        assertThat(setupDownloads(categories, emptyMap())).isEmpty()
        assertThat(setupInstalled(categories, emptyMap(), installed)).containsExactly(main.id, pronunciation.id)
    }

    @Test
    fun `a mandatory category without dictionaries in the chosen languages is still shown, and missing`() {
        val categories = setupCategories(Catalog(catalog.entries.filterNot { it.id == "en-en" }), Language.ENGLISH, listOf("fr"), installed = emptyList())
        assertThat(categories.first().category).isEqualTo(CatalogCategory.MAIN)
        assertThat(categories.first().items).isEmpty()
        assertThat(missingChoices(categories, emptyMap())).containsExactly(CatalogCategory.MAIN)

        // The language's own edition is listed as well; as the only main dictionary it is the one to take.
        val ownOnly = setupCategories(catalog, Language.ENGLISH, listOf("fr"), installed = emptyList())
        assertThat(ownOnly.first().items.map { it.entry.id }).containsExactly("en-en")
        assertThat(ownOnly.first().fixed).isTrue()
        assertThat(missingChoices(ownOnly, emptyMap())).isEmpty()
    }

    @Test
    fun `totals add the archives, the typical space after import, and the most space with the largest archive`() {
        val totals = setupTotals(listOf(entry("a", "ru", size = 10), entry("b", "ru", size = 30)))
        assertThat(totals.downloadBytes).isEqualTo(40)
        assertThat(totals.installedBytes).isEqualTo(88)
        assertThat(totals.neededBytes).isEqualTo(230)
    }

    @Test
    fun `an archive the app carries takes space but is not downloaded`() {
        val bundled = entry("hanja", "en", size = 20, url = "asset:///catalog/hanja.zip")
        val totals = setupTotals(listOf(entry("a", "ru", size = 10), bundled))
        assertThat(totals.downloadBytes).isEqualTo(10)
        assertThat(totals.installedBytes).isEqualTo(66)
    }

    private val index = "https://example.org/fi-en-index.json"
    private val finnish = Catalog(
        listOf(
            entry("fi-en", "en", CatalogCategory.MAIN, recommended = true, source = "fi").copy(indexUrl = index),
            entry("fi-ru", "ru", CatalogCategory.MAIN, recommended = true, source = "fi"),
            entry("fi-forms", "en", CatalogCategory.FORMS, recommended = true, source = "fi")
                .copy(installedTitle = null, oldTitles = listOf("wty-fi-en (forms)"), indexUrl = index, build = BUILD_FORMS),
        ),
        mapOf("fi" to setOf(CatalogCategory.MAIN, CatalogCategory.FORMS)),
    )

    @Test
    fun `word forms are a fixed item while the whole dictionary is not ticked, and gone with it`() {
        val glosses = listOf("ru", "en")
        val withEnglish = setupCategories(finnish, Language.FINNISH, glosses, installed = emptyList())
        assertThat(withEnglish.map { it.category }).doesNotContain(CatalogCategory.FORMS)
        assertThat(missingChoices(withEnglish, emptyMap())).isEmpty()
        assertThat(setupDownloads(withEnglish, emptyMap()).map { it.id }).containsExactly("fi-ru", "fi-en").inOrder()

        val unticked = mapOf("fi-en" to false)
        val withoutEnglish = setupCategories(finnish, Language.FINNISH, glosses, installed = emptyList(), choices = unticked)
        val forms = withoutEnglish.single { it.category == CatalogCategory.FORMS }
        assertThat(forms.mandatory).isTrue()
        assertThat(forms.fixed).isTrue()
        assertThat(missingChoices(withoutEnglish, unticked)).isEmpty()
        assertThat(setupDownloads(withoutEnglish, unticked).map { it.id }).containsExactly("fi-ru", "fi-forms").inOrder()

        // The only dictionary of the main category is always taken, and with it the forms.
        val englishOnly = setupCategories(finnish, Language.FINNISH, listOf("en"), installed = emptyList(), choices = unticked)
        assertThat(englishOnly.map { it.category }).doesNotContain(CatalogCategory.FORMS)
        val russianOnly = setupCategories(finnish, Language.FINNISH, listOf("ru"), installed = emptyList())
        assertThat(setupDownloads(russianOnly, emptyMap()).map { it.id }).containsExactly("fi-ru", "fi-forms").inOrder()
    }

    @Test
    fun `the whole dictionary holds its word forms, a copy of the forms only does not count as it`() {
        val whole = installed("wty-fi-en", source = "fi").copy(indexUrl = index)
        val formsOnly = installed("wty-fi-en (forms)", source = "fi").copy(indexUrl = index)
        val (main, forms) = finnish.entries.let { it.first() to it.last() }
        assertThat(main.matches(whole)).isTrue()
        assertThat(main.matches(formsOnly)).isFalse()
        assertThat(forms.matches(formsOnly)).isTrue()
        assertThat(forms.matches(whole)).isFalse()
        assertThat(forms.isCutFrom(whole)).isTrue()
        assertThat(forms.isCutFrom(formsOnly)).isFalse()
        assertThat(forms.isCutFrom(main)).isTrue()
        assertThat(main.isCutFrom(forms)).isFalse()

        assertThat(missingCategories(Language.FINNISH, listOf(installed("fi-ru", source = "fi")), finnish))
            .containsExactly(CatalogCategory.FORMS)
        assertThat(missingCategories(Language.FINNISH, listOf(installed("fi-ru", source = "fi"), formsOnly), finnish)).isEmpty()
        assertThat(missingCategories(Language.FINNISH, listOf(whole), finnish)).isEmpty()
        val categories = setupCategories(finnish, Language.FINNISH, listOf("ru"), installed = listOf(whole))
        assertThat(categories.single { it.category == CatalogCategory.FORMS }.items.single().installed).isTrue()
    }

    @Test
    fun `a language lacks the mandatory categories none of its enabled dictionaries covers`() {
        val forms = entry("en-forms", null, CatalogCategory.FORMS)
        val withForms = Catalog(catalog.entries + forms, mapOf("en" to setOf(CatalogCategory.MAIN, CatalogCategory.FORMS)))
        assertThat(missingCategories(Language.ENGLISH, emptyList(), withForms))
            .containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS).inOrder()
        assertThat(missingCategories(Language.ENGLISH, listOf(installed("en-ru"), installed("en-forms", terms = 0)), withForms)).isEmpty()
        assertThat(missingCategories(Language.ENGLISH, listOf(installed("ja-en", source = "ja")), catalog)).containsExactly(CatalogCategory.MAIN)
    }

    @Test
    fun `an enabled term dictionary of the language or of no stated language covers the main category`() {
        assertThat(missingCategories(Language.ENGLISH, listOf(installed("any", source = null)), catalog)).isEmpty()
        assertThat(missingCategories(Language.ENGLISH, listOf(installed("en-ru").copy(enabled = false)), catalog))
            .containsExactly(CatalogCategory.MAIN)
        assertThat(missingCategories(Language.ENGLISH, listOf(installed("en-freq", terms = 0)), catalog))
            .containsExactly(CatalogCategory.MAIN)
    }

    @Test
    fun `the install progress counts the unfinished imports and names the running one`() {
        fun task(name: String, state: ImportTask.State, percent: Int? = null) =
            ImportTask(UUID.randomUUID(), name, state, percent, emptyList(), null)
        assertThat(installProgress(listOf(task("done", ImportTask.State.SUCCEEDED)))).isNull()
        val progress = installProgress(
            listOf(task("done", ImportTask.State.SUCCEEDED), task("en-ru", ImportTask.State.DOWNLOADING, 45), task("en-en", ImportTask.State.QUEUED)),
        )
        assertThat(progress).isEqualTo(InstallProgress(2, "en-ru", 45))
    }

    @Test
    fun `deleting a language's files cancels downloads of its catalog entries and updates of its dictionaries`() {
        fun task(name: String, state: ImportTask.State = ImportTask.State.DOWNLOADING) =
            ImportTask(UUID.randomUUID(), name, state, null, emptyList(), null)
        val tasks = listOf(
            task("en-ru"),
            task("Old English Title"),
            task("ja-en"),
            task("Own Import"),
            task("en-en", ImportTask.State.SUCCEEDED),
        )
        val installed = listOf(installed("Old English Title"))

        assertThat(downloadsOf(setOf("en"), tasks, catalog, installed, Language.JAPANESE).map { it.name })
            .containsExactly("en-ru", "Old English Title")
    }

    @Test
    fun `bundled imports belong to their language regardless of their displayed name`() {
        fun task(name: String, state: ImportTask.State = ImportTask.State.QUEUED, source: String? = "bundled") =
            ImportTask(UUID.randomUUID(), name, state, null, emptyList(), null, source = source)
        val queued = task("")
        val importing = task("jmdict-english", ImportTask.State.IMPORTING)
        val finished = task("kanjidic", ImportTask.State.SUCCEEDED)
        val ownFile = task("own.zip", source = "file")
        val tasks = listOf(queued, importing, finished, ownFile)

        assertThat(downloadsOf(setOf("ja"), tasks, catalog, emptyList(), Language.JAPANESE))
            .containsExactly(queued, importing)
        assertThat(downloadsOf(setOf("en"), tasks, catalog, emptyList(), Language.JAPANESE)).isEmpty()
    }
}
