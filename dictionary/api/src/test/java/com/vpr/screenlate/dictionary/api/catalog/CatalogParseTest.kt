package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
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
    fun `a model entry needs its id, engine, version and checksum, a dictionary its kind`() {
        val catalog = DictionaryCatalog.parse(
            """{"format": 2, "dictionaries": [
                {"id": "ru-ocr", "title": "Cyrillic recognition", "category": "OCR_MODEL", "sourceLanguage": "ru",
                 "model": "eslav-rec", "engine": "paddle", "version": "5", "sha256": "ab", "recommended": true,
                 "requires": ["detector"], "downloadUrl": "https://example.org/eslav.zip", "downloadSize": 8000},
                {"id": "ru-tess", "title": "Tesseract", "category": "OCR_MODEL", "sourceLanguage": "ru",
                 "model": "tesseract-fast-rus", "engine": "tesseract", "version": "1", "sha256": "cd",
                 "file": "tessdata/rus.traineddata", "downloadUrl": "https://example.org/rus.traineddata"},
                {"id": "uk-ocr", "title": "No checksum", "category": "OCR_MODEL", "sourceLanguage": "uk",
                 "model": "eslav-rec", "engine": "paddle", "version": "5", "downloadUrl": "https://example.org/eslav.zip"},
                {"id": "d", "title": "No kind", "sourceLanguage": "ru", "downloadUrl": "https://example.org/d.zip"}
            ]}""",
        )!!
        val (model, file) = catalog.entries
        assertThat(model.isModel).isTrue()
        assertThat(model.kind).isNull()
        assertThat(model.category).isEqualTo(CatalogCategory.OCR_MODEL)
        assertThat(model.installedBytes).isEqualTo(8000L)
        assertThat(model.requires).containsExactly("detector")
        assertThat(model.file).isNull()
        assertThat(file.file).isEqualTo("tessdata/rus.traineddata")
        assertThat(file.requires).isEmpty()
    }

    @Test
    fun `word forms need no checksum, another build does`() {
        val catalog = DictionaryCatalog.parse(
            """{"format": 2, "mandatory": {"fi": ["MAIN", "FORMS"]}, "dictionaries": [
                {"id": "fi-forms", "title": "Forms", "kind": "TERM", "category": "FORMS", "sourceLanguage": "fi",
                 "targetLanguage": "en", "build": "forms", "downloadUrl": "https://example.org/fi-en.zip"},
                {"id": "fi-freq", "title": "Frequency", "kind": "FREQUENCY", "sourceLanguage": "fi", "build": "wordfreq",
                 "version": "3", "downloadUrl": "https://example.org/fi.msgpack.gz"}
            ]}""",
        )!!
        assertThat(catalog.entries.map { it.id }).containsExactly("fi-forms")
        assertThat(catalog.entries.single().category).isEqualTo(CatalogCategory.FORMS)
        assertThat(catalog.mandatory("fi")).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS).inOrder()
    }

    @Test
    fun `an entry without its own size after import takes the measured one, in proportion to its archive now`() {
        val text = """{"format": 2, "dictionaries": [
            {"id": "a", "title": "A", "kind": "TERM", "sourceLanguage": "en", "downloadUrl": "https://example.org/a.zip",
             "downloadSize": 2000},
            {"id": "b", "title": "B", "kind": "TERM", "sourceLanguage": "en", "downloadUrl": "https://example.org/b.zip",
             "downloadSize": 1000, "installedSize": 1500},
            {"id": "c", "title": "C", "kind": "TERM", "sourceLanguage": "en", "downloadUrl": "https://example.org/c.zip",
             "downloadSize": 1000}
        ]}"""
        val measured = DictionaryCatalog.parseSizes("""{"sizes": {"a": {"download": 1000, "installed": 4500},
            "b": {"download": 1000, "installed": 9000}}}""")
        val (a, b, c) = DictionaryCatalog.parse(text, measured)!!.entries
        assertThat(a.installedBytes).isEqualTo(9000L)
        assertThat(b.installedBytes).isEqualTo(1500L)
        assertThat(c.installedBytes).isEqualTo(2200L)
        assertThat(DictionaryCatalog.parse(text)!!.entries.first().installedBytes).isEqualTo(4400L)
    }

    @Test
    fun `a built frequency dictionary takes about as much from a long source as from a short one`() {
        val mb = 1024L * 1024L
        val wordfreq = CatalogEntry(
            id = "fi-freq", title = "Frequency", kind = DictionaryKind.FREQUENCY, sourceLanguage = "fi",
            downloadUrl = "https://example.org/fi.msgpack.gz", downloadSize = 100_000, build = BUILD_WORDFREQ,
        )
        assertThat(wordfreq.installedBytes).isEqualTo(2_500_000L)
        assertThat(wordfreq.copy(downloadSize = 4 * mb).installedBytes).isEqualTo(12 * mb)
        assertThat(wordfreq.installedBytesAtMost).isEqualTo(16 * mb)
        val corpus = wordfreq.copy(downloadSize = mb, build = BUILD_CORPUS_COUNTS)
        assertThat(corpus.installedBytes).isEqualTo(5 * mb)
        assertThat(corpus.copy(downloadSize = 20 * mb).installedBytes).isEqualTo(12 * mb)
        assertThat(corpus.copy(downloadSize = 20 * mb).installedBytesAtMost).isEqualTo(16 * mb)
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
        assertThat(entry.installedBytesAtMost).isEqualTo(6250L)
        val estimated = entry.copy(installedSize = null)
        assertThat(estimated.installedBytes).isEqualTo(2200L)
        assertThat(estimated.installedBytesAtMost).isEqualTo(5000L)
        assertThat(estimated.copy(kind = DictionaryKind.PITCH).installedBytes).isEqualTo(10_500L)
        assertThat(estimated.copy(kind = DictionaryKind.PITCH).installedBytesAtMost).isEqualTo(15_000L)
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
