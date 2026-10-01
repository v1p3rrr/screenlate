package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.io.File
import org.junit.Test

/** The bundled catalog, which the app also fetches from the repository: a broken file hides every entry. */
class CatalogFileTest {

    private val entries = DictionaryCatalog.parse(File("src/main/assets/catalog/dictionaries.json").readText())

    @Test
    fun `every entry is complete and unique`() {
        assertThat(entries).isNotNull()
        val all = entries!!
        assertThat(all.map { it.id }).containsNoDuplicates()
        for (entry in all) {
            assertThat(entry.downloadUrl).startsWith("https://")
            assertThat(entry.installedTitle).isNotEmpty()
            assertThat(entry.sourceLanguage).isNotEmpty()
            assertThat(entry.description.keys).containsAtLeast("en", "ru")
            assertThat(entry.license).isNotEmpty()
            if (entry.kind == DictionaryKind.FREQUENCY || entry.kind == DictionaryKind.PITCH) {
                assertThat(entry.targetLanguage).isNull()
            } else {
                assertThat(entry.targetLanguage).isNotEmpty()
            }
        }
    }

    @Test
    fun `installed titles tell entries apart`() {
        val all = entries!!
        for (entry in all) {
            val others = all.filter { it !== entry && it.kind == entry.kind && it.installedTitle.startsWith(entry.installedTitle) }
            // A prefix shared with another entry would mark both as installed.
            assertThat(others.map { it.id }).isEmpty()
        }
    }

    @Test
    fun `an installed dictionary matches entries of its own kind only`() {
        fun installed(title: String, kind: DictionaryKind, indexUrl: String? = null) = DictionaryEntity(
            title = title, revision = "1", kind = kind, sourceLanguage = "ja", targetLanguage = null, frequencyMode = null,
            enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0, pitchCount = 0, kanjiCount = 0,
            mediaCount = 0, isUpdatable = false, indexUrl = indexUrl, downloadUrl = null, author = null, url = null,
            description = null, attribution = null, bundled = false, importedAt = 0,
        )
        fun matching(dictionary: DictionaryEntity) = entries!!.filter { it.matches(dictionary) }.map { it.id }

        // "Jiten" is a prefix of Jitendex's titles, old and new; the old one is no catalog entry's.
        assertThat(matching(installed("Jitendex.org [2026-09-01]", DictionaryKind.TERM))).containsExactly("jitendex")
        assertThat(matching(installed("Jitendex [2023-12-12]", DictionaryKind.TERM))).isEmpty()
        assertThat(matching(installed("Jiten", DictionaryKind.FREQUENCY, "https://api.jiten.moe/api/frequency-list/index")))
            .containsExactly("jiten-global")
        // Wiktionary was renamed from kty-* to wty-* and moved; copies from before and after both belong to the entry.
        val wty = "https://huggingface.co/datasets/daxida/wty-release/resolve/main/latest/index/wty-ja-ru-index.json?download=true"
        assertThat(matching(installed("wty-ja-ru", DictionaryKind.TERM, wty))).containsExactly("wiktionary-ja-ru")
        val kty = "https://pub-c3d38cca4dc2403b88934c56748f5144.r2.dev/releases/latest/kty-ja-ru-index.json"
        assertThat(matching(installed("kty-ja-ru", DictionaryKind.TERM, kty))).containsExactly("wiktionary-ja-ru")
    }
}
