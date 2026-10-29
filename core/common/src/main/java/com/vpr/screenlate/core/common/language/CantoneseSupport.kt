package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Cantonese: Chinese's text rules and script conversion, Jyutping readings, Hong Kong glyph forms (`yue-HK`) and
 * Traditional characters by default, as Hong Kong text and words.hk's headwords are written.
 */
object CantoneseSupport : LanguageSupport {
    override val language = Language.CANTONESE
    override val glyph = "粵"
    override val wordSeparator = ""
    override val ocrScript = OcrScript.CHINESE
    override val flagCountries = listOf("hk")
    override val searchResolution = SearchResolution.LETTER
    override val hasCharacterEntries = true
    override val furiganaMarkers = true
    override val sentenceTerminators = ChineseSupport.sentenceTerminators
    override val quotePairs = ChineseSupport.quotePairs

    override fun endsSentence(text: String, index: Int): Boolean = ChineseSupport.endsSentence(text, index)

    /** The word in Simplified and in Traditional characters, for note types with a field of each. */
    override val scriptMarkers = mapOf("expression-simplified" to SIMPLIFIED, "expression-traditional" to TRADITIONAL)
    override val ankiMarkers: List<String> = scriptMarkers.keys.toList()

    /** Fields of common Cantonese note types: Characters, Simplified, Traditional, Jyutping, English. */
    override val ankiFieldNames = mapOf(
        "expression-simplified" to listOf("simplified", "简体", "簡體"),
        "expression-traditional" to listOf("traditional", "繁体", "繁體"),
        "expression" to listOf("hanzi", "cantonese", "characters", "汉字", "漢字", "粵語", "粤语", "廣東話"),
        "reading" to listOf("jyutping", "粵拼", "粤拼"),
        "glossary" to listOf("english"),
    )

    /**
     * Lingua Libre first: more common words have a recording there (17 of 20 against Wiktionary's 8), titled by the
     * characters alone.
     */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** Lingua Libre files Cantonese under its own item, `LL-Q9186 (yue)`. */
    override val wikidataId = "Q9186"

    /** The reading in Yale, as Wiktionary titles Cantonese recordings (`Yue-ngo5dei6.ogg`), or else the word. */
    override fun recordingTitle(term: String, reading: String): String = CantoneseJyutping.yale(reading) ?: term

    /** `Yue-` recordings titled in Yale, then `Zh-yue-` ones titled by the characters (`Zh-yue-我哋.ogg`). */
    override fun recordings(term: String, reading: String): List<RecordingTitle> = listOfNotNull(
        CantoneseJyutping.yale(reading)?.let { RecordingTitle(language.dictionaryCode, it) },
        RecordingTitle(ChineseSupport.language.dictionaryCode, "${language.dictionaryCode}-$term"),
    )

    /** The characters: a voice reads Jyutping as Latin letters. */
    override fun speechText(term: String, reading: String): String = term

    /** Hong Kong's: HarfBuzz gives the system CJK face's Hong Kong forms for it (`zh-Hant` would give Taiwan's). */
    override val languageTag = TRADITIONAL
    override val speechTag = "yue-HK"
    override val otherSpeechTags = listOf("zh-HK")
    override val fontSample = "粵語 廣東話 · 骨 直 角 · 我哋喺度食飯"
    override val translationSample = "今日天氣好好。我哋去行街啦。"
    override val audioTestWord = "你好"

    /**
     * Noto Sans/Serif CJK, which Android ships: each face carries the Hong Kong forms as well and picks them for text
     * tagged [languageTag].
     */
    override val systemFonts = SystemFonts(
        sans = listOf(
            "NotoSansCJKtc-Regular", "Noto Sans CJK TC Regular", "NotoSansCJKsc-Regular", "Noto Sans CJK SC Regular",
            "NotoSansTC-Regular", "Noto Sans TC Regular",
        ),
        serif = listOf(
            "NotoSerifCJKtc-Regular", "Noto Serif CJK TC", "NotoSerifCJKsc-Regular", "Noto Serif CJK SC",
            "NotoSerifTC-Regular", "Noto Serif TC Regular",
        ),
        unicodeRange = ChineseSupport.systemFonts.unicodeRange,
        aliases = ChineseSupport.systemFonts.aliases,
    )

    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? = ChineseSupport.lookupStart(text, latinAsNative)

    override fun wordStartOffset(before: String, aimed: String): Int = ChineseSupport.wordStartOffset(before, aimed)

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = ChineseSupport.singleCharacterEntries(matched)

    override fun characterEntry(text: String): String? = ChineseSupport.characterEntry(text)

    override val scriptTags = listOf(SIMPLIFIED, TRADITIONAL)

    override fun scriptOf(text: String): String? = when (ChineseScripts.scriptOf(text)) {
        ChineseScripts.SIMPLIFIED -> SIMPLIFIED
        ChineseScripts.TRADITIONAL -> TRADITIONAL
        else -> null
    }

    override fun inScript(text: MappedText, tag: String): List<MappedText> =
        ChineseScripts.convert(text, if (tag == SIMPLIFIED) ChineseScripts.SIMPLIFIED else ChineseScripts.TRADITIONAL)

    /**
     * The word in the other script, and in the Hong Kong and Taiwan forms of its variant characters ([CantoneseVariants]):
     * rime-cantonese's list writes OpenCC's forms (爲, 衛), words.hk Hong Kong's (為, 衞).
     */
    override fun frequencySpellings(word: String): List<String> =
        (listOf(word) + CantoneseVariants.spellings(word)).flatMap { spelling ->
            listOf(spelling) + scriptTags.flatMap { tag -> inScript(MappedText.identity(spelling), tag).map { it.text } }
        }.distinct().filter { it != word }

    /** Traditional whatever the interface language: Hong Kong text and words.hk's headwords are written so. */
    override fun defaultScriptTag(locale: Locale): String = TRADITIONAL

    /** In lower case without spaces: dictionaries space the syllables ("nei5 hou2"), typed queries may not. */
    override fun normalizeReading(reading: String): String = reading.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }

    /** Jyutping typed with tone digits (nei5hou2) or without (neihou). */
    override val readingSearch = true

    override fun readingKey(reading: String): String? = CantoneseJyutping.key(reading)

    override fun readingMatch(query: String, reading: String): Int = CantoneseJyutping.match(query, reading)

    /** Simplified characters, tagged so that the page reads their Jyutping by Cantonese rules. */
    const val SIMPLIFIED = "yue-Hans"

    /** Traditional characters in their Hong Kong forms. */
    const val TRADITIONAL = "yue-HK"
}
