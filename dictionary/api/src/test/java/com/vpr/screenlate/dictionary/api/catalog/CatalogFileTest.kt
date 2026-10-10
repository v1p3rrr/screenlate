package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.io.File
import java.util.Locale
import org.junit.Test

/**
 * The bundled catalog, which the app also fetches from the repository: a broken file hides every entry. The format 1
 * copy is what older versions fetch.
 */
class CatalogFileTest {

    private val catalog = DictionaryCatalog.parse(File("src/main/assets/catalog/dictionaries-v2.json").readText())
    private val legacy = DictionaryCatalog.parse(File("src/main/assets/catalog/dictionaries.json").readText())

    /** The interface locales, as keys of catalog texts. */
    private val locales = listOf("en", "ru", "de", "es", "fr", "it", "ja", "ko", "pl", "pt", "tr", "vi", "zh", "zh-Hant")

    @Test
    fun `every entry is complete and unique`() {
        assertThat(catalog).isNotNull()
        val all = catalog!!.entries
        assertThat(all.map { it.id }).containsNoDuplicates()
        assertThat(all.map { it.title }).containsNoDuplicates()
        for (entry in all) {
            assertThat(entry.downloadUrl).startsWith("https://")
            assertThat(entry.installedTitle != null || entry.oldTitles.isNotEmpty()).isTrue()
            assertThat(entry.sourceLanguage).isNotEmpty()
            assertThat(entry.description.keys).containsAtLeastElementsIn(locales)
            assertThat(entry.license).isNotEmpty()
            assertThat(entry.downloadSize).isGreaterThan(0L)
            if (entry.kind == DictionaryKind.FREQUENCY || entry.kind == DictionaryKind.PITCH) {
                assertThat(entry.targetLanguage).isNull()
            } else {
                assertThat(entry.targetLanguage).isNotEmpty()
            }
        }
    }

    @Test
    fun `templates fill titles and descriptions in every locale`() {
        val entry = catalog!!.entries.first { it.id == "wiktionary-en-ru" }
        assertThat(entry.titles.keys).containsAtLeastElementsIn(locales)
        assertThat(entry.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (en–ru)")
        assertThat(entry.description(Locale.ENGLISH)).startsWith("Wiktionary, English → Russian:")
        assertThat(entry.description(Locale.forLanguageTag("ru"))).startsWith("Викисловарь, английский → русский:")
        for (locale in locales) {
            assertThat(entry.description(Locale.forLanguageTag(locale))).doesNotContain("{")
            assertThat(entry.displayTitle(Locale.forLanguageTag(locale))).doesNotContain("{")
        }
        val transcription = catalog.entries.first { it.id == "wiktionary-en-transcription" }
        assertThat(transcription.category).isEqualTo(CatalogCategory.PRONUNCIATION)
        assertThat(transcription.displayTitle(Locale.ENGLISH)).isEqualTo("Transcription (Wiktionary)")
    }

    @Test
    fun `each language pair has one recommended main dictionary`() {
        val main = catalog!!.entries.filter { it.category == CatalogCategory.MAIN && it.recommended }
        val pairs = main.map { it.sourceLanguage to it.targetLanguage }
        assertThat(pairs).containsNoDuplicates()
        assertThat(main.first { it.sourceLanguage == "ja" && it.targetLanguage == "en" }.id).isEqualTo("jitendex")
        assertThat(pairs).contains("en" to "en")
        assertThat(catalog.mandatory("en")).containsExactly(CatalogCategory.MAIN)
    }

    @Test
    fun `installed titles tell entries apart`() {
        val all = catalog!!.entries
        for (entry in all) {
            val prefix = entry.installedTitle ?: continue
            val others = all.filter { it !== entry && it.kind == entry.kind && it.installedTitle?.startsWith(prefix) == true }
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
        for (entries in listOf(catalog!!.entries, legacy!!.entries)) {
            fun matching(dictionary: DictionaryEntity) = entries.filter { it.matches(dictionary) }.map { it.id }

            // "Jiten" is a prefix of Jitendex's titles, old and new; the old one is listed as an old build of Jitendex.
            assertThat(matching(installed("Jitendex.org [2026-09-01]", DictionaryKind.TERM))).containsExactly("jitendex")
            assertThat(matching(installed("Jitendex [2023-12-12]", DictionaryKind.TERM))).containsExactly("jitendex")
            // Old builds without an update address: the whole title, not a prefix of the other JMdict entries'.
            assertThat(matching(installed("JMdict", DictionaryKind.TERM))).containsExactly("jmdict-english")
            assertThat(matching(installed("JMdict (English)", DictionaryKind.TERM))).containsExactly("jmdict-english")
            assertThat(matching(installed("JMdict (Russian) [2026-10-01]", DictionaryKind.TERM))).containsExactly("jmdict-russian")
            assertThat(matching(installed("KANJIDIC (English)", DictionaryKind.KANJI))).containsExactly("kanjidic-english")
            assertThat(matching(installed("Jiten", DictionaryKind.FREQUENCY, "https://api.jiten.moe/api/frequency-list/index")))
                .containsExactly("jiten-global")
            // Wiktionary was renamed from kty-* to wty-* and moved; copies from before and after both belong to the entry.
            val wty = "https://huggingface.co/datasets/daxida/wty-release/resolve/main/latest/index/wty-ja-ru-index.json?download=true"
            assertThat(matching(installed("wty-ja-ru", DictionaryKind.TERM, wty))).containsExactly("wiktionary-ja-ru")
            val kty = "https://pub-c3d38cca4dc2403b88934c56748f5144.r2.dev/releases/latest/kty-ja-ru-index.json"
            assertThat(matching(installed("kty-ja-ru", DictionaryKind.TERM, kty))).containsExactly("wiktionary-ja-ru")
        }
        // The main dictionary's name is a prefix of its glossary's; only the exact title matches.
        val gloss = "https://huggingface.co/datasets/daxida/wty-release/resolve/main/latest/index/wty-en-ru-gloss-index.json?download=true"
        assertThat(catalog.entries.filter { it.matches(installed("wty-en-ru-gloss", DictionaryKind.TERM, gloss)) }.map { it.id })
            .containsExactly("wiktionary-en-ru-glossary")
        assertThat(catalog.entries.filter { it.matches(installed("wty-en-ru-gloss", DictionaryKind.TERM)) }.map { it.id })
            .containsExactly("wiktionary-en-ru-glossary")
    }

    @Test
    fun `the format 1 copy holds the Japanese entries older versions can read`() {
        val entries = legacy!!.entries
        assertThat(entries.map { it.sourceLanguage }.distinct()).containsExactly("ja")
        assertThat(entries.all { it.installedTitle != null && it.sizeMb > 0 }).isTrue()
        val v2Japanese = catalog!!.entries.filter { it.sourceLanguage == "ja" && it.category != CatalogCategory.GLOSSARY }
            .filter { it.template != "wiktionary-transcription" }
        assertThat(entries.map { it.id }).containsExactlyElementsIn(v2Japanese.map { it.id })
    }
}
