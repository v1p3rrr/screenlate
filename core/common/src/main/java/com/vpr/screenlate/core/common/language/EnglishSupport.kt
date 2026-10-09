package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Experiment: English with Yomitan's text processors and transforms doing case and inflections in the engine. */
object EnglishSupport : LanguageSupport {
    override val language = Language.ENGLISH
    override val glyph = "A"
    override val wordSeparator = " "
    override val ocrScript = OcrScript.LATIN
    override val sentenceTerminators = setOf('.', '!', '?', '…', '\n')
    override val quotePairs = mapOf('"' to '"', '“' to '”', '‘' to '’', '(' to ')', '[' to ']')
    override val ankiMarkers = emptyList<String>()
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY")
    override val iso639Part3 = "eng"
    override val wikidataId = "Q1860"
    override val languageTag = "en"
    override val fontSample = "The quick brown fox jumps over the lazy dog"
    override val translationSample = "The weather is nice today. Let's go for a walk."

    override val systemFonts = SystemFonts(
        sans = listOf("Roboto-Regular", "Roboto"),
        serif = listOf("NotoSerif-Regular", "Noto Serif"),
        unicodeRange = "U+0000-024F",
        aliases = emptyMap(),
    )

    /** A word starts with a letter or digit; phrases and inflections are tried by the engine. */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        return if (Character.isLetterOrDigit(text.codePointAt(0))) LookupStart.Any else null
    }

    /** The whole word under the aim, as Yomitan's word scan resolution. */
    override fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty() || !Character.isLetterOrDigit(aimed.codePointAt(0))) return 0
        var index = before.length
        var count = 0
        while (index > 0) {
            val c = before[index - 1]
            val inWord = Character.isLetterOrDigit(c) ||
                (c in WORD_CONNECTORS && index > 1 && Character.isLetterOrDigit(before[index - 2]))
            if (!inWord) break
            index--
            count++
        }
        return count
    }

    private const val WORD_CONNECTORS = "'’-"

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null
}
