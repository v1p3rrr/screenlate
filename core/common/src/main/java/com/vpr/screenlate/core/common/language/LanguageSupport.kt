package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Everything that differs between source languages. Code outside the implementations asks this class instead of
 * checking for a particular language.
 */
interface LanguageSupport {
    val language: Language

    /** A characteristic letter of the language, shown on the docked bubble. */
    val glyph: String

    /** Inserted between recognized words; empty for scripts written without spaces. */
    val wordSeparator: String

    /** On-device recognizer to use. */
    val ocrScript: OcrScript

    /**
     * How the lookup shortens the text after the aim when a longer match fails: by words for languages written with
     * spaces ("look up to" → "look up" → "look"), by characters otherwise (Yomitan's search resolution).
     */
    val searchResolution: SearchResolution

    /** Whether lines run from right to left. */
    val rightToLeft: Boolean get() = false

    /** Characters that may end a sentence, for `{sentence}` and cloze markers; [endsSentence] has the final say. */
    val sentenceTerminators: Set<Char>

    /** Whether the character at [index] of [text] ends a sentence. */
    fun endsSentence(text: String, index: Int): Boolean = text[index] in sentenceTerminators

    /** Opening to closing brackets and quotes; a terminator inside them does not end the sentence. */
    val quotePairs: Map<Char, Char>

    /** Anki markers that exist only for this language (e.g. pitch accent), in addition to the common ones. */
    val ankiMarkers: List<String>

    /**
     * Anki markers ([ankiMarkers]) that spell the word in one of [scriptTags], to that script's tag:
     * `{expression-traditional}` for a note type with a field for each script.
     */
    val scriptMarkers: Map<String, String> get() = emptyMap()

    /**
     * Field names of note types made for the language, by the marker such a field is pre-filled with (Pinyin →
     * `reading`); they come before the names every language shares.
     */
    val ankiFieldNames: Map<String, List<String>> get() = emptyMap()

    /** Default audio sources, as `AudioSourceType` names, in priority order. */
    val defaultAudioSources: List<String>

    /** ISO 639-3 code, used by Lingua Libre recordings. */
    val iso639Part3: String

    /** Wikidata item of the language, used by Lingua Libre recordings. */
    val wikidataId: String

    /**
     * The spelling Wiktionary's recordings of a word ([term] read as [reading]) are titled by: the word itself, or its
     * pinyin for Chinese (`Zh-péngyou.ogg`).
     */
    fun recordingTitle(term: String, reading: String): String = term

    /** What text to speech reads for a word: its reading where a voice reads it as the word (kana), else the word. */
    fun speechText(term: String, reading: String): String = reading.ifEmpty { term }

    /** BCP 47 tag of the language, for `lang` attributes and locale-dependent glyph forms. */
    val languageTag: String

    /** The phone's fonts for the language, used by the lookup page unless the user picks another font. */
    val systemFonts: SystemFonts

    /** Sample text for font previews, with characters whose forms differ between fonts or regions. */
    val fontSample: String

    /** A short sentence for the translation services' test. */
    val translationSample: String

    /** A common word for the audio sources' test. */
    val audioTestWord: String

    /**
     * Countries whose round flag may stand for the language (ISO 3166-1 alpha-2, lower case), the default first; the
     * Languages screen offers the others. Each has a `flag_<country>` drawable.
     */
    val flagCountries: List<String>

    /** Regions of Wiktionary recordings in their default order; empty where the file titles name none. */
    val audioRegions: List<AudioRegion> get() = emptyList()

    /**
     * Accent names in the transcription tags of Wiktionary dictionaries (`General-American`) and the regions they
     * stand for (`us`); flag emoji need no entry (see [TranscriptionRegions]).
     */
    val transcriptionRegionNames: Map<String, String> get() = emptyMap()

    /**
     * A word with its transcription, the example that explains pronunciation dictionaries (`water → /ˈwɔːtər/`); null
     * where they hold something else (pitch accents for Japanese).
     */
    val pronunciationSample: String? get() = null

    /**
     * Whether a dictionary word may start at the beginning of [text] (the text from the aim point on), and which
     * matches count; null when there is nothing to look up.
     *
     * @param latinAsNative Latin text is converted by [fromLatin] before the lookup.
     */
    fun lookupStart(text: String, latinAsNative: Boolean): LookupStart?

    /**
     * How many characters (code points) before the aimed one the lookup starts, so a word the language reads as a
     * whole is looked up from its beginning; [before] is the text before the aimed character, [aimed] that character.
     */
    fun wordStartOffset(before: String, aimed: String): Int

    /** Other spellings of [text] that are looked up as well; the original is always looked up. */
    fun spellingVariants(text: MappedText): List<MappedText>

    /** Converts a transliteration in Latin letters into the language's script; null if there is nothing to convert. */
    fun fromLatin(text: MappedText): MappedText?

    /** Characters of a matched word that get entries of their own below the results (kanji for Japanese). */
    fun singleCharacterEntries(matched: String): List<String>

    /** Whether [singleCharacterEntries] gives any, which the lookup settings offer to turn off. */
    val hasCharacterEntries: Boolean get() = false

    /** Whether [fromLatin] converts anything, which the lookup settings offer to turn on (romaji for Japanese). */
    val convertsLatin: Boolean get() = false

    /**
     * The first character of [text] when kanji dictionaries describe such characters (a kanji for Japanese), for the
     * kanji entry shown when no word is found there; null otherwise.
     */
    fun characterEntry(text: String): String?

    /**
     * BCP 47 tags of the scripts the language is written in, where it has several (Simplified and Traditional
     * Chinese); empty otherwise. Each result of a lookup is tagged with one of them.
     */
    val scriptTags: List<String> get() = emptyList()

    /** The script ([scriptTags]) [text] is written in; null where it reads the same in each. */
    fun scriptOf(text: String): String? = null

    /**
     * [text] written in the script with [tag] ([scriptTags]), one spelling per likely conversion, the most likely
     * first; empty when nothing in it changes.
     */
    fun inScript(text: MappedText, tag: String): List<MappedText> = emptyList()

    /** The script for words written alike in each that an interface language ([locale]) suggests. */
    fun defaultScriptTag(locale: Locale): String? = scriptTags.firstOrNull()

    /**
     * Tags of form-of entries that only spell a word in another script (Wiktionary's `Simplified-Chinese`); such a
     * result is shown under the spelling that was read.
     */
    val spellingFormTags: Set<String> get() = emptySet()

    /** A dictionary's reading as entries show it, without notations it adds ("xíng (xing²)" → "xíng"). */
    fun displayReading(reading: String): String = reading

    /**
     * The form of a reading ([displayReading]) under which results of one word join, as Yomitan's reading normalizer
     * groups them: pinyin "nǐ hǎo" and "nǐhǎo" are one reading.
     */
    fun normalizeReading(reading: String): String = reading

    /** Whether a typed search also finds words by their reading in Latin letters (pinyin), see [readingKey]. */
    val readingSearch: Boolean get() = false

    /**
     * What a typed reading and a dictionary's reading ([displayReading]) of the same word share for [readingSearch]:
     * for pinyin, its letters without tones, spaces or apostrophes. Null when [reading] is not one.
     */
    fun readingKey(reading: String): String? = null

    /**
     * How well what [query] types beyond the [readingKey] agrees with [reading] of the same key: for pinyin, one point
     * for each tone and syllable break that matches, one off for each that does not; 0 when the query has none.
     */
    fun readingMatch(query: String, reading: String): Int = 0
}

/** Which matches a lookup accepts. */
sealed interface LookupStart {
    /** Any match from the start of the text, the longest first. */
    data object Any : LookupStart

    /** Only matches of exactly [length] UTF-16 characters: a word that counts only as a whole. */
    data class Whole(val length: Int) : LookupStart
}

enum class FontStyle { SANS, SERIF }

/**
 * @property sans names of the phone's sans-serif font for the language as CSS `local()` takes them (PostScript or
 * full names), most specific first.
 * @property serif the same for the serif font.
 * @property unicodeRange CSS `unicode-range` of the language's script, so other text keeps the default font.
 * @property aliases font names common on other systems (e.g. Windows) that stand for these fonts in user CSS.
 */
data class SystemFonts(
    val sans: List<String>,
    val serif: List<String>,
    val unicodeRange: String,
    val aliases: Map<String, FontStyle>,
)

enum class SearchResolution { WORD, LETTER }

/** On-device OCR models; each one covers a script family. */
enum class OcrScript { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN }

val Language.support: LanguageSupport
    get() = when (this) {
        Language.JAPANESE -> JapaneseSupport
        Language.ENGLISH -> EnglishSupport
        Language.CHINESE -> ChineseSupport
        Language.KOREAN -> KoreanSupport
        Language.SPANISH -> SpanishSupport
    }
