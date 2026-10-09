package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Experiment: Korean. Yomitan splits Hangul into jamo, deinflects on jamo and joins them back in the engine; the lookup
 * goes letter by letter inside the spaced word, as in Yomitan.
 */
object KoreanSupport : LanguageSupport {
    override val language = Language.KOREAN
    override val glyph = "한"
    override val wordSeparator = " "
    override val ocrScript = OcrScript.KOREAN
    override val sentenceTerminators = setOf('.', '!', '?', '…', '。', '\n')
    override val quotePairs = mapOf('"' to '"', '“' to '”', '‘' to '’', '「' to '」', '『' to '』', '(' to ')')
    override val ankiMarkers = emptyList<String>()
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY")
    override val iso639Part3 = "kor"
    override val wikidataId = "Q9176"
    override val languageTag = "ko"
    override val fontSample = "다람쥐 헌 쳇바퀴에 타고파 — 漢字"
    override val translationSample = "오늘은 날씨가 좋네요. 산책하러 갈까요?"

    override val systemFonts = SystemFonts(
        sans = listOf("NotoSansCJKkr-Regular", "Noto Sans CJK KR Regular", "NotoSansKR-Regular", "Noto Sans KR Regular"),
        serif = listOf("NotoSerifCJKkr-Regular", "Noto Serif CJK KR", "NotoSerifKR-Regular", "Noto Serif KR Regular"),
        unicodeRange = "U+1100-11FF, U+3000-303F, U+3130-318F, U+3200-32FF, U+4E00-9FFF, U+A960-A97F, U+AC00-D7FF, U+F900-FAFF, U+FF00-FFEF",
        aliases = emptyMap(),
    )

    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        val script = Character.UnicodeScript.of(text.codePointAt(0))
        return if (script == Character.UnicodeScript.HANGUL || script == Character.UnicodeScript.HAN) LookupStart.Any else null
    }

    override fun wordStartOffset(before: String, aimed: String): Int = 0

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null
}
