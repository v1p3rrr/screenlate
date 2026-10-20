package com.vpr.screenlate.core.anki.audio

import com.vpr.screenlate.core.common.language.AudioRegion
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parsers for the pages and API answers of the audio sources, kept free of I/O for tests. */
internal object AudioPages {
    /**
     * A recording found for a term; [name] is the speaker when the source names one, [rank] how close its title is to
     * the term ([recordingRank]).
     */
    data class Found(val url: String, val name: String = "", val rank: Int = 0)

    private val LANGUAGE_POD_ROW = Regex("""class="[^"]*\bdc-result-row\b""")
    private val SOURCE = Regex("""<source\s+src="([^"]+)"""")
    private val VOCAB = Regex("""class=['"]dc-vocab['"]\s*>([^<]*)<""")
    private val VOCAB_KANA = Regex("""class=['"]dc-vocab_kana['"]\s*>([^<]*)<""")

    /**
     * Rows of the JapanesePod101 dictionary search whose word and kana match. Kana-only words have no separate
     * kana field.
     */
    fun languagePod(html: String, term: String, reading: String): List<Found> {
        val rows = LANGUAGE_POD_ROW.findAll(html).map { it.range.first }.toList()
        return rows.mapIndexedNotNull { index, start ->
            val row = html.substring(start, rows.getOrElse(index + 1) { html.length })
            val url = SOURCE.find(row)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            val vocab = VOCAB.find(row)?.groupValues?.get(1)?.trim().orEmpty()
            val kana = VOCAB_KANA.find(row)?.groupValues?.get(1)?.trim().orEmpty()
            val matches = if (vocab.isEmpty()) {
                kana == term
            } else {
                vocab == term && (reading.isEmpty() || kana.isEmpty() || kana == reading)
            }
            Found(url).takeIf { matches }
        }.distinct()
    }

    private val JISHO_AUDIO = Regex("""<audio id="audio_([^":]+):([^"]+)"[^>]*>(.*?)</audio>""", RegexOption.DOT_MATCHES_ALL)

    /** The word audio of jisho.org search results for this term and reading. */
    fun jisho(html: String, term: String, reading: String): List<Found> =
        JISHO_AUDIO.findAll(html).mapNotNull { match ->
            val (expression, kana, sources) = match.destructured
            if (expression != term || kana != reading.ifEmpty { term }) return@mapNotNull null
            SOURCE.findAll(sources).map { it.groupValues[1] }
                .firstOrNull { it.endsWith(".mp3") }
                ?.let { Found(if (it.startsWith("//")) "https:$it" else it) }
        }.distinct().toList()

    /** Characters with a meaning in CirrusSearch (Lucene) regular expressions. */
    private val REGEX_SPECIAL = setOf('.', '?', '+', '*', '|', '{', '}', '[', ']', '(', ')', '"', '\\', '#', '@', '&', '<', '>', '~')

    private fun luceneEscape(text: String) = buildString {
        text.forEach { if (it in REGEX_SPECIAL) append('\\'); append(it) }
    }

    /**
     * [term] for a Lucene regular expression; with [marks], every letter may carry any of them, as titles that write
     * the word vowelled do (`Ar-كِتَابٌ.oga` for كتاب).
     */
    private fun luceneTerm(term: String, marks: String): String = buildString {
        val anyMarks = if (marks.isEmpty()) "" else "[$marks]*"
        withoutMarks(term, marks).forEach { if (it in REGEX_SPECIAL) append('\\'); append(it); append(anyMarks) }
    }

    /** [term] as a group of a Kotlin regular expression, every letter followed by any of [marks]. */
    private fun titleTerm(term: String, marks: String): String {
        val anyMarks = if (marks.isEmpty()) "" else marks.asIterable().joinToString("", "[", "]*") { "\\x{%x}".format(it.code) }
        return withoutMarks(term, marks).map { Regex.escape(it.toString()) + anyMarks }.joinToString("", "(", ")")
    }

    private fun withoutMarks(term: String, marks: String) = if (marks.isEmpty()) term else term.filterNot { it in marks }

    /**
     * How close a recording titled [word] is to [term] read as [reading], in a language whose titles may add [marks]:
     * 0 the term itself, 1 its vowelled reading, 2 the same letters with other marks, which may be another word
     * (the verb كَلَبَ for the noun كلب). Without marks titles hold the term itself, so all are 0.
     */
    fun recordingRank(word: String, term: String, reading: String, marks: String): Int = when {
        marks.isEmpty() || word == term -> 0
        reading.isNotEmpty() && word == reading -> 1
        else -> 2
    }

    /**
     * A plain title word in front of a regular expression lets the search index narrow the candidates first;
     * a regular expression alone scans every file title and takes many seconds.
     */
    private fun titleWord(term: String) = "intitle:\"${term.replace("\"", "")}\" "

    /**
     * Commons file search for Lingua Libre recordings in the languages of [wikidataIds]: `LL-Q5287 (jpn)-Speaker-猫.wav`.
     * The Wikidata item names the language; the code after it varies: Greek recordings carry `ell`, `gre` or `mis`.
     * Titles may add [marks] to the term's letters. Then the plain title word is left out: the index files a vowelled
     * title under other words (`…-مَدْرَسَة.wav` is not found by "مدرسة"), and the item's prefix keeps the expression fast.
     */
    fun linguaLibreSearch(term: String, wikidataIds: List<String>, marks: String = ""): String {
        val item = wikidataIds.singleOrNull() ?: wikidataIds.joinToString("|", "(", ")")
        val narrowed = if (marks.isEmpty()) titleWord(term) else ""
        return narrowed + """intitle:/LL-$item \([a-z]{3}\)-.*-${luceneTerm(term, marks)}\.wav/"""
    }

    /**
     * Commons file search for Wiktionary recordings: `Ja-猫.ogg`, `ja-us-猫2.oga`, and with a region code of
     * [regions] longer than two letters, `Es-am-lat-agua.ogg`; titles may add [marks] to the term's letters. It also
     * matches inside titles (`…Kerbush-вода.wav` for sh), which [wiktionaryFiles] leaves out.
     */
    fun wiktionarySearch(
        term: String,
        languageCode: String,
        regions: List<AudioRegion> = emptyList(),
        marks: String = "",
    ): String {
        val prefix = languageCode.take(1).let { "[${it.uppercase()}${it.lowercase()}]" } + languageCode.drop(1)
        val longCodes = longCodes(regions)
        val region = if (longCodes.isEmpty()) "[a-zA-Z]{2}" else {
            "(${(longCodes.map(::luceneEscape) + "[a-zA-Z]{2}").joinToString("|")})"
        }
        return titleWord(withoutMarks(term, marks)) +
            """intitle:/$prefix(-$region)?-${luceneTerm(term, marks)}[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/"""
    }

    /** Region codes of more than two letters (`am-lat`), which file titles hold as well as two-letter ones. */
    private fun longCodes(regions: List<AudioRegion>): List<String> =
        regions.flatMap { it.codes }.filter { it.length > 2 }.distinct()

    /**
     * File titles of a Commons `list=search` answer. A search that matches a redirect gives its target (`Ar-كتاب.ogg`
     * gives `Ar-كِتَابٌ.oga`). Throws [CommonsErrorException] for an error answer, which is not "no recording".
     */
    fun commonsTitles(answer: JsonElement): List<String> {
        checkCommons(answer)
        return answer.jsonObject["query"]?.jsonObject?.get("search")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.contentOrNull }
    }

    /** File URLs of a Commons `prop=imageinfo` answer, in the order of [titles]; throws as [commonsTitles] does. */
    fun commonsUrls(answer: JsonElement, titles: List<String>): Map<String, String> {
        checkCommons(answer)
        val pages = answer.jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject?.values.orEmpty()
        val urls = pages.mapNotNull { page ->
            val title = page.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = page.jsonObject["imageinfo"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            title to url
        }.toMap()
        return titles.mapNotNull { title -> urls[title]?.let { title to it } }.toMap()
    }

    private fun checkCommons(answer: JsonElement) {
        val error = answer.jsonObject["error"] ?: return
        throw CommonsErrorException((error as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull ?: "error")
    }

    /** The speaker in a Lingua Libre file title (`File:LL-Q1860 (eng)-Speaker-cat.wav`). */
    fun linguaLibreSpeaker(title: String, term: String, marks: String = ""): String {
        val rest = title.substringAfter(")-", "")
        val word = linguaLibreWord(rest, term, marks)
        return if (word != null) rest.dropLast(word.length + ".wav".length + 1) else rest.substringBeforeLast('-', "")
    }

    /** The word a Lingua Libre title ends with (`Speaker-كِتَاب.wav`) when it is [term], with or without [marks]. */
    private fun linguaLibreWord(rest: String, term: String, marks: String): String? =
        Regex("-${titleTerm(term, marks)}\\.wav$").find(rest)?.groupValues?.get(1)

    /**
     * Lingua Libre recordings (title to URL) with their speakers, those closest to [term] read as [reading] first
     * ([recordingRank]), then in the order of their languages in [wikidataIds], and in each those whose speaker part
     * holds a hyphen last: such a title is usually a compound that ends with the term ("kitty-cat" for "cat"), though a
     * speaker's name may hold one too. A file of an item not in [wikidataIds] is left out: a search that matches a
     * redirect gives its target, which may be filed under another item (an Arabic title moved to a dialect's).
     */
    fun linguaLibre(
        files: List<Pair<String, String>>,
        term: String,
        wikidataIds: List<String> = emptyList(),
        marks: String = "",
        reading: String = "",
    ): List<Found> {
        fun rank(title: String): Int {
            val word = linguaLibreWord(title.substringAfter(")-", ""), term, marks).orEmpty()
            return recordingRank(word, term, reading, marks)
        }
        val order = compareBy<Pair<String, String>> { (title, _) -> rank(title) }
            .thenBy { (title, _) -> languageIndex(title, wikidataIds) }
            .thenBy { (title, _) -> '-' in linguaLibreSpeaker(title, term, marks) }
        return files
            .filter { (title, _) -> wikidataIds.isEmpty() || languageIndex(title, wikidataIds) < wikidataIds.size }
            .sortedWith(order)
            .map { (title, url) -> Found(url, linguaLibreSpeaker(title, term, marks), rank(title)) }
    }

    /** Where the item a Lingua Libre title names is in [wikidataIds]; after them all for another. */
    private fun languageIndex(title: String, wikidataIds: List<String>): Int =
        wikidataIds.indexOf(title.removePrefix("File:LL-").substringBefore(' ')).takeIf { it >= 0 } ?: wikidataIds.size

    /**
     * The region code in a Wiktionary file title (`File:En-us-water.ogg` gives `us`, `File:Es-am-lat-agua.ogg` gives
     * `am-lat` when [regions] list it); null when it names none.
     */
    fun wiktionaryRegion(
        title: String,
        term: String,
        languageCode: String,
        regions: List<AudioRegion> = emptyList(),
        marks: String = "",
    ): String? {
        val pattern = wiktionaryTitle(term, languageCode, regions, marks)
        return pattern.find(title.removePrefix("File:"))?.groupValues?.get(1)?.lowercase()?.ifEmpty { null }
    }

    /**
     * The [files] (title to URL) whose whole title is a Wiktionary recording of [term] under [languageCode], with or
     * without [marks]. A search that matches a redirect gives its target, kept only when it records the same letters
     * (`Ar-جرس.ogg` leads to `Ar-حرس.oga`).
     */
    fun wiktionaryFiles(
        files: List<Pair<String, String>>,
        term: String,
        languageCode: String,
        regions: List<AudioRegion> = emptyList(),
        marks: String = "",
    ): List<Pair<String, String>> {
        val pattern = wiktionaryTitle(term, languageCode, regions, marks)
        return files.filter { (title, _) -> pattern.matches(title.removePrefix("File:")) }
    }

    /**
     * Wiktionary recordings of [term] read as [reading] among the search's [files] (title to URL), named by their
     * titles: [wiktionaryFiles], the closest first ([recordingRank]), then in the order of [byRegion].
     */
    fun wiktionary(
        files: List<Pair<String, String>>,
        term: String,
        reading: String,
        languageCode: String,
        regions: List<AudioRegion> = emptyList(),
        marks: String = "",
    ): List<Found> {
        val pattern = wiktionaryTitle(term, languageCode, regions, marks)
        val found = wiktionaryFiles(files, term, languageCode, regions, marks)
        return byRegion(found, term, languageCode, regions, marks).map { (title, url) ->
            val name = title.removePrefix("File:")
            val word = pattern.find(name)?.groupValues?.get(2).orEmpty()
            Found(url, name, recordingRank(word, term, reading, marks))
        }.sortedBy { it.rank }
    }

    /** Groups: the region code, the word. */
    private fun wiktionaryTitle(term: String, languageCode: String, regions: List<AudioRegion>, marks: String): Regex {
        val codes = (longCodes(regions).map(Regex::escape) + "[a-z]{2}").joinToString("|")
        return Regex(
            """^${Regex.escape(languageCode)}-(?:($codes)-)?${titleTerm(term, marks)}[0-9]*\.[a-z0-9]+$""",
            RegexOption.IGNORE_CASE,
        )
    }

    /**
     * Wiktionary files (title to URL) in the order of their regions in [regions]; a file of no listed region goes where
     * [AudioRegion.OTHER] is, or last. Files of the same region keep their order.
     */
    fun byRegion(
        files: List<Pair<String, String>>,
        term: String,
        languageCode: String,
        regions: List<AudioRegion>,
        marks: String = "",
    ): List<Pair<String, String>> {
        if (regions.isEmpty()) return files
        val other = regions.indexOf(AudioRegion.OTHER).takeIf { it >= 0 } ?: regions.size
        return files.sortedBy { (title, _) ->
            val code = wiktionaryRegion(title, term, languageCode, regions, marks)
            regions.indexOfFirst { code != null && code in it.codes }.takeIf { it >= 0 } ?: other
        }
    }
}
