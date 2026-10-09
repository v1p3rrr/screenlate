package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Experiment: Chinese (simplified and traditional in one profile), Yomitan's text processors in the engine. */
object ChineseSupport : LanguageSupport {
    override val language = Language.CHINESE
    override val glyph = "中"
    override val wordSeparator = ""
    override val ocrScript = OcrScript.CHINESE
    override val sentenceTerminators = setOf('。', '！', '？', '!', '?', '…', '\n')
    override val quotePairs = mapOf('“' to '”', '‘' to '’', '「' to '」', '『' to '』', '（' to '）', '(' to ')', '《' to '》')
    override val ankiMarkers = emptyList<String>()
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY")
    override val iso639Part3 = "cmn"
    override val wikidataId = "Q727694"
    override val languageTag = "zh"
    override val fontSample = "门户 骨 直 — 門戶 骨 直"
    override val translationSample = "今天天气很好。我们去散步吧。"

    override val systemFonts = SystemFonts(
        sans = listOf("NotoSansCJKsc-Regular", "Noto Sans CJK SC Regular", "NotoSansSC-Regular", "Noto Sans SC Regular"),
        serif = listOf("NotoSerifCJKsc-Regular", "Noto Serif CJK SC", "NotoSerifSC-Regular", "Noto Serif SC Regular"),
        unicodeRange = "U+2E80-2FDF, U+3000-303F, U+3100-312F, U+31A0-31BF, U+3200-9FFF, U+F900-FAFF, U+FF00-FFEF, U+20000-2FA1F",
        aliases = emptyMap(),
    )

    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        return if (Character.UnicodeScript.of(text.codePointAt(0)) == Character.UnicodeScript.HAN) LookupStart.Any else null
    }

    override fun wordStartOffset(before: String, aimed: String): Int = 0

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = emptyList()

    override fun characterEntry(text: String): String? = null
}
