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

    /**
     * Whether a dictionary word may start at the beginning of [text] (the text from the aim point on).
     *
     * @param latinAsNative Latin text is converted by [fromLatin] before the lookup.
     */
    fun isLookupStart(text: String, latinAsNative: Boolean): Boolean

    /** Other spellings of [text] that are looked up as well; the original is always looked up. */
    fun spellingVariants(text: MappedText): List<MappedText>

    /** Converts a transliteration in Latin letters into the language's script; null if there is nothing to convert. */
    fun fromLatin(text: MappedText): MappedText?

    /** Characters of a matched word that get entries of their own below the results (kanji for Japanese). */
    fun singleCharacterEntries(matched: String): List<String>
}

/** On-device OCR models; each one covers a script family. */
enum class OcrScript { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN }

val Language.support: LanguageSupport
    get() = when (this) {
        Language.JAPANESE -> JapaneseSupport
    }
