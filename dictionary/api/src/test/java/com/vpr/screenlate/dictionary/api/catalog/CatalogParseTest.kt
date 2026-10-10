package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class CatalogParseTest {

    @Test
    fun `a format 1 catalog gives sizes in megabytes and categories by kind`() {
        val catalog = DictionaryCatalog.parse(
            """{"format": 1, "dictionaries": [
                {"id": "a", "title": "A", "installedTitle": "A", "kind": "TERM", "sourceLanguage": "ja",
                 "targetLanguage": "en", "downloadUrl": "https://example.org/a.zip", "sizeMb": 3,
                 "description": {"en": "Text"}},
                {"id": "p", "title": "P", "installedTitle": "P", "kind": "PITCH", "sourceLanguage": "ja",
                 "downloadUrl": "https://example.org/p.zip"}
            ]}""",
        )!!
        val (term, pitch) = catalog.entries
        assertThat(term.downloadBytes).isEqualTo(3L * 1024 * 1024)
        assertThat(term.category).isEqualTo(CatalogCategory.MAIN)
        assertThat(term.displayTitle(Locale.ENGLISH)).isEqualTo("A")
        assertThat(term.description(Locale.FRENCH)).isEqualTo("Text")
        assertThat(pitch.category).isEqualTo(CatalogCategory.PRONUNCIATION)
        assertThat(catalog.mandatory("ja")).containsExactly(CatalogCategory.MAIN)
    }

    @Test
    fun `templates give texts an entry can override, and unknown entries leave out only themselves`() {
        val catalog = DictionaryCatalog.parse(
            """{"format": 2,
                "mandatory": {"it": ["MAIN", "FORMS", "SOMETHING_NEW"]},
                "templates": {"t": {"title": {"en": "T ({src}–{tgt})", "zh-Hant": "繁 ({src}–{tgt})"},
                                    "description": {"en": "{source} → {target}", "de": "{source} → {target}"}}},
                "dictionaries": [
                {"id": "g", "title": "G", "template": "t", "kind": "TERM", "category": "GLOSSARY",
                 "sourceLanguage": "en", "targetLanguage": "ru", "downloadUrl": "https://example.org/g.zip",
                 "downloadSize": 1000, "installedSize": 5000, "description": {"de": "Eigener Text"}},
                {"id": "m", "title": "M", "kind": "SOMETHING_NEW", "sourceLanguage": "en",
                 "downloadUrl": "https://example.org/m.bin"},
                {"id": "x", "title": "X", "kind": "TERM", "category": "SOMETHING_NEW", "sourceLanguage": "en",
                 "downloadUrl": "https://example.org/x.zip"}
            ]}""",
        )!!
        val entry = catalog.entries.single()
        assertThat(entry.category).isEqualTo(CatalogCategory.GLOSSARY)
        assertThat(entry.displayTitle(Locale.ENGLISH)).isEqualTo("T (en–ru)")
        assertThat(entry.displayTitle(Locale.TRADITIONAL_CHINESE)).isEqualTo("繁 (en–ru)")
        assertThat(entry.displayTitle(Locale.forLanguageTag("ko"))).isEqualTo("G")
        assertThat(entry.description(Locale.ENGLISH)).isEqualTo("English → Russian")
        assertThat(entry.description(Locale.GERMAN)).isEqualTo("Eigener Text")
        assertThat(entry.downloadBytes).isEqualTo(1000L)
        assertThat(entry.installedBytes).isEqualTo(5000L)
        assertThat(catalog.mandatory("it")).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS)
    }

    @Test
    fun `a format this version does not know gives no catalog`() {
        assertThat(DictionaryCatalog.parse("""{"format": 3, "dictionaries": []}""")).isNull()
        assertThat(DictionaryCatalog.parse("not json")).isNull()
    }

    @Test
    fun `Chinese texts follow the script, or the region when the locale names none`() {
        assertThat(localeKey(Locale.forLanguageTag("zh-Hant-CN"))).isEqualTo("zh-Hant")
        assertThat(localeKey(Locale.forLanguageTag("zh-TW"))).isEqualTo("zh-Hant")
        assertThat(localeKey(Locale.forLanguageTag("zh-Hans-HK"))).isEqualTo("zh")
        assertThat(localeKey(Locale.forLanguageTag("zh-CN"))).isEqualTo("zh")
        assertThat(localeKey(Locale.forLanguageTag("pt-BR"))).isEqualTo("pt")
    }
}
