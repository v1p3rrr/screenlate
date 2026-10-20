package com.vpr.screenlate.dictionary.api.imports

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a Yomitan frequency dictionary from a corpus' word counts where wordfreq has no list for the language:
 * PyThaiNLP's counts of the Thai National Corpus (`tnc_freq.txt`), one `word<TAB>count` line per word, the most frequent
 * first, as `scripts/frequency/corpus_catalog.py` checks. Ranks follow the list's order: 1 is the most frequent word.
 * Tokens without a letter, or with a space (the corpus' spelled-out letters "ห - ม - า"), are left out, and the spaces
 * some tokens start with are dropped.
 */
object CorpusCountsDictionary {
    /** A corpus whose counts the app builds a dictionary from. */
    class Corpus(val name: String, val author: String, val url: String, val attribution: String)

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
    )

    /** Writes the dictionary of [language]'s corpus counts [source] to [output]; returns the number of ranked words. */
    fun build(source: File, language: String, version: String, output: File, size: Int = WordfreqDictionary.DEFAULT_SIZE): Int {
        val corpus = CORPORA[language] ?: throw IOException("No corpus counts for $language")
        // Tokens without letters are dropped, so more words are read than needed.
        val words = source.inputStream().use { words(it, size * 2) }
        val rows = WordfreqDictionary.termMeta(words, size, Locale.forLanguageTag(language))
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
