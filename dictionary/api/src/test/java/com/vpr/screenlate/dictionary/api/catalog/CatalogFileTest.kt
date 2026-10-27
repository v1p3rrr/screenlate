package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.imports.CorpusCountsDictionary
import com.vpr.screenlate.dictionary.api.imports.WordfreqDictionary
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
            if (entry.downloadUrl.startsWith(ASSET_URL_PREFIX)) {
                val asset = File("src/main/assets", entry.downloadUrl.removePrefix(ASSET_URL_PREFIX))
                assertThat(entry.downloadSize).isEqualTo(asset.length())
            } else {
                assertThat(entry.downloadUrl).startsWith("https://")
            }
            assertThat(entry.sourceLanguage).isNotEmpty()
            assertThat(entry.description.keys).containsAtLeastElementsIn(locales)
            assertThat(entry.license).isNotEmpty()
            assertThat(entry.downloadSize).isGreaterThan(0L)
            // A model is found in the model store by its id, not among installed dictionaries by title.
            if (entry.isModel) {
                assertThat(entry.targetLanguage).isNull()
                continue
            }
            assertThat(entry.installedTitle != null || entry.oldTitles.isNotEmpty()).isTrue()
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
    fun `every source language but Japanese, Belarusian, Georgian, Armenian and Thai has a frequency dictionary built from wordfreq`() {
        val built = catalog!!.entries.filter { it.build == BUILD_WORDFREQ }
        assertThat(built.map { it.sourceLanguage })
            .containsExactly("en", "zh", "ko", "es", "fr", "de", "it", "pt", "ru", "pl", "tr", "vi", "lv", "nl", "cs", "id", "ms", "tl", "sh", "uk", "el", "hi", "bn", "ar", "fa", "he", "sv", "no", "da", "ro", "lt", "sk", "sl")
        for (entry in built) {
            assertThat(entry.build).isEqualTo(BUILD_WORDFREQ)
            assertThat(entry.category).isEqualTo(CatalogCategory.FREQUENCY)
            assertThat(entry.recommended).isTrue()
            assertThat(entry.sha256).matches("[0-9a-f]{64}")
            // wordfreq names Tagalog fil and Norwegian by Bokmål's nb.
            val code = when (entry.sourceLanguage) {
                "tl" -> "fil"
                "no" -> "nb"
                else -> entry.sourceLanguage
            }
            assertThat(entry.downloadUrl).matches(
                "https://raw\\.githubusercontent\\.com/rspeer/wordfreq/[0-9a-f]{40}/wordfreq/data/(large|small)_$code\\.msgpack\\.gz",
            )
            assertThat(entry.oldTitles).containsExactly(WordfreqDictionary.title(entry.sourceLanguage))
            assertThat(entry.titles.keys).containsAtLeastElementsIn(locales)
        }
        val english = built.first { it.sourceLanguage == "en" }
        assertThat(english.displayTitle(Locale.ENGLISH)).isEqualTo("Word frequency (wordfreq)")
        assertThat(english.description(Locale.ENGLISH)).startsWith("wordfreq, English:")
        // Older versions cannot build it, so the format 1 copy leaves it out.
        assertThat(legacy!!.entries.none { it.id == english.id }).isTrue()
    }

    @Test
    fun `thai, kazakh, kyrgyz and latin have frequency dictionaries built from corpus counts, of PyThaiNLP and Leipzig`() {
        val built = catalog!!.entries.filter { it.build == BUILD_CORPUS_COUNTS }
        assertThat(built.map { it.sourceLanguage }).containsExactly("th", "kk", "ky", "la").inOrder()
        val latin = built.single { it.sourceLanguage == "la" }
        assertThat(latin.recommended).isTrue()
        assertThat(latin.sha256).matches("[0-9a-f]{64}")
        assertThat(latin.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/lat_wikipedia_2021_100K.tar.gz")
        assertThat(latin.oldTitles).containsExactly(CorpusCountsDictionary.title("la"))
        assertThat(latin.displayTitle(Locale.ENGLISH)).isEqualTo("Word frequency (Leipzig Corpora Collection)")
        assertThat(latin.description(Locale.ENGLISH)).startsWith("Leipzig Corpora Collection, Latin: how common words are in Wikipedia")
        val kyrgyz = built.single { it.sourceLanguage == "ky" }
        assertThat(kyrgyz.recommended).isTrue()
        assertThat(kyrgyz.sha256).isEqualTo("145fd1c78a6d22291a5356aea1dbe6d71cc9061704eed018b1272c608303d9bb")
        assertThat(kyrgyz.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/kir_newscrawl_2016_100K.tar.gz")
        assertThat(kyrgyz.oldTitles).containsExactly(CorpusCountsDictionary.title("ky"))
        assertThat(kyrgyz.description(Locale.ENGLISH)).startsWith("Leipzig Corpora Collection, Kyrgyz:")
        val kazakh = built.single { it.sourceLanguage == "kk" }
        assertThat(kazakh.recommended).isTrue()
        assertThat(kazakh.sha256).matches("[0-9a-f]{64}")
        assertThat(kazakh.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/kaz_newscrawl_2016_100K.tar.gz")
        assertThat(kazakh.oldTitles).containsExactly(CorpusCountsDictionary.title("kk"))
        assertThat(kazakh.titles.keys).containsAtLeastElementsIn(locales)
        assertThat(kazakh.displayTitle(Locale.ENGLISH)).isEqualTo("Word frequency (Leipzig Corpora Collection)")
        assertThat(kazakh.description(Locale.ENGLISH)).startsWith("Leipzig Corpora Collection, Kazakh:")
        val thai = built.single { it.sourceLanguage == "th" }
        assertThat(thai.category).isEqualTo(CatalogCategory.FREQUENCY)
        assertThat(thai.recommended).isTrue()
        assertThat(thai.sha256).matches("[0-9a-f]{64}")
        assertThat(thai.downloadUrl).matches(
            "https://raw\\.githubusercontent\\.com/PyThaiNLP/pythainlp/[0-9a-f]{40}/pythainlp/corpus/tnc_freq\\.txt",
        )
        assertThat(thai.oldTitles).containsExactly(CorpusCountsDictionary.title("th"))
        assertThat(thai.titles.keys).containsAtLeastElementsIn(locales)
        assertThat(thai.displayTitle(Locale.ENGLISH)).isEqualTo("Word frequency (Thai National Corpus)")
        assertThat(legacy!!.entries.none { it.id == thai.id }).isTrue()
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
    fun `khmer offers the english and russian wiktionaries, transcriptions and tesseract fast, ticked, and no frequency`() {
        val khmer = catalog!!.entries.filter { it.sourceLanguage == "km" }
        assertThat(khmer.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-km-en", "wiktionary-km-ru", "wiktionary-km-transcription", "tesseract-fast-khm",
        )
        assertThat(khmer.map { it.id }).contains("tesseract-best-khm")
        assertThat(khmer.none { it.category == CatalogCategory.FREQUENCY }).isTrue()
    }

    @Test
    fun `persian offers five wiktionaries, its transcription and wordfreq's small list, ticked, and no on-device model`() {
        val persian = catalog!!.entries.filter { it.sourceLanguage == "fa" }
        assertThat(persian.map { it.id }).containsExactly(
            "wiktionary-fa-en", "wiktionary-fa-ru", "wiktionary-fa-fr", "wiktionary-fa-pl", "wiktionary-fa-zh",
            "wiktionary-fa-transcription", "wordfreq-fa",
        )
        assertThat(persian.all { it.recommended }).isTrue()
        assertThat(persian.first { it.id == "wordfreq-fa" }.downloadUrl).endsWith("/small_fa.msgpack.gz")
    }

    @Test
    fun `hebrew offers six wiktionaries, its transcription, wordfreq's large list and tesseract fast, ticked, and best`() {
        val hebrew = catalog!!.entries.filter { it.sourceLanguage == "he" }
        assertThat(hebrew.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-he-en", "wiktionary-he-ru", "wiktionary-he-es", "wiktionary-he-ja", "wiktionary-he-pl",
            "wiktionary-he-zh", "wiktionary-he-transcription", "wordfreq-he", "tesseract-fast-heb",
        )
        assertThat(hebrew.filterNot { it.recommended }.map { it.id }).containsExactly("tesseract-best-heb")
        assertThat(hebrew.first { it.id == "wordfreq-he" }.downloadUrl).endsWith("/large_he.msgpack.gz")
    }

    @Test
    fun `kazakh offers six wiktionaries, its transcription, the leipzig counts and paddle, ticked, and tesseract`() {
        val kazakh = catalog!!.entries.filter { it.sourceLanguage == "kk" }
        assertThat(kazakh.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-kk-en", "wiktionary-kk-ru", "wiktionary-kk-fr", "wiktionary-kk-ko", "wiktionary-kk-pl",
            "wiktionary-kk-zh", "wiktionary-kk-transcription", "corpus-kk", "paddle-cyrillic-kk",
        )
        assertThat(kazakh.filterNot { it.recommended }.map { it.id })
            .containsExactly("tesseract-fast-kaz", "tesseract-best-kaz", "paddle-detector-v5-kk")
    }

    @Test
    fun `kyrgyz offers two wiktionaries, its transcription, the leipzig counts and paddle, ticked, and tesseract`() {
        val kyrgyz = catalog!!.entries.filter { it.sourceLanguage == "ky" }
        assertThat(kyrgyz.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-ky-en", "wiktionary-ky-ru", "wiktionary-ky-transcription", "corpus-ky", "paddle-cyrillic-ky",
        )
        assertThat(kyrgyz.filterNot { it.recommended }.map { it.id })
            .containsExactly("tesseract-fast-kir", "tesseract-best-kir", "paddle-detector-v5-ky")
    }

    @Test
    fun `swedish offers eleven wiktionaries, its transcription, wordfreq's large list and paddle latin, ticked, and the other models`() {
        val swedish = catalog!!.entries.filter { it.sourceLanguage == "sv" }
        assertThat(swedish.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-sv-en", "wiktionary-sv-ru", "wiktionary-sv-de", "wiktionary-sv-es", "wiktionary-sv-fr",
            "wiktionary-sv-ja", "wiktionary-sv-ko", "wiktionary-sv-pl", "wiktionary-sv-pt", "wiktionary-sv-tr",
            "wiktionary-sv-zh", "wiktionary-sv-transcription", "wordfreq-sv", "paddle-latin-sv",
        )
        assertThat(swedish.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-swe", "tesseract-best-swe", "paddle-v6-sv", "paddle-detector-v5-sv", "paddle-detector-v6-sv",
        )
        assertThat(swedish.first { it.id == "wordfreq-sv" }.downloadUrl).endsWith("/large_sv.msgpack.gz")
    }

    @Test
    fun `danish offers seven wiktionaries, its transcription, wordfreq's small list and paddle latin, ticked, and the other models`() {
        val danish = catalog!!.entries.filter { it.isFor(Language.DANISH) }
        assertThat(danish.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-da-en", "wiktionary-da-ru", "wiktionary-da-fr", "wiktionary-da-ja", "wiktionary-da-pl",
            "wiktionary-da-tr", "wiktionary-da-zh", "wiktionary-da-transcription", "wordfreq-da", "paddle-latin-da",
        )
        assertThat(danish.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-dan", "tesseract-best-dan", "paddle-v6-da", "paddle-detector-v5-da", "paddle-detector-v6-da",
        )
        assertThat(danish.first { it.id == "wordfreq-da" }.downloadUrl).endsWith("/small_da.msgpack.gz")
        assertThat(danish.first { it.id == "wiktionary-da-en" }.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (da–en)")
    }

    @Test
    fun `romanian offers eight wiktionaries, its transcription, wordfreq's small list and paddle latin, ticked, and the other models`() {
        val romanian = catalog!!.entries.filter { it.isFor(Language.ROMANIAN) }
        assertThat(romanian.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-ro-en", "wiktionary-ro-ru", "wiktionary-ro-es", "wiktionary-ro-fr", "wiktionary-ro-ja",
            "wiktionary-ro-pl", "wiktionary-ro-tr", "wiktionary-ro-zh", "wiktionary-ro-transcription", "wordfreq-ro",
            "paddle-latin-ro",
        )
        assertThat(romanian.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-ron", "tesseract-best-ron", "paddle-v6-ro", "paddle-detector-v5-ro", "paddle-detector-v6-ro",
        )
        assertThat(romanian.first { it.id == "wordfreq-ro" }.downloadUrl).endsWith("/small_ro.msgpack.gz")
        assertThat(romanian.first { it.id == "wiktionary-ro-en" }.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (ro–en)")
    }

    @Test
    fun `latin offers eleven wiktionaries, its transcription, the leipzig counts and paddle v6, ticked, and the other models`() {
        val latin = catalog!!.entries.filter { it.isFor(Language.LATIN) }
        assertThat(latin.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-la-en", "wiktionary-la-ru", "wiktionary-la-de", "wiktionary-la-es", "wiktionary-la-fr",
            "wiktionary-la-it", "wiktionary-la-ja", "wiktionary-la-ko", "wiktionary-la-pl", "wiktionary-la-pt",
            "wiktionary-la-zh", "wiktionary-la-transcription", "corpus-la", "paddle-v6-la",
        )
        assertThat(latin.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-lat", "tesseract-best-lat", "paddle-latin-la", "paddle-detector-v5-la", "paddle-detector-v6-la",
        )
        assertThat(latin.filter { it.category == CatalogCategory.FREQUENCY }.map { it.id }).containsExactly("corpus-la")
        assertThat(latin.first { it.id == "wiktionary-la-en" }.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (la–en)")
    }

    @Test
    fun `lithuanian offers seven wiktionaries, its transcription, wordfreq's small list and tesseract fast, ticked, and the other models`() {
        val lithuanian = catalog!!.entries.filter { it.isFor(Language.LITHUANIAN) }
        assertThat(lithuanian.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-lt-en", "wiktionary-lt-ru", "wiktionary-lt-de", "wiktionary-lt-fr", "wiktionary-lt-ko",
            "wiktionary-lt-pl", "wiktionary-lt-zh", "wiktionary-lt-transcription", "wordfreq-lt", "tesseract-fast-lit",
        )
        assertThat(lithuanian.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-best-lit", "paddle-v6-lt", "paddle-latin-lt", "paddle-detector-v5-lt", "paddle-detector-v6-lt",
        )
        assertThat(lithuanian.first { it.id == "wordfreq-lt" }.downloadUrl).endsWith("/small_lt.msgpack.gz")
        assertThat(lithuanian.first { it.id == "wiktionary-lt-en" }.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (lt–en)")
    }

    @Test
    fun `slovak offers six wiktionaries, its transcription, wordfreq's small list and tesseract fast, ticked, and the other models`() {
        val slovak = catalog!!.entries.filter { it.isFor(Language.SLOVAK) }
        assertThat(slovak.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-sk-en", "wiktionary-sk-ru", "wiktionary-sk-fr", "wiktionary-sk-pl", "wiktionary-sk-tr",
            "wiktionary-sk-zh", "wiktionary-sk-transcription", "wordfreq-sk", "tesseract-fast-slk",
        )
        assertThat(slovak.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-best-slk", "paddle-v6-sk", "paddle-latin-sk", "paddle-detector-v5-sk", "paddle-detector-v6-sk",
        )
        assertThat(slovak.first { it.id == "wordfreq-sk" }.downloadUrl).endsWith("/small_sk.msgpack.gz")
    }

    @Test
    fun `slovenian offers six wiktionaries, its transcription, wordfreq's small list and paddle latin, ticked, and the other models`() {
        val slovenian = catalog!!.entries.filter { it.isFor(Language.SLOVENIAN) }
        assertThat(slovenian.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-sl-en", "wiktionary-sl-ru", "wiktionary-sl-fr", "wiktionary-sl-pl", "wiktionary-sl-tr",
            "wiktionary-sl-zh", "wiktionary-sl-transcription", "wordfreq-sl", "paddle-latin-sl",
        )
        assertThat(slovenian.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-slv", "tesseract-best-slv", "paddle-v6-sl", "paddle-detector-v5-sl", "paddle-detector-v6-sl",
        )
        assertThat(slovenian.first { it.id == "wordfreq-sl" }.downloadUrl).endsWith("/small_sl.msgpack.gz")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.SLOVENIAN) }).isEqualTo(slovenian)
    }

    @Test
    fun `slovak also reads czech term dictionaries, not czech models, frequencies or transcriptions`() {
        val czech = catalog!!.entries.filter { it.isReadBy(Language.SLOVAK) && !it.isFor(Language.SLOVAK) }
        assertThat(czech).isNotEmpty()
        assertThat(czech.all { it.sourceLanguage == "cs" && it.kind == DictionaryKind.TERM }).isTrue()
        assertThat(czech.map { it.id }).containsAtLeast("wiktionary-cs-en", "wiktionary-cs-ru")
        assertThat(czech.map { it.id }).containsNoneOf("wordfreq-cs", "tesseract-fast-ces", "wiktionary-cs-transcription")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.CZECH) }).isEqualTo(catalog!!.entries.filter { it.isFor(Language.CZECH) })
    }

    @Test
    fun `norwegian lists bokmål, norwegian and nynorsk wiktionaries, one ticked per gloss language, and bokmål's transcription`() {
        val norwegian = catalog!!.entries.filter { it.isFor(Language.NORWEGIAN) }
        assertThat(norwegian.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-nb-en", "wiktionary-nb-ja", "wiktionary-nb-pl", "wiktionary-nb-zh", "wiktionary-no-ru",
            "wiktionary-no-fr", "wiktionary-no-vi", "wiktionary-nb-transcription", "wordfreq-no", "paddle-v6-no",
        )
        assertThat(norwegian.filter { it.id.startsWith("wiktionary-nn-") }.map { it.recommended }).containsExactly(
            false, false, false, false,
        )
        assertThat(norwegian.first { it.id == "wordfreq-no" }.downloadUrl).endsWith("/large_nb.msgpack.gz")
        val bokmal = norwegian.first { it.id == "wiktionary-nb-en" }
        assertThat(bokmal.downloadUrl).contains("/dict/nb/en/wty-nb-en.zip")
        assertThat(bokmal.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (nb–en)")
        assertThat(bokmal.description(Locale.ENGLISH)).startsWith("Wiktionary, Norwegian Bokmål → English:")
        assertThat(norwegian.first { it.id == "wiktionary-nn-en" }.description(Locale.forLanguageTag("ru")))
            .startsWith("Викисловарь, нюнорск → английский:")
        assertThat(norwegian.first { it.id == "wiktionary-no-ru" }.displayTitle(Locale.ENGLISH)).isEqualTo("Wiktionary (no–ru)")
        assertThat(norwegian.first { it.id == "tesseract-fast-nor" }.recommended).isFalse()
    }

    @Test
    fun `korean has KRDICT ticked for its gloss languages and the hanja dictionary from the app`() {
        val korean = catalog!!.entries.filter { it.sourceLanguage == "ko" }
        val krdict = korean.filter { it.template == "krdict" }
        assertThat(krdict.map { it.targetLanguage })
            .containsExactly("ar", "en", "es", "fr", "id", "ja", "mn", "ru", "th", "vi", "zh", "ko")
        assertThat(krdict.all { it.recommended && it.category == CatalogCategory.MAIN }).isTrue()
        assertThat(korean.first { it.id == "wiktionary-ko-en" }.recommended).isFalse()
        assertThat(krdict.first { it.id == "krdict-ru" }.displayTitle(Locale.ENGLISH)).isEqualTo("KRDICT (ko–ru)")
        assertThat(krdict.first { it.id == "krdict-ru" }.description(Locale.ENGLISH)).contains("Korean → Russian")
        val hanja = korean.single { it.kind == DictionaryKind.KANJI }
        assertThat(hanja.downloadUrl).isEqualTo("${ASSET_URL_PREFIX}catalog/unihan-hanja.zip")
        assertThat(hanja.recommended).isTrue()
        // KRDICT's archives all name themselves "KRDICT ..."; the monolingual one matches its whole title only.
        fun installed(title: String) = DictionaryEntity(
            title = title, revision = "1", kind = DictionaryKind.TERM, sourceLanguage = "ko", targetLanguage = null,
            frequencyMode = null, enabled = true, priority = 0, directory = "d", termCount = 1, frequencyCount = 0,
            pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
            author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
        )
        assertThat(korean.filter { it.matches(installed("KRDICT EN")) }.map { it.id }).containsExactly("krdict-en")
        assertThat(korean.filter { it.matches(installed("KRDICT")) }.map { it.id }).containsExactly("krdict-ko")
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
        // Older versions read no recognition models.
        val v2Japanese = catalog!!.entries.filter { it.sourceLanguage == "ja" && it.category != CatalogCategory.GLOSSARY }
            .filter { it.template != "wiktionary-transcription" && !it.isModel }
        assertThat(entries.map { it.id }).containsExactlyElementsIn(v2Japanese.map { it.id })
    }
}
