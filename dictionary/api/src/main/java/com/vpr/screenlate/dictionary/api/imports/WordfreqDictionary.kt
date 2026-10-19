package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a Yomitan frequency dictionary from one of wordfreq's word lists (`large_<lang>.msgpack.gz`, or `small_` where
 * wordfreq has no large one), as `scripts/frequency/wordfreq_dictionary.py` does from the Python package.
 *
 * The list is gzipped MessagePack: a header `{"format": "cB", "version": 1}`, then one array of words per centibel of
 * frequency, the most frequent first. Ranks follow that order: 1 is the most frequent word. Tokens without a letter are
 * left out. wordfreq lowercases everything, while dictionaries write names and German nouns capitalized, so each word
 * also gets its capitalized form (in the language's rules, Turkish `i` → `İ`) with the same rank. A language written in
 * two scripts gets each word's spellings in the other one too (`LanguageSupport.frequencySpellings`): wordfreq writes
 * Chinese in Simplified characters and Serbo-Croatian in Latin letters only, which the script leaves as they are, and
 * the Ukrainian apostrophe as ' only. A language's spelling of a word (`LanguageSupport.frequencyWord`, the Greek final
 * ς) replaces the list's. The index credits wordfreq and its sources, as the CC BY-SA 4.0 license of its data asks.
 */
object WordfreqDictionary {
    const val DEFAULT_SIZE = 100_000

    /** Writes the dictionary of the [language] list [source] to [output]; returns the number of ranked words. */
    fun build(source: File, language: String, version: String, output: File, size: Int = DEFAULT_SIZE): Int {
        // Tokens without letters are dropped, so more words are read than needed.
        val words = source.inputStream().use { words(it, size * 2) }
        val support = Language.ofDictionaries(language)?.support
        var otherSpellings = false
        val listed = if (support == null) words else words.map(support::frequencyWord)
        val rows = termMeta(listed, size, Locale.forLanguageTag(language)) { word ->
            support?.frequencySpellings(word).orEmpty().also { if (it.isNotEmpty()) otherSpellings = true }
        }
        val ranked = rows.maxOfOrNull { it.second } ?: 0
        write(output, index(language, version, ranked, otherSpellings), rows)
        return ranked
    }

    /** The first [limit] words of a gzipped word list, the most frequent first. */
    fun words(gzipped: InputStream, limit: Int): List<String> {
        val reader = MsgPackReader(GZIPInputStream(gzipped))
        val count = reader.readArrayHeader()
        val header = if (count > 0) reader.readValue() as? Map<*, *> else null
        if (header?.get("format") != "cB" || header["version"] != 1L) throw IOException("Not a wordfreq word list")
        val words = ArrayList<String>()
        repeat(count - 1) {
            repeat(reader.readArrayHeader()) {
                if (words.size < limit) words += reader.readString() else reader.skip()
            }
            if (words.size >= limit) return words
        }
        return words
    }

    /**
     * `term_meta_bank` rows (word, rank) for the first [size] words with a letter, ranked from 1. The [otherSpellings]
     * of a word, capitalized as well, get its rank unless the list has them as words of their own.
     */
    fun termMeta(
        words: List<String>,
        size: Int,
        locale: Locale,
        otherSpellings: (String) -> List<String> = { emptyList() },
    ): List<Pair<String, Int>> {
        val rows = mutableListOf<Pair<String, Int>>()
        val seen = HashSet<String>()
        val others = mutableListOf<Pair<String, Int>>()
        var rank = 0
        for (word in words) {
            if (rank >= size) break
            if (!hasLetter(word) || !seen.add(word)) continue
            rank++
            rows += word to rank
            capitalized(word, locale)?.takeIf(seen::add)?.let { rows += it to rank }
            otherSpellings(word).forEach { other ->
                others += other to rank
                capitalized(other, locale)?.let { others += it to rank }
            }
        }
        for (other in others) {
            if (seen.add(other.first)) rows += other
        }
        return rows
    }

    /** The word with its first letter in upper case, or null when that changes nothing. */
    internal fun capitalized(word: String, locale: Locale): String? {
        if (word.isEmpty()) return null
        val first = Character.charCount(word.codePointAt(0))
        val upper = word.substring(0, first).uppercase(locale) + word.substring(first)
        return upper.takeIf { it != word }
    }

    private fun hasLetter(word: String): Boolean = word.codePoints().anyMatch(Character::isLetter)

    internal fun index(language: String, version: String, size: Int, otherSpellings: Boolean = false) = buildJsonObject {
        put("title", title(language))
        put("format", 3)
        put("revision", "wordfreq-$version-$size")
        put("sequenced", false)
        put("frequencyMode", "rank-based")
        put("author", "Robyn Speer (wordfreq)")
        put("url", "https://github.com/rspeer/wordfreq")
        put(
            "description",
            "Frequency ranks of the $size most frequent words of wordfreq $version for the language $language: 1 is the most frequent." +
                if (otherSpellings) " A word's spellings the list leaves out, in another script or with another apostrophe, have its rank." else "",
        )
        put("attribution", attribution(version))
        put("sourceLanguage", language)
    }

    /** The title of the dictionary built for [language], which its catalog entry names as its installed title. */
    fun title(language: String): String = "wordfreq $language"

    private fun write(output: File, index: JsonObject, rows: List<Pair<String, Int>>) {
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("index.json"))
            zip.write(Json.encodeToString(JsonObject.serializer(), index).encodeToByteArray())
            zip.closeEntry()
            rows.chunked(BANK_SIZE).forEachIndexed { i, bank ->
                zip.putNextEntry(ZipEntry("term_meta_bank_${i + 1}.json"))
                val text = bank.joinToString(",", "[", "]") { (word, rank) -> """[${JsonPrimitive(word)},"freq",$rank]""" }
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    /** Whether [file] has the SHA-256 checksum [expected] (hex, any case). */
    fun hasChecksum(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }

    private fun attribution(version: String): String =
        "Word frequencies from wordfreq $version by Robyn Speer (https://github.com/rspeer/wordfreq), data licensed " +
            "CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). wordfreq's data comes from Wikipedia " +
            "(https://www.wikipedia.org); OPUS OpenSubtitles 2018 (http://opus.nlpl.eu/OpenSubtitles.php), which originates " +
            "from the OpenSubtitles project (http://www.opensubtitles.org/); the SUBTLEX word lists by Marc Brysbaert et al., " +
            "freely available data (http://crr.ugent.be/programs-data/subtitle-frequencies); Google Books Ngrams " +
            "(http://books.google.com/ngrams); the Leeds Internet Corpus (http://corpus.leeds.ac.uk/list.html); ParaCrawl " +
            "(https://paracrawl.eu); NewsCrawl, GlobalVoices, OSCAR, Twitter and Reddit; for Chinese, the word list of the " +
            "Jieba segmenter. This dictionary is shared under CC BY-SA 4.0."

    private const val BANK_SIZE = 10_000
}
