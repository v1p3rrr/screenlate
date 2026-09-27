package com.vpr.screenlate.core.anki.audio

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parsers for the pages and API answers of the audio sources, kept free of I/O for tests. */
internal object AudioPages {
    /** A recording found for a term; [name] is the speaker when the source names one. */
    data class Found(val url: String, val name: String = "")

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
     * A plain title word in front of a regular expression lets the search index narrow the candidates first;
     * a regular expression alone scans every file title and takes many seconds.
     */
    private fun titleWord(term: String) = "intitle:\"${term.replace("\"", "")}\" "

    /** Commons file search for Lingua Libre recordings: `LL-Q5287 (jpn)-Speaker-猫.wav`. */
    fun linguaLibreSearch(term: String, wikidataId: String, iso639Part3: String): String =
        titleWord(term) + """intitle:/LL-$wikidataId \($iso639Part3\)-.*-${luceneEscape(term)}\.wav/"""

    /** Commons file search for Wiktionary recordings: `Ja-猫.ogg`, `ja-us-猫2.oga`. */
    fun wiktionarySearch(term: String, languageCode: String): String {
        val prefix = languageCode.take(1).let { "[${it.uppercase()}${it.lowercase()}]" } + languageCode.drop(1)
        return titleWord(term) +
            """intitle:/$prefix(-[a-zA-Z]{2})?-${luceneEscape(term)}[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/"""
    }

    /** File titles of a Commons `list=search` answer. */
    fun commonsTitles(answer: JsonElement): List<String> =
        answer.jsonObject["query"]?.jsonObject?.get("search")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.contentOrNull }

    /** File URLs of a Commons `prop=imageinfo` answer, in the order of [titles]. */
    fun commonsUrls(answer: JsonElement, titles: List<String>): Map<String, String> {
        val pages = answer.jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject?.values.orEmpty()
        val urls = pages.mapNotNull { page ->
            val title = page.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = page.jsonObject["imageinfo"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            title to url
        }.toMap()
        return titles.mapNotNull { title -> urls[title]?.let { title to it } }.toMap()
    }

    /** The speaker in a Lingua Libre file title. */
    fun linguaLibreSpeaker(title: String): String = title.substringAfter(")-", "").substringBeforeLast('-', "")
}
