package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
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
}
