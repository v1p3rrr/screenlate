package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.support
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a Yomitan frequency dictionary from a corpus' word counts where wordfreq has no list for the language:
 * PyThaiNLP's counts of the Thai National Corpus (`tnc_freq.txt`), one `word<TAB>count` line per word, the most frequent
 * first, as `scripts/frequency/corpus_catalog.py` checks; or a Leipzig Corpora Collection archive ([Format.LEIPZIG]).
 * Ranks follow the counts: 1 is the most frequent word. Tokens without a letter, or with a space (the corpus'
 * spelled-out letters "ห - ม - า"), are left out, and the spaces some tokens start with are dropped.
 */
object CorpusCountsDictionary {
    /** How a corpus gives its counts. */
    enum class Format {
        /** `word<TAB>count` lines, the most frequent first. */
        COUNTS,

        /**
         * A Leipzig Corpora Collection archive (`.tar.gz`) whose `-words.txt`, at a place in it that varies, has
         * `id<TAB>word<TAB>count` lines with the tokens as written: capitals, numbers and punctuation among them.
         */
        LEIPZIG,
    }

    /** A corpus whose counts the app builds a dictionary from. */
    class Corpus(
        val name: String,
        val author: String,
        val url: String,
        val attribution: String,
        val format: Format = Format.COUNTS,
    )

    /** The corpora by the language they are for. */
    val CORPORA = mapOf(
        "th" to Corpus(
            name = "Thai National Corpus",
            author = "PyThaiNLP",
            url = "https://github.com/PyThaiNLP/pythainlp",
            attribution = "Word counts of the Thai National Corpus (https://www.arts.chula.ac.th/ling/tnc/) from PyThaiNLP " +
                "(https://github.com/PyThaiNLP/pythainlp, pythainlp/corpus/tnc_freq.txt), released under CC0 1.0 " +
                "(https://creativecommons.org/publicdomain/zero/1.0/).",
        ),
        "kk" to leipzig("Kazakh", "kaz_newscrawl_2016_100K"),
        "ky" to leipzig("Kyrgyz", "kir_newscrawl_2016_100K"),
        "la" to leipzig("Latin", "lat_wikipedia_2021_100K", texts = "Wikipedia of 2021"),
        "eo" to leipzig("Esperanto", "epo_web_2012_100K", texts = "web texts of 2012"),
        "sq" to leipzig("Albanian", "sqi_news_2020_100K", texts = "news of 2020"),
        "sw" to leipzig("Swahili", "swa_news_2020_30K", texts = "news of 2020"),
        "et" to leipzig("Estonian", "est_news_2020_100K", texts = "news of 2020"),
        "az" to leipzig("Azerbaijani", "aze_news_2020_30K", texts = "news of 2020"),
        "uz" to leipzig("Uzbek", "uzb_news_2020_30K", texts = "news of 2020"),
    )

    /** The Leipzig archive [corpus] of [language]'s [texts]. */
    private fun leipzig(language: String, corpus: String, texts: String = "news crawl of 2016") = Corpus(
        name = "Leipzig Corpora Collection",
        author = "Leipzig University",
        url = "https://wortschatz.uni-leipzig.de/en/download/$language",
        attribution = "Word counts of the $language $texts ($corpus) of the Leipzig Corpora Collection " +
            "(https://wortschatz.uni-leipzig.de/), released under CC BY. D. Goldhahn, T. Eckart, U. Quasthoff: Building " +
            "Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages. LREC 2012.",
        format = Format.LEIPZIG,
    )

    /** Writes the dictionary of [language]'s corpus counts [source] to [output]; returns the number of ranked words. */
    fun build(source: File, language: String, version: String, output: File, size: Int = WordfreqDictionary.DEFAULT_SIZE): Int {
        val corpus = CORPORA[language] ?: throw IOException("No corpus counts for $language")
        val locale = Locale.forLanguageTag(language)
        // Tokens without letters are dropped, so more words are read than needed.
        val words = source.inputStream().use { input ->
            when (corpus.format) {
                Format.COUNTS -> words(input, size * 2)
                Format.LEIPZIG -> leipzigWords(input, size * 2, Language.ofDictionaries(language)?.support, locale)
            }
        }
        val rows = WordfreqDictionary.termMeta(words, size, locale)
        val ranked = rows.maxOfOrNull { it.second } ?: 0
        WordfreqDictionary.write(output, index(language, version, ranked, corpus), rows)
        return ranked
    }

    /** The first [limit] words of a count list, the most frequent first. */
    fun words(input: InputStream, limit: Int): List<String> {
        val words = ArrayList<String>()
        input.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (words.size >= limit) break
                val word = line.substringBefore('\t').trim()
                if (word.isEmpty() || word.any(Char::isWhitespace)) continue
                if ('\t' !in line || line.substringAfter('\t').trim().toLongOrNull() == null) {
                    throw IOException("Not a list of word counts")
                }
                words += word
            }
        }
        return words
    }

    /**
     * The first [limit] words of a Leipzig archive's counts ([Format.LEIPZIG]), the most frequent first: lowercased and
     * spelled as the language's dictionaries spell them ([LanguageSupport.frequencyWord]: Kazakh Latin i inside a
     * Cyrillic word is і), the counts of tokens that become one word added up. Only words of letters, with hyphens or
     * apostrophes between them, are kept: numbers, punctuation and tokens such as "Кабар/" are left out.
     */
    fun leipzigWords(gzipped: InputStream, limit: Int, support: LanguageSupport?, locale: Locale): List<String> {
        val list = tarEntry(GZIPInputStream(gzipped)) { it.endsWith("-words.txt") }
            ?: throw IOException("No word list in the archive")
        val counts = LinkedHashMap<String, Long>()
        list.bufferedReader().useLines { lines ->
            for (line in lines) {
                val fields = line.split('\t')
                val count = fields.getOrNull(2)?.toLongOrNull()
                if (fields.size != 3 || count == null) throw IOException("Not a Leipzig word list")
                if (!LEIPZIG_WORD.matches(fields[1])) continue
                val word = fields[1].lowercase(locale).let { support?.frequencyWord(it) ?: it }
                counts[word] = (counts[word] ?: 0) + count
            }
        }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** The data of the first file of a tar stream whose path [wanted] accepts; null when there is none. */
    private fun tarEntry(tar: InputStream, wanted: (String) -> Boolean): InputStream? {
        val header = ByteArray(TAR_BLOCK)
        var longName: String? = null
        while (true) {
            if (!readFully(tar, header) || header.all { it == 0.toByte() }) return null
            val size = header.field(124, 12).trim().ifEmpty { "0" }.toLongOrNull(8) ?: throw IOException("Not a tar archive")
            val type = header[156].toInt().toChar()
            val ustar = header.field(257, 5) == "ustar"
            val name = longName ?: listOfNotNull(header.field(345, 155).takeIf { ustar && it.isNotEmpty() }, header.field(0, 100))
                .joinToString("/")
            longName = null
            val padded = (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK
            when {
                // GNU tar keeps a long path in an entry of its own before the file's.
                type == 'L' -> longName = String(read(tar, size, padded), Charsets.UTF_8).trimEnd('\u0000')
                (type == '0' || type == '\u0000') && wanted(name) -> {
                    if (size > MAX_LIST_BYTES) throw IOException("The word list is too large")
                    return ByteArrayInputStream(read(tar, size, padded))
                }
                else -> skipFully(tar, padded)
            }
        }
    }

    private fun ByteArray.field(offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && this[end] != 0.toByte()) end++
        return String(this, offset, end - offset, Charsets.US_ASCII)
    }

    /** [size] bytes of [input] out of the next [padded]. */
    private fun read(input: InputStream, size: Long, padded: Long): ByteArray {
        val data = ByteArray(size.toInt())
        if (!readFully(input, data)) throw IOException("The archive ends early")
        skipFully(input, padded - size)
        return data
    }

    /** Fills [buffer]; false when the stream ends first. */
    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val buffer = ByteArray(TAR_BLOCK * 16)
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (read < 0) throw IOException("The archive ends early")
            left -= read
        }
    }

    private const val TAR_BLOCK = 512

    /** The 100K archives' lists are 3.6 and 4.8 MB. */
    private const val MAX_LIST_BYTES = 64L shl 20

    /** Letters and marks, with single hyphens or apostrophes between them (сондай-ақ). */
    private val LEIPZIG_WORD = Regex("[\\p{L}\\p{M}]+(?:[-'’][\\p{L}\\p{M}]+)*")

    internal fun index(language: String, version: String, size: Int, corpus: Corpus): JsonObject = buildJsonObject {
        put("title", title(language))
        put("format", 3)
        put("revision", "corpus-$version-$size")
        put("sequenced", false)
        put("frequencyMode", "rank-based")
        put("author", corpus.author)
        put("url", corpus.url)
        put(
            "description",
            "Frequency ranks of the $size most frequent words of the ${corpus.name} for the language $language: 1 is the most frequent.",
        )
        put("attribution", corpus.attribution)
        put("sourceLanguage", language)
    }

    /** The title of the dictionary built for [language], which its catalog entry names as its installed title. */
    fun title(language: String): String = "${CORPORA[language]?.name ?: "Corpus"} ($language)"
}
