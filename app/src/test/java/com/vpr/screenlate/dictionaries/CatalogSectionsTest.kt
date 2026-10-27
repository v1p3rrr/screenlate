package com.vpr.screenlate.dictionaries

import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSectionsTest {
    private fun item(title: String, task: ImportTask? = null, outdated: DictionaryEntity? = null) = CatalogItem(
        CatalogEntry(id = title, title = title, installedTitle = title, kind = DictionaryKind.TERM, sourceLanguage = "ja", downloadUrl = "https://example.org/$title.zip"),
        installed = false,
        task = task,
        outdated = outdated,
    )

    private val old = DictionaryEntity(
        title = "Old", revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ja", targetLanguage = "de",
        frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0,
        pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
        author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
    )

    @Test
    fun `only the translations into the interface language start open`() {
        val russian = CatalogSection(DictionaryKind.TERM, "ru", listOf(item("A")))
        val english = CatalogSection(DictionaryKind.TERM, "en", listOf(item("B")))
        val frequency = CatalogSection(DictionaryKind.FREQUENCY, null, listOf(item("C")))

        assertTrue(russian.openByDefault("ru"))
        assertFalse(english.openByDefault("ru"))
        assertFalse(frequency.openByDefault("ru"))
        // Another kind never counts as a translation, whatever its language.
        assertFalse(CatalogSection(DictionaryKind.KANJI, null, listOf(item("D"))).openByDefault(""))
    }

    @Test
    fun `a section with a download under way or an update waiting starts open`() {
        val downloading = ImportTask(UUID.randomUUID(), "B", ImportTask.State.DOWNLOADING, 10, titles = emptyList(), error = null)

        assertTrue(CatalogSection(DictionaryKind.TERM, "en", listOf(item("A"), item("B", task = downloading))).openByDefault("ru"))
        assertTrue(CatalogSection(DictionaryKind.PITCH, null, listOf(item("C", outdated = old))).openByDefault("ru"))
    }

    @Test
    fun `English, the language itself and the interface language stay on top, the others go under one heading`() {
        val sections = listOf("ru", "en", "ja", "es", "de", null).map { CatalogSection(DictionaryKind.TERM, it, listOf(item("$it"))) } +
            listOf(DictionaryKind.FREQUENCY, DictionaryKind.KANJI).map { CatalogSection(it, null, listOf(item("$it"))) }

        val layout = CatalogGroup("ja", sections).layout("ru")

        assertEquals(listOf("ru", "en", "ja", null), layout.main.map { it.targetLanguage })
        assertEquals(listOf("es", "de"), layout.otherTargets.map { it.targetLanguage })
        assertEquals(listOf(DictionaryKind.FREQUENCY, DictionaryKind.KANJI), layout.kinds.map { it.kind })
    }

    @Test
    fun `with an English interface the top has two languages`() {
        val sections = listOf("en", "zh", "ru").map { CatalogSection(DictionaryKind.TERM, it, listOf(item("$it"))) }

        val layout = CatalogGroup("zh", sections).layout("en")

        assertEquals(listOf("en", "zh"), layout.main.map { it.targetLanguage })
        assertEquals(listOf("ru"), layout.otherTargets.map { it.targetLanguage })
    }

    @Test
    fun `the other languages open by themselves only for a download or an update in one of them`() {
        val downloading = ImportTask(UUID.randomUUID(), "B", ImportTask.State.DOWNLOADING, 10, titles = emptyList(), error = null)
        val quiet = CatalogGroup("ja", listOf(CatalogSection(DictionaryKind.TERM, "es", listOf(item("A")))))
        val busy = CatalogGroup("ja", listOf(CatalogSection(DictionaryKind.TERM, "es", listOf(item("A"), item("B", task = downloading)))))

        assertFalse(quiet.layout("ru").otherTargetsOpenByDefault("ru"))
        assertTrue(busy.layout("ru").otherTargetsOpenByDefault("ru"))
        assertNotEquals(quiet.otherTargetsKey(), CatalogGroup("zh", emptyList()).otherTargetsKey())
    }

    @Test
    fun `each section of each source language has its own key`() {
        val toEnglish = CatalogSection(DictionaryKind.TERM, "en", emptyList())
        val frequency = CatalogSection(DictionaryKind.FREQUENCY, null, emptyList())

        assertNotEquals(toEnglish.key("ja"), toEnglish.key("zh"))
        assertNotEquals(toEnglish.key("ja"), CatalogSection(DictionaryKind.TERM, "ru", emptyList()).key("ja"))
        assertNotEquals(frequency.key("ja"), CatalogSection(DictionaryKind.PITCH, null, emptyList()).key("ja"))
    }
}
