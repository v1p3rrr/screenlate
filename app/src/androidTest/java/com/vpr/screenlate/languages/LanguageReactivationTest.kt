package com.vpr.screenlate.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageReactivationTest {
    @Test
    fun aKeptDisabledMainDictionaryIsEnabledWithoutDownloadingItAgain() = runBlocking {
        LanguageTestFixture().use { fixture ->
            val catalog = fixture.catalog.localCatalog()
            val entry = catalog.entries.first { it.sourceLanguage == "en" && it.targetLanguage == "en" && it.category == CatalogCategory.MAIN }
            val main = fixture.dictionary(entry.installedTitle ?: entry.title, indexUrl = entry.indexUrl)
            val other = fixture.dictionary("Own English Dictionary")
            val japanese = fixture.dictionary("Japanese Dictionary", "ja")
            fixture.profiles.turnOn(Language.ENGLISH)
            fixture.switch.turnOff(Language.ENGLISH, deleteFiles = false)
            listOf(main, other, japanese).forEach { fixture.repository.setEnabled(it.id, false) }
            assertThat(fixture.switch.needsDownloads(Language.ENGLISH)).isTrue()
            val categories = setupCategories(catalog, "en", listOf("en"), fixture.repository.getAll())
            val selected = setupInstalled(categories, emptyMap(), fixture.repository.getAll())
            val before = fixture.imports.tasks.first()

            fixture.switch.turnOn(Language.ENGLISH, enableDictionaries = selected + japanese.id)

            assertThat(fixture.profiles.current().active).isEqualTo(Language.ENGLISH)
            assertThat(fixture.switch.needsDownloads(Language.ENGLISH)).isFalse()
            assertThat(fixture.repository.hasTermDictionaries(Language.ENGLISH)).isTrue()
            val installed = fixture.repository.getAll().associateBy { it.id }
            assertThat(installed.getValue(main.id).enabled).isTrue()
            assertThat(installed.getValue(other.id).enabled).isFalse()
            assertThat(installed.getValue(japanese.id).enabled).isFalse()
            assertThat(fixture.imports.tasks.first()).isEqualTo(before)
        }
    }
}
