package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Korean. Words are separated by spaces, but particles and endings join the word they follow (한국어를, 먹었습니다), so
 * the lookup shortens the text letter by letter as Yomitan does; Yomitan splits Hangul into jamo, deinflects on jamo
 * and joins them back in the engine. Unlike Yomitan, the lookup starts at the beginning of the spaced word under the
 * aim, wherever in it the aim is.
 */
object KoreanSupport : LanguageSupport {
    override val language = Language.KOREAN
    override val glyph = "한"
    override val wordSeparator = " "

    /** Korean text breaks a word at any syllable at a line end, without a hyphen. */
    override val lineEndSeparator = ""
    override val ocrScript = OcrScript.KOREAN
    override val flagCountries = listOf("kr")
    override val searchResolution = SearchResolution.LETTER

    /** Hanja in a word written in characters (漢字) get entries of their own, from the character dictionary. */
    override val hasCharacterEntries = true
    override val sentenceTerminators = setOf('.', '!', '?', '…', '。', '\n')
    override val quotePairs = mapOf(
        '“' to '”', '‘' to '’', '「' to '」', '『' to '』', '《' to '》', '〈' to '〉', '(' to ')', '[' to ']',
    )
    override val ankiMarkers: List<String> = emptyList()

    /** Fields of common Korean note types: Korean, Hangul, English. */
    override val ankiFieldNames = mapOf(
        "expression" to listOf("korean", "hangul", "한국어", "한글", "단어"),
        "glossary" to listOf("english", "뜻"),
    )

    /** Wiktionary first: more common words have a recording there than on Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
    override val wikidataId = "Q9176"

    /** The word: a voice reads Hangul as it is. */
    override fun speechText(term: String, reading: String): String = term

    override val languageTag = "ko"
    override val fontSample = "다람쥐 헌 쳇바퀴에 타고파 · 漢字"
    override val translationSample = "오늘은 날씨가 좋네요. 산책하러 갈까요?"
    override val audioTestWord = "사람"
    override val pronunciationSample = "한국어 → [ha(ː)n.ɡu.ɡʌ]"

    /** Noto Sans/Serif CJK KR, which Android ships; Hanja take their Korean forms from the same face. */
    override val systemFonts = SystemFonts(
        sans = listOf("NotoSansCJKkr-Regular", "Noto Sans CJK KR Regular", "NotoSansKR-Regular", "Noto Sans KR Regular"),
        serif = listOf("NotoSerifCJKkr-Regular", "Noto Serif CJK KR", "NotoSerifKR-Regular", "Noto Serif KR Regular"),
        unicodeRange = "U+1100-11FF, U+2E80-2FDF, U+3000-303F, U+3130-318F, U+3200-9FFF, U+A960-A97F, U+AC00-D7FF, " +
            "U+F900-FAFF, U+FE10-FE1F, U+FE30-FE4F, U+FF00-FFEF, U+20000-2FA1F",
        aliases = listOf(
            "Malgun Gothic", "맑은 고딕", "Gulim", "굴림", "Dotum", "돋움", "Apple SD Gothic Neo", "AppleGothic",
            "Nanum Gothic", "나눔고딕", "NanumGothic", "Noto Sans KR", "Noto Sans CJK KR", "Source Han Sans K",
            "Source Han Sans KR", "Pretendard",
        ).associateWith { FontStyle.SANS } + listOf(
            "Batang", "바탕", "Gungsuh", "궁서", "AppleMyungjo", "Nanum Myeongjo", "나눔명조", "NanumMyeongjo",
            "Noto Serif KR", "Noto Serif CJK KR", "Source Han Serif K", "Source Han Serif KR",
        ).associateWith { FontStyle.SERIF },
    )

    /**
     * A period ends the sentence unless a letter or digit follows it at once (3.14, example.com); the other
     * terminators always do.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        return character != '.' || index + 1 >= text.length || !text[index + 1].isLetterOrDigit()
    }

    /** Hangul and Hanja start a word, and so do Latin letters and digits that Korean follows directly (3월, TV를). */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        if (isKoreanLetter(text.codePointAt(0))) return LookupStart.Any
        return CjkText.latinStart(text, ::isKoreanLetter)
    }

    /**
     * The spaced word's letters before the aimed one: a finger often lands on a neighboring syllable, and a start
     * mid-word finds endings or other words (부하 in 공부하고). Latin letters belong to the word (TV를); digits do not,
     * since dictionaries list 월 but rarely 3월. Latin words are looked up from their start.
     */
    override fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty()) return 0
        if (!isKoreanLetter(aimed.codePointAt(0))) return CjkText.latinWordStartOffset(before, aimed)
        var index = before.length
        var count = 0
        while (index > 0) {
            val codePoint = before.codePointBefore(index)
            if (!isKoreanLetter(codePoint) && !CjkText.isLatinLetter(codePoint)) break
            index -= Character.charCount(codePoint)
            count++
        }
        return count
    }

    override fun spellingVariants(text: MappedText): List<MappedText> = emptyList()

    override fun fromLatin(text: MappedText): MappedText? = null

    override fun singleCharacterEntries(matched: String): List<String> = CjkText.laterCharacters(matched, CjkText::isHan)

    override fun characterEntry(text: String): String? {
        if (text.isEmpty()) return null
        val codePoint = text.codePointAt(0)
        return if (CjkText.isHan(codePoint)) String(Character.toChars(codePoint)) else null
    }

    /** Hangul syllables and jamo, and Hanja. */
    private fun isKoreanLetter(codePoint: Int): Boolean =
        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL || CjkText.isHan(codePoint)
}
