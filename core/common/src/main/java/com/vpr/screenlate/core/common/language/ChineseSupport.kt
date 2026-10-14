package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Chinese in both scripts: one profile covers Simplified and Traditional text. */
object ChineseSupport : LanguageSupport {
    override val language = Language.CHINESE
    override val glyph = "中"
    override val wordSeparator = ""
    override val ocrScript = OcrScript.CHINESE
    override val flagCountries = listOf("cn", "tw", "hk", "sg")
    override val searchResolution = SearchResolution.LETTER
    override val hasCharacterEntries = true
    override val sentenceTerminators = setOf('。', '！', '？', '!', '?', '．', '…', '\n')
    override val quotePairs = mapOf(
        '“' to '”', '‘' to '’', '「' to '」', '『' to '』', '《' to '》', '〈' to '〉', '（' to '）', '(' to ')', '【' to '】',
    )
    override val ankiMarkers: List<String> = emptyList()

    /** Recordings of words first, then the phone's text to speech. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Lingua Libre files Mandarin under its own item, `LL-Q9192 (cmn)`. */
    override val iso639Part3 = "cmn"
    override val wikidataId = "Q9192"

    /** Simplified; a match in Traditional characters sets its own tag where the text is shown. */
    override val languageTag = "zh-Hans"
    override val fontSample = "汉字 漢字 · 骨 直 角 · 你好，世界"
    override val translationSample = "今天天气很好。我们去散步吧。"
    override val audioTestWord = "你好"

    /**
     * Noto Sans/Serif CJK SC, which Android ships. The faces carry the other regions' glyphs as well and pick them by
     * the text's language, so Traditional text tagged `zh-Hant` gets its own forms from the same face.
     */
    override val systemFonts = SystemFonts(
        sans = listOf("NotoSansCJKsc-Regular", "Noto Sans CJK SC Regular", "NotoSansSC-Regular", "Noto Sans SC Regular"),
        serif = listOf("NotoSerifCJKsc-Regular", "Noto Serif CJK SC", "NotoSerifSC-Regular", "Noto Serif SC Regular"),
        unicodeRange = "U+2E80-2FDF, U+3000-303F, U+3100-312F, U+31A0-31BF, U+3200-9FFF, U+F900-FAFF, " +
            "U+FE10-FE1F, U+FE30-FE4F, U+FF00-FFEF, U+20000-2FA1F",
        aliases = listOf(
            "Microsoft YaHei", "微软雅黑", "Microsoft YaHei UI", "Microsoft JhengHei", "微軟正黑體", "SimHei", "黑体",
            "PingFang SC", "PingFang TC", "PingFang HK", "Heiti SC", "Heiti TC", "Hiragino Sans GB", "Noto Sans SC",
            "Noto Sans TC", "Noto Sans CJK SC", "Noto Sans CJK TC", "Source Han Sans SC", "Source Han Sans TC",
            "Source Han Sans CN", "Source Han Sans TW", "WenQuanYi Micro Hei",
        ).associateWith { FontStyle.SANS } + listOf(
            "SimSun", "宋体", "NSimSun", "新宋体", "MingLiU", "細明體", "PMingLiU", "新細明體", "Songti SC", "Songti TC",
            "STSong", "Noto Serif SC", "Noto Serif TC", "Noto Serif CJK SC", "Noto Serif CJK TC", "Source Han Serif SC",
            "Source Han Serif TC", "Source Han Serif CN", "Source Han Serif TW",
        ).associateWith { FontStyle.SERIF },
    )

    /** Chinese characters start a word, and so do Latin letters and digits that Chinese follows directly (T恤, 3月). */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        if (isChineseLetter(text.codePointAt(0))) return LookupStart.Any
        return CjkText.latinStart(text, ::isChineseLetter)
    }

    override fun wordStartOffset(before: String, aimed: String): Int = CjkText.latinWordStartOffset(before, aimed)

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = CjkText.laterCharacters(matched, CjkText::isHan)

    override fun characterEntry(text: String): String? {
        if (text.isEmpty()) return null
        val codePoint = text.codePointAt(0)
        return if (CjkText.isHan(codePoint)) String(Character.toChars(codePoint)) else null
    }

    /** Hanzi and 〇, the zero of numbers written in characters (二〇二四年). */
    private fun isChineseLetter(codePoint: Int): Boolean = CjkText.isHan(codePoint) || codePoint == 0x3007
}
