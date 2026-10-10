package com.vpr.screenlate.languages

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionaries.catalogTargetOrder
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
    ) = CatalogEntry(
        id = id, title = id, installedTitle = id, kind = kind, declaredCategory = category, sourceLanguage = source,
        targetLanguage = target, recommended = recommended, downloadUrl = "https://example.org/$id.zip", downloadSize = size,
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
        val categories = setupCategories(catalog, "en", listOf("ru", "en"), installed = emptyList())
        assertThat(categories.map { it.category })
            .containsExactly(CatalogCategory.MAIN, CatalogCategory.PRONUNCIATION, CatalogCategory.GLOSSARY).inOrder()
        val main = categories.first()
        assertThat(main.items.map { it.entry.id }).containsExactly("en-ru", "en-en").inOrder()
        assertThat(main.mandatory).isTrue()
        assertThat(main.fixed).isFalse()
    }

    @Test
    fun `recommended dictionaries are ticked, the user's ticks win, an empty mandatory category is missing`() {
        val categories = setupCategories(catalog, "en", listOf("ru", "en"), installed = emptyList())
        assertThat(setupDownloads(categories, emptyMap()).map { it.id }).containsExactly("en-ru", "en-en", "en-ipa").inOrder()

        val choices = mapOf("en-ru" to false, "en-en" to false, "en-ru-glossary" to true)
        assertThat(missingChoices(categories, choices)).containsExactly(CatalogCategory.MAIN)
        assertThat(setupDownloads(categories, choices).map { it.id }).containsExactly("en-ipa", "en-ru-glossary").inOrder()
    }

    @Test
    fun `a single mandatory item is fixed and installed items are not downloaded again`() {
        val categories = setupCategories(catalog, "en", listOf("de"), installed = listOf(installed("en-ipa", kind = DictionaryKind.PITCH)))
        val main = categories.first { it.category == CatalogCategory.MAIN }
        assertThat(main.fixed).isTrue()
        assertThat(main.ticked(main.items.single(), mapOf("en-de" to false))).isTrue()
        assertThat(setupDownloads(categories, mapOf("en-de" to false)).map { it.id }).containsExactly("en-de")
    }

    @Test
    fun `a mandatory category without dictionaries in the chosen languages is still shown, and missing`() {
        val categories = setupCategories(catalog, "en", listOf("fr"), installed = emptyList())
        assertThat(categories.first().category).isEqualTo(CatalogCategory.MAIN)
        assertThat(categories.first().items).isEmpty()
        assertThat(missingChoices(categories, emptyMap())).containsExactly(CatalogCategory.MAIN)
    }

    @Test
    fun `totals add the archives, the space after import, and the largest archive on the way`() {
        val totals = setupTotals(listOf(entry("a", "ru", size = 10), entry("b", "ru", size = 30)))
        assertThat(totals.downloadBytes).isEqualTo(40)
        assertThat(totals.installedBytes).isEqualTo(120)
        assertThat(totals.neededBytes).isEqualTo(150)
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
    fun `the install progress counts the unfinished imports and names the running one`() {
        fun task(name: String, state: ImportTask.State, percent: Int? = null) =
            ImportTask(UUID.randomUUID(), name, state, percent, emptyList(), null)
        assertThat(installProgress(listOf(task("done", ImportTask.State.SUCCEEDED)))).isNull()
        val progress = installProgress(
            listOf(task("done", ImportTask.State.SUCCEEDED), task("en-ru", ImportTask.State.DOWNLOADING, 45), task("en-en", ImportTask.State.QUEUED)),
        )
        assertThat(progress).isEqualTo(InstallProgress(2, "en-ru", 45))
    }
}
