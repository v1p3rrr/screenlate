package com.vpr.screenlate.dictionary.api.catalog

import com.google.common.collect.Range
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

    private val measured = DictionaryCatalog.parseSizes(File("src/main/assets/catalog/installed-sizes.json").readText())
    private val catalog =
        DictionaryCatalog.parse(File("src/main/assets/catalog/dictionaries-v2.json").readText(), measured)
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
            .containsExactly("en", "zh", "ko", "es", "fr", "de", "it", "pt", "ru", "pl", "tr", "vi", "lv", "nl", "cs", "id", "ms", "tl", "sh", "uk", "el", "hi", "bn", "ar", "fa", "he", "sv", "no", "da", "ro", "lt", "sk", "sl", "fi", "hu", "bg", "mk", "ta")
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
    fun `thai, kazakh, kyrgyz, latin, esperanto, albanian, swahili, estonian, azerbaijani, uzbek and mongolian have frequency dictionaries built from corpus counts, of PyThaiNLP and Leipzig`() {
        val built = catalog!!.entries.filter { it.build == BUILD_CORPUS_COUNTS }
        assertThat(built.map { it.sourceLanguage })
            .containsExactly("th", "kk", "ky", "la", "eo", "sq", "sw", "et", "az", "uz", "mn").inOrder()
        val mongolian = built.single { it.sourceLanguage == "mn" }
        assertThat(mongolian.recommended).isTrue()
        assertThat(mongolian.sha256).isEqualTo("421ee074485df3a8de98a746e4d37f941d91d5212236d858e53fa0590480761e")
        assertThat(mongolian.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/mon_news_2020_100K.tar.gz")
        assertThat(mongolian.description(Locale.ENGLISH))
            .startsWith("Leipzig Corpora Collection, Mongolian: how common words are in news")
        val uzbek = built.single { it.sourceLanguage == "uz" }
        assertThat(uzbek.recommended).isTrue()
        assertThat(uzbek.sha256).isEqualTo("33844aa9b4ad140e0d40fd51d2ed92d5d1ab67828ef6583344cf81b705f2f38e")
        assertThat(uzbek.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/uzb_news_2020_30K.tar.gz")
        assertThat(uzbek.description(Locale.ENGLISH))
            .startsWith("Leipzig Corpora Collection, Uzbek: how common words are in news")
        val azerbaijani = built.single { it.sourceLanguage == "az" }
        assertThat(azerbaijani.recommended).isTrue()
        assertThat(azerbaijani.sha256).isEqualTo("2340f80d378e83708c4eae11bf78e3260b955a19b566278c5c6e292c93ee508f")
        assertThat(azerbaijani.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/aze_news_2020_30K.tar.gz")
        assertThat(azerbaijani.description(Locale.ENGLISH))
            .startsWith("Leipzig Corpora Collection, Azerbaijani: how common words are in news")
        val estonian = built.single { it.sourceLanguage == "et" }
        assertThat(estonian.recommended).isTrue()
        assertThat(estonian.sha256).isEqualTo("399cf09f698aeb0ffec1e63ddc870b50f15a2394723fed3dcb85d0c697aecce0")
        assertThat(estonian.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/est_news_2020_100K.tar.gz")
        assertThat(estonian.oldTitles).containsExactly(CorpusCountsDictionary.title("et"))
        assertThat(estonian.description(Locale.ENGLISH))
            .startsWith("Leipzig Corpora Collection, Estonian: how common words are in news")
        val swahili = built.single { it.sourceLanguage == "sw" }
        assertThat(swahili.recommended).isTrue()
        assertThat(swahili.sha256).isEqualTo("b46b072eab065bcc4a70ee2e13b1bc310b28d39e2b082434f611501b5bc26a1e")
        assertThat(swahili.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/swa_news_2020_30K.tar.gz")
        assertThat(swahili.oldTitles).containsExactly(CorpusCountsDictionary.title("sw"))
        assertThat(swahili.description(Locale.ENGLISH))
            .startsWith("Leipzig Corpora Collection, Swahili: how common words are in news")
        val esperanto = built.single { it.sourceLanguage == "eo" }
        assertThat(esperanto.recommended).isTrue()
        assertThat(esperanto.sha256).isEqualTo("3f011e22b9a258a998172101fcf5a625dea2fe5fdd5c6069636a10baee64188a")
        assertThat(esperanto.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/epo_web_2012_100K.tar.gz")
        assertThat(esperanto.oldTitles).containsExactly(CorpusCountsDictionary.title("eo"))
        assertThat(esperanto.description(Locale.ENGLISH))
            .startsWith("Leipzig Corpora Collection, Esperanto: how common words are in texts of websites")
        val albanian = built.single { it.sourceLanguage == "sq" }
        assertThat(albanian.recommended).isTrue()
        assertThat(albanian.sha256).isEqualTo("8ff3f8b93a75ef1e9c1cc1c4856718cf1717c658d1fe6cfb4b472175f4073ea6")
        assertThat(albanian.downloadUrl)
            .isEqualTo("https://downloads.wortschatz-leipzig.de/corpora/sqi_news_2020_100K.tar.gz")
        assertThat(albanian.oldTitles).containsExactly(CorpusCountsDictionary.title("sq"))
        assertThat(albanian.description(Locale.ENGLISH)).startsWith("Leipzig Corpora Collection, Albanian: how common words are in news")
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
    fun `hebrew offers seven wiktionaries, its transcription, wordfreq's large list and tesseract fast, ticked, and best`() {
        val hebrew = catalog!!.entries.filter { it.sourceLanguage == "he" }
        assertThat(hebrew.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-he-en", "wiktionary-he-ru", "wiktionary-he-es", "wiktionary-he-ja", "wiktionary-he-pl",
            "wiktionary-he-tr", "wiktionary-he-zh", "wiktionary-he-transcription", "wordfreq-he", "tesseract-fast-heb",
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
    fun `esperanto offers ten wiktionaries, its transcription, the leipzig counts and tesseract fast, ticked, tesseract best and no paddle`() {
        val esperanto = catalog!!.entries.filter { it.isFor(Language.ESPERANTO) }
        assertThat(esperanto.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-eo-en", "wiktionary-eo-ru", "wiktionary-eo-de", "wiktionary-eo-es", "wiktionary-eo-fr",
            "wiktionary-eo-ja", "wiktionary-eo-pl", "wiktionary-eo-pt", "wiktionary-eo-tr", "wiktionary-eo-zh",
            "wiktionary-eo-transcription", "corpus-eo", "tesseract-fast-epo",
        )
        // Paddle's models read every hat letter wrong.
        assertThat(esperanto.filterNot { it.recommended }.map { it.id }).containsExactly("tesseract-best-epo")
        assertThat(esperanto.filter { it.category == CatalogCategory.FREQUENCY }.map { it.id }).containsExactly("corpus-eo")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.ESPERANTO) }).isEqualTo(esperanto)
    }

    @Test
    fun `albanian offers seven wiktionaries, its transcription, paddle latin and leipzig's word counts, ticked, and the other models`() {
        val albanian = catalog!!.entries.filter { it.isFor(Language.ALBANIAN) }
        assertThat(albanian.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-sq-en", "wiktionary-sq-ru", "wiktionary-sq-de", "wiktionary-sq-fr", "wiktionary-sq-pl",
            "wiktionary-sq-tr", "wiktionary-sq-zh", "wiktionary-sq-transcription", "paddle-latin-sq", "corpus-sq",
        )
        assertThat(albanian.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-sqi", "tesseract-best-sqi", "paddle-v6-sq", "paddle-detector-v5-sq", "paddle-detector-v6-sq",
        )
        assertThat(albanian.filter { it.category == CatalogCategory.FREQUENCY }.map { it.id }).containsExactly("corpus-sq")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.ALBANIAN) }).isEqualTo(albanian)
    }

    @Test
    fun `swahili offers four wiktionaries and leipzig's word counts, ticked, and the ocr models unticked`() {
        val swahili = catalog!!.entries.filter { it.isFor(Language.SWAHILI) }
        assertThat(swahili.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-sw-en", "wiktionary-sw-ru", "wiktionary-sw-pl", "wiktionary-sw-zh", "corpus-sw",
        )
        assertThat(swahili.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-swa", "tesseract-best-swa", "paddle-v6-sw", "paddle-latin-sw", "paddle-detector-v5-sw",
            "paddle-detector-v6-sw",
        )
        // Wiktionary has too few Swahili pronunciations for a transcription dictionary.
        assertThat(swahili.none { it.category == CatalogCategory.PRONUNCIATION }).isTrue()
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.SWAHILI) }).isEqualTo(swahili)
    }

    @Test
    fun `welsh offers three wiktionaries and its transcription, ticked, and four ocr models marked more accurate`() {
        val welsh = catalog!!.entries.filter { it.isFor(Language.WELSH) }
        assertThat(welsh.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-cy-en", "wiktionary-cy-ru", "wiktionary-cy-fr", "wiktionary-cy-transcription",
            "tesseract-fast-cym", "tesseract-best-cym", "paddle-v6-cy", "paddle-latin-cy",
        )
        assertThat(welsh.filterNot { it.recommended }.map { it.id })
            .containsExactly("paddle-detector-v6-cy", "paddle-detector-v5-cy")
        // No frequency dictionary for Welsh.
        assertThat(welsh.none { it.category == CatalogCategory.FREQUENCY }).isTrue()
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.WELSH) }).isEqualTo(welsh)
    }

    @Test
    fun `irish offers four wiktionaries and its transcription, ticked, and the ocr models unticked`() {
        val irish = catalog!!.entries.filter { it.isFor(Language.IRISH) }
        assertThat(irish.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-ga-en", "wiktionary-ga-ru", "wiktionary-ga-fr", "wiktionary-ga-pl", "wiktionary-ga-transcription",
        )
        // ML Kit reads Irish with few errors, so no model is marked more accurate.
        assertThat(irish.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-gle", "tesseract-best-gle", "paddle-v6-ga", "paddle-latin-ga", "paddle-detector-v6-ga",
            "paddle-detector-v5-ga",
        )
        assertThat(irish.none { it.category == CatalogCategory.FREQUENCY }).isTrue()
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.IRISH) }).isEqualTo(irish)
    }

    @Test
    fun `scottish gaelic offers two wiktionaries and its transcription, ticked, and two paddle models marked more accurate`() {
        val gaelic = catalog!!.entries.filter { it.isFor(Language.SCOTTISH_GAELIC) }
        assertThat(gaelic.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-gd-en", "wiktionary-gd-ru", "wiktionary-gd-transcription", "paddle-v6-gd", "paddle-latin-gd",
        )
        // Tesseract reads Gaelic with fewer errors than ML Kit too, but by less than half a point.
        assertThat(gaelic.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-gla", "tesseract-best-gla", "paddle-detector-v6-gd", "paddle-detector-v5-gd",
        )
        assertThat(gaelic.none { it.category == CatalogCategory.FREQUENCY }).isTrue()
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.SCOTTISH_GAELIC) }).isEqualTo(gaelic)
    }

    @Test
    fun `estonian offers its wiktionaries, their word forms and frequencies, ticked, the forms mandatory, and paddle v6 ticked`() {
        val estonian = catalog!!.entries.filter { it.isFor(Language.ESTONIAN) }
        assertThat(estonian.filter { it.recommended }.map { it.id }).containsExactly(
            "corpus-et", "paddle-v6-et", "wiktionary-et-en", "wiktionary-et-forms", "wiktionary-et-ru", "wiktionary-et-fr",
            "wiktionary-et-ko", "wiktionary-et-pl", "wiktionary-et-tr", "wiktionary-et-zh", "wiktionary-et-transcription",
        )
        // Paddle v6 got 0.43% of the characters wrong against ML Kit's 1.33%; the rest are offered unticked.
        assertThat(estonian.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-est", "tesseract-best-est", "paddle-latin-et", "paddle-detector-v6-et", "paddle-detector-v5-et",
        )
        val forms = estonian.single { it.category == CatalogCategory.FORMS }
        assertThat(forms.build).isEqualTo(BUILD_FORMS)
        assertThat(forms.downloadUrl).isEqualTo(estonian.single { it.id == "wiktionary-et-en" }.downloadUrl)
        assertThat(catalog!!.mandatory(Language.ESTONIAN)).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS)
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.ESTONIAN) }).isEqualTo(estonian)
    }

    @Test
    fun `azerbaijani offers its wiktionaries, their word forms and frequencies, ticked, the forms mandatory, and paddle v6 ticked`() {
        val azerbaijani = catalog!!.entries.filter { it.isFor(Language.AZERBAIJANI) }
        assertThat(azerbaijani.filter { it.recommended }.map { it.id }).containsExactly(
            "corpus-az", "paddle-v6-az", "wiktionary-az-en", "wiktionary-az-forms", "wiktionary-az-ru", "wiktionary-az-es",
            "wiktionary-az-fr", "wiktionary-az-ko", "wiktionary-az-pl", "wiktionary-az-tr", "wiktionary-az-zh",
            "wiktionary-az-transcription",
        )
        // Paddle v6 got 1.52% of the characters wrong against ML Kit's 4.99% and holds up over pictures; v5 drops every
        // ə, so it is not offered.
        assertThat(azerbaijani.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-aze", "tesseract-best-aze", "paddle-detector-v6-az",
        )
        val forms = azerbaijani.single { it.category == CatalogCategory.FORMS }
        assertThat(forms.build).isEqualTo(BUILD_FORMS)
        assertThat(forms.downloadUrl).isEqualTo(azerbaijani.single { it.id == "wiktionary-az-en" }.downloadUrl)
        assertThat(catalog!!.mandatory(Language.AZERBAIJANI)).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS)
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.AZERBAIJANI) }).isEqualTo(azerbaijani)
    }

    @Test
    fun `uzbek offers its wiktionaries, their word forms and frequencies, ticked, the forms mandatory, and the ocr models unticked`() {
        val uzbek = catalog!!.entries.filter { it.isFor(Language.UZBEK) }
        assertThat(uzbek.filter { it.recommended }.map { it.id }).containsExactly(
            "corpus-uz", "wiktionary-uz-en", "wiktionary-uz-forms", "wiktionary-uz-ru", "wiktionary-uz-tr",
        )
        // ML Kit got 2.72% of the characters wrong and no model a third fewer; Paddle v5 loses lines over pictures, so
        // only v6 is offered.
        assertThat(uzbek.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-uzb", "tesseract-best-uzb", "paddle-v6-uz", "paddle-detector-v6-uz",
        )
        val forms = uzbek.single { it.category == CatalogCategory.FORMS }
        assertThat(forms.build).isEqualTo(BUILD_FORMS)
        assertThat(forms.downloadUrl).isEqualTo(uzbek.single { it.id == "wiktionary-uz-en" }.downloadUrl)
        assertThat(catalog!!.mandatory(Language.UZBEK)).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS)
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.UZBEK) }).isEqualTo(uzbek)
    }

    @Test
    fun `bulgarian, macedonian and mongolian offer their wiktionaries and frequencies with paddle's cyrillic model, ticked`() {
        val bulgarian = catalog!!.entries.filter { it.isFor(Language.BULGARIAN) }
        assertThat(bulgarian.filter { it.recommended }.map { it.id }).containsExactly(
            "wordfreq-bg", "paddle-cyrillic-bg", "wiktionary-bg-en", "wiktionary-bg-ru", "wiktionary-bg-fr", "wiktionary-bg-ko",
            "wiktionary-bg-pl", "wiktionary-bg-tr", "wiktionary-bg-zh", "wiktionary-bg-transcription",
        )
        val macedonian = catalog!!.entries.filter { it.isFor(Language.MACEDONIAN) }
        assertThat(macedonian.filter { it.recommended }.map { it.id }).containsExactly(
            "wordfreq-mk", "paddle-cyrillic-mk", "wiktionary-mk-en", "wiktionary-mk-ru", "wiktionary-mk-pl", "wiktionary-mk-zh",
            "wiktionary-mk-transcription",
        )
        // Mongolian has no wordfreq list; its frequencies are built from Leipzig's news.
        val mongolian = catalog!!.entries.filter { it.isFor(Language.MONGOLIAN) }
        assertThat(mongolian.filter { it.recommended }.map { it.id }).containsExactly(
            "corpus-mn", "paddle-cyrillic-mn", "wiktionary-mn-en", "wiktionary-mn-ru", "wiktionary-mn-fr", "wiktionary-mn-zh",
            "wiktionary-mn-transcription",
        )
        // Paddle's Cyrillic model got 6, 25 and 0 characters wrong over the four screens against Tesseract fast's 104,
        // 115 and 174; Tesseract is offered unticked, the eslav model not at all.
        for ((language, code) in listOf(Language.BULGARIAN to "bul", Language.MACEDONIAN to "mkd", Language.MONGOLIAN to "mon")) {
            val entries = catalog!!.entries.filter { it.isFor(language) }
            assertThat(entries.filterNot { it.recommended }.map { it.id }).containsExactly(
                "tesseract-fast-$code", "tesseract-best-$code", "paddle-detector-v5-${language.code}",
            )
            assertThat(catalog!!.mandatory(language)).containsExactly(CatalogCategory.MAIN)
            assertThat(catalog!!.entries.filter { it.isReadBy(language) }).isEqualTo(entries)
        }
    }

    @Test
    fun `marathi offers its english wiktionary and transcription ticked, no frequency, and tesseract unticked`() {
        val marathi = catalog!!.entries.filter { it.isFor(Language.MARATHI) }
        assertThat(marathi.filter { it.recommended }.map { it.id })
            .containsExactly("wiktionary-mr-en", "wiktionary-mr-transcription")
        // ML Kit's Devanagari model, bundled for Hindi, reads Marathi on the device.
        assertThat(marathi.filterNot { it.recommended }.map { it.id }).containsExactly("tesseract-fast-mar", "tesseract-best-mar")
        assertThat(marathi.none { it.category == CatalogCategory.FREQUENCY }).isTrue()
        assertThat(catalog!!.mandatory(Language.MARATHI)).containsExactly(CatalogCategory.MAIN)
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.MARATHI) }).isEqualTo(marathi)
    }

    @Test
    fun `tamil offers its wiktionaries, transcription, frequency and tesseract fast ticked, tesseract best unticked`() {
        val tamil = catalog!!.entries.filter { it.isFor(Language.TAMIL) }
        assertThat(tamil.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-ta-en", "wiktionary-ta-ru", "wiktionary-ta-fr", "wiktionary-ta-transcription", "wordfreq-ta",
            "tesseract-fast-tam",
        )
        // Paddle's Tamil model reads it far better and replaces the fast model once it is released.
        assertThat(tamil.filterNot { it.recommended }.map { it.id }).containsExactly("tesseract-best-tam")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.TAMIL) }).isEqualTo(tamil)
    }

    @Test
    fun `kannada offers its english wiktionary, transcription and tesseract fast ticked, tesseract best unticked, no frequency`() {
        val kannada = catalog!!.entries.filter { it.isFor(Language.KANNADA) }
        assertThat(kannada.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-kn-en", "wiktionary-kn-transcription", "tesseract-fast-kan",
        )
        // Paddle's Kannada model misreads stacked consonants and is not offered.
        assertThat(kannada.filterNot { it.recommended }.map { it.id }).containsExactly("tesseract-best-kan")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.KANNADA) }).isEqualTo(kannada)
    }

    @Test
    fun `telugu offers its wiktionaries, transcription and tesseract fast ticked, tesseract best unticked, no frequency`() {
        val telugu = catalog!!.entries.filter { it.isFor(Language.TELUGU) }
        assertThat(telugu.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-te-en", "wiktionary-te-ru", "wiktionary-te-zh", "wiktionary-te-transcription", "tesseract-fast-tel",
        )
        // Paddle's Telugu model reads it far better and replaces the fast model once it is released.
        assertThat(telugu.filterNot { it.recommended }.map { it.id }).containsExactly("tesseract-best-tel")
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.TELUGU) }).isEqualTo(telugu)
    }

    @Test
    fun `hungarian offers its wiktionaries, their word forms and frequencies, ticked, the forms mandatory, and the ocr models unticked`() {
        val hungarian = catalog!!.entries.filter { it.isFor(Language.HUNGARIAN) }
        assertThat(hungarian.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-hu-en", "wiktionary-hu-forms", "wiktionary-hu-ru", "wiktionary-hu-fr", "wiktionary-hu-ja",
            "wiktionary-hu-ko", "wiktionary-hu-pl", "wiktionary-hu-tr", "wiktionary-hu-zh", "wiktionary-hu-transcription",
            "wordfreq-hu",
        )
        // No model reads Hungarian enough better than ML Kit to be marked.
        assertThat(hungarian.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-hun", "tesseract-best-hun", "paddle-v6-hu", "paddle-latin-hu", "paddle-detector-v6-hu",
            "paddle-detector-v5-hu",
        )
        val forms = hungarian.single { it.category == CatalogCategory.FORMS }
        assertThat(forms.build).isEqualTo(BUILD_FORMS)
        assertThat(forms.downloadUrl).isEqualTo(hungarian.single { it.id == "wiktionary-hu-en" }.downloadUrl)
        assertThat(catalog!!.mandatory(Language.HUNGARIAN)).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS)
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.HUNGARIAN) }).isEqualTo(hungarian)
    }

    @Test
    fun `finnish offers its wiktionaries, their word forms and frequencies, ticked, the forms mandatory, and the ocr models unticked`() {
        val finnish = catalog!!.entries.filter { it.isFor(Language.FINNISH) }
        assertThat(finnish.filter { it.recommended }.map { it.id }).containsExactly(
            "wiktionary-fi-en", "wiktionary-fi-forms", "wiktionary-fi-ru", "wiktionary-fi-fr", "wiktionary-fi-it",
            "wiktionary-fi-ja", "wiktionary-fi-ko", "wiktionary-fi-pl", "wiktionary-fi-pt", "wiktionary-fi-tr",
            "wiktionary-fi-zh", "wiktionary-fi-transcription", "wordfreq-fi",
        )
        // ML Kit reads Finnish with few errors, so no model is marked more accurate.
        assertThat(finnish.filterNot { it.recommended }.map { it.id }).containsExactly(
            "tesseract-fast-fin", "tesseract-best-fin", "paddle-v6-fi", "paddle-latin-fi", "paddle-detector-v6-fi",
            "paddle-detector-v5-fi",
        )
        // The forms come from the English edition's file, which the English dictionary installs as well.
        val forms = finnish.single { it.category == CatalogCategory.FORMS }
        val english = finnish.single { it.id == "wiktionary-fi-en" }
        assertThat(forms.build).isEqualTo(BUILD_FORMS)
        assertThat(forms.downloadUrl).isEqualTo(english.downloadUrl)
        assertThat(forms.indexUrl).isEqualTo(english.indexUrl)
        assertThat(catalog!!.mandatory(Language.FINNISH)).containsExactly(CatalogCategory.MAIN, CatalogCategory.FORMS)
        assertThat(catalog!!.entries.filter { it.isReadBy(Language.FINNISH) }).isEqualTo(finnish)
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
    fun `measured sizes belong to entries without their own, and give Chinese about what its import takes`() {
        val entries = catalog!!.entries.associateBy { it.id }
        assertThat(measured).isNotEmpty()
        for ((id, size) in measured) {
            assertThat(entries.keys).contains(id)
            assertThat(size.download).isGreaterThan(0L)
            assertThat(size.installed).isGreaterThan(0L)
        }
        // CC-CEDICT, its characters and the word frequency took 33.4, 22.2 and 12.1 MB on the emulator; the shares
        // per kind would give 16.6, 12.8 and 12 MB.
        val chinese = listOf("cc-cedict", "cc-cedict-hanzi", "wordfreq-zh").sumOf { entries.getValue(it).installedBytes }
        assertThat(chinese).isIn(Range.closed(65_000_000L, 73_000_000L))
    }

    @Test
    fun `a text detector has one name with its version and no comma, which a list of tasks would split`() {
        val detectors = catalog!!.entries.filter { it.model?.startsWith("paddle-detector-") == true }
        assertThat(detectors).isNotEmpty()
        for (detector in detectors) {
            assertThat(detector.titles.keys).containsAtLeastElementsIn(locales)
            val version = detector.model!!.removePrefix("paddle-detector-").substringBefore('-')
            for (locale in locales) {
                val title = detector.displayTitle(Locale.forLanguageTag(locale))
                assertThat(title).doesNotContain(",")
                // German joins the words with hyphens: PaddleOCR-v6-Textdetektor.
                assertThat(title).containsMatch("PaddleOCR[ -]$version")
            }
        }
        assertThat(detectors.first { it.model!!.startsWith("paddle-detector-v6") }.displayTitle(Locale.ENGLISH))
            .isEqualTo("PaddleOCR v6 text detector")
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
