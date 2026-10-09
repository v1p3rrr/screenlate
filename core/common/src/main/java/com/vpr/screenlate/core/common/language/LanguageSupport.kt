package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

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

    /** Characters that end a sentence, for `{sentence}` and cloze markers. */
    val sentenceTerminators: Set<Char>

    /** Opening to closing brackets and quotes; a terminator inside them does not end the sentence. */
    val quotePairs: Map<Char, Char>

    /** Anki markers that exist only for this language (e.g. pitch accent), in addition to the common ones. */
    val ankiMarkers: List<String>

    /** Default audio sources, as `AudioSourceType` names, in priority order. */
    val defaultAudioSources: List<String>

    /** ISO 639-3 code, used by Lingua Libre recordings. */
    val iso639Part3: String

    /** Wikidata item of the language, used by Lingua Libre recordings. */
    val wikidataId: String

    /** BCP 47 tag of the language, for `lang` attributes and locale-dependent glyph forms. */
    val languageTag: String

    /** The phone's fonts for the language, used by the lookup page unless the user picks another font. */
    val systemFonts: SystemFonts

    /** Sample text for font previews, with characters whose forms differ between fonts or regions. */
    val fontSample: String

    /** A short sentence for the translation services' test. */
    val translationSample: String

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

    /**
     * The first character of [text] when kanji dictionaries describe such characters (a kanji for Japanese), for the
     * kanji entry shown when no word is found there; null otherwise.
     */
    fun characterEntry(text: String): String?
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

/** On-device OCR models; each one covers a script family. */
enum class OcrScript { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN }

val Language.support: LanguageSupport
    get() = when (this) {
        Language.JAPANESE -> JapaneseSupport
    }
