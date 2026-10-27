package com.vpr.screenlate.dictionaries

import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.util.UUID
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
    fun `each section of each source language has its own key`() {
        val toEnglish = CatalogSection(DictionaryKind.TERM, "en", emptyList())
        val frequency = CatalogSection(DictionaryKind.FREQUENCY, null, emptyList())

        assertNotEquals(toEnglish.key("ja"), toEnglish.key("zh"))
        assertNotEquals(toEnglish.key("ja"), CatalogSection(DictionaryKind.TERM, "ru", emptyList()).key("ja"))
        assertNotEquals(frequency.key("ja"), CatalogSection(DictionaryKind.PITCH, null, emptyList()).key("ja"))
    }
}
