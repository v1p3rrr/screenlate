package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Experiment: Russian. Yomitan has no transforms for it: case, ё/е and stress marks are text processors in the
 * engine, and inflected forms come from the dictionary's form-of entries.
 */
object RussianSupport : LanguageSupport {
    override val language = Language.RUSSIAN
    override val glyph = "Я"
    override val wordSeparator = " "
    override val ocrScript = OcrScript.LATIN
    override val sentenceTerminators = setOf('.', '!', '?', '…', '\n')
    override val quotePairs = mapOf('«' to '»', '„' to '“', '"' to '"', '(' to ')')
    override val ankiMarkers = emptyList<String>()
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY")
    override val iso639Part3 = "rus"
    override val wikidataId = "Q7737"
    override val languageTag = "ru"
    override val fontSample = "Съешь же ещё этих мягких французских булок"
    override val translationSample = "Сегодня хорошая погода. Пойдём гулять."

    override val systemFonts = SystemFonts(
        sans = listOf("Roboto-Regular", "Roboto"),
        serif = listOf("NotoSerif-Regular", "Noto Serif"),
        unicodeRange = "U+0301, U+0400-052F, U+1C80-1C8F, U+2DE0-2DFF, U+A640-A69F",
        aliases = emptyMap(),
    )

    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? = SpacedWords.lookupStart(text)

    override fun wordStartOffset(before: String, aimed: String): Int = SpacedWords.wordStartOffset(before, aimed)

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null
}
