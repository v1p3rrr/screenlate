package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object JapaneseSupport : LanguageSupport {
    override val language = Language.JAPANESE
    override val glyph = "あ"
    override val wordSeparator = ""
    override val ocrScript = OcrScript.JAPANESE
    override val sentenceTerminators = setOf('。', '！', '？', '!', '?', '．', '…', '\n')
    override val quotePairs = mapOf('「' to '」', '『' to '』', '（' to '）', '(' to ')')

    override val ankiMarkers = listOf(
        "cloze-body-kana",
        "pitch-accents",
        "pitch-accent-graphs",
        "pitch-accent-graphs-jj",
        "pitch-accent-positions",
        "pitch-accent-categories",
    )

    override val defaultAudioSources = listOf("JAPANESE_POD_101", "LANGUAGE_POD_101", "JISHO")
    override val iso639Part3 = "jpn"
    override val wikidataId = "Q5287"
    override val languageTag = "ja"
    override val fontSample = "置く・直す・骨 — あいうえお アイウエオ"

    /**
     * Noto Sans/Serif CJK JP, which Android ships; asking for the Japanese face by name keeps Japanese glyph forms
     * even where the phone's main font covers Chinese. Windows and macOS Japanese fonts map to them.
     */
    override val systemFonts = SystemFonts(
        sans = listOf("NotoSansCJKjp-Regular", "Noto Sans CJK JP Regular", "NotoSansJP-Regular", "Noto Sans JP Regular"),
        serif = listOf("NotoSerifCJKjp-Regular", "Noto Serif CJK JP", "NotoSerifJP-Regular", "Noto Serif JP Regular"),
        unicodeRange = "U+2E80-2FDF, U+3000-30FF, U+31F0-31FF, U+3200-9FFF, U+F900-FAFF, U+FF00-FFEF, U+20000-2FA1F",
        aliases = listOf(
            "Meiryo", "メイリオ", "Meiryo UI", "Yu Gothic", "YuGothic", "游ゴシック", "Yu Gothic UI", "MS Gothic",
            "ＭＳ ゴシック", "MS PGothic", "ＭＳ Ｐゴシック", "MS UI Gothic", "Hiragino Sans", "Hiragino Kaku Gothic ProN",
            "Hiragino Kaku Gothic Pro", "ヒラギノ角ゴ ProN", "Osaka", "Noto Sans JP", "Noto Sans CJK JP",
            "Source Han Sans", "Source Han Sans JP", "IPAGothic", "IPAPGothic", "TakaoGothic",
        ).associateWith { FontStyle.SANS } + listOf(
            "Yu Mincho", "YuMincho", "游明朝", "MS Mincho", "ＭＳ 明朝", "MS PMincho", "ＭＳ Ｐ明朝", "Hiragino Mincho ProN",
            "Hiragino Mincho Pro", "ヒラギノ明朝 ProN", "Noto Serif JP", "Noto Serif CJK JP", "Source Han Serif",
            "Source Han Serif JP", "IPAMincho", "IPAPMincho",
        ).associateWith { FontStyle.SERIF },
    )

    /**
     * Japanese text starts a word, and so do Latin letters and digits that Japanese follows directly (Tシャツ, ３月,
     * ＣＤ-ＲＯＭドライブ). A Latin word on its own (ＯＬ, ＤＮＡ, Ｗｉ-Ｆｉ) counts only as a whole and with at least two
     * characters, one of them a letter: dictionaries list single letters, and English text should not bring up
     * entries for its first letters. Converted romaji takes any match.
     */
    override fun lookupStart(text: String, latinAsNative: Boolean): LookupStart? {
        if (text.isEmpty()) return null
        val first = text.codePointAt(0)
        if (isJapaneseLetter(first)) return LookupStart.Any
        if (!isLatinOrDigit(first)) return null
        if (latinAsNative && isLatinLetter(first)) return LookupStart.Any
        val word = latinWordLength(text)
        if (word < text.length && isJapaneseLetter(text.codePointAt(word))) return LookupStart.Any
        val letters = text.substring(0, word).count { isLatinLetter(it.code) }
        return if (letters > 0 && word >= 2) LookupStart.Whole(word) else null
    }

    /** A Latin word is looked up from its first character wherever the aim is inside it. */
    override fun wordStartOffset(before: String, aimed: String): Int {
        if (aimed.isEmpty() || !isLatinLetter(aimed.codePointAt(0))) return 0
        var index = before.length
        var count = 0
        while (index > 0) {
            val c = before[index - 1]
            val inWord = isLatinOrDigit(c.code) ||
                (c in WORD_CONNECTORS && index > 1 && isLatinOrDigit(before[index - 2].code))
            if (!inWord) break
            index--
            count++
        }
        return count
    }

    /** Length of the Latin word at the start of [text]: letters and digits, joined by [WORD_CONNECTORS]. */
    private fun latinWordLength(text: String): Int {
        var index = 0
        while (index < text.length) {
            val c = text[index]
            val inWord = isLatinOrDigit(c.code) ||
                (c in WORD_CONNECTORS && index > 0 && index + 1 < text.length && isLatinOrDigit(text[index + 1].code))
            if (!inWord) break
            index++
        }
        return index
    }

    /** Characters that join the parts of one Latin term: ＣＤ-ＲＯＭ, Ｍ＆Ａ, Ｉ／Ｏ. */
    private const val WORD_CONNECTORS = "-－.．&＆/／"

    /**
     * Romaji to hiragana with Mozc's input rules, longest rule first. `nn` before a vowel is read as ん + n
     * (konnichiha → こんにちは), and `-` becomes ー only after a letter.
     */
    override fun fromLatin(text: MappedText): MappedText? {
        val source = text.text
        if (source.none { isLatinLetter(it.code) }) return null
        val lower = String(CharArray(source.length) { asciiLower(source[it]) })
        val out = StringBuilder()
        val ends = ArrayList<Int>(source.length)
        var i = 0
        while (i < lower.length) {
            val c = lower[i]
            val convertible = c in 'a'..'z' || c == '\'' || (c == '-' && i > 0 && lower[i - 1] in 'a'..'z')
            if (!convertible) {
                out.append(source[i])
                ends += i + 1
                i++
                continue
            }
            if (c == 'n' && lower.getOrNull(i + 1) == 'n' && lower.getOrNull(i + 2)?.let { it in "aiueoy" } == true) {
                out.append('ん')
                ends += i + 1
                i++
                continue
            }
            var rule: MozcRomaji.Rule? = null
            var length = minOf(MozcRomaji.MAX_INPUT, lower.length - i)
            while (length > 0) {
                rule = MozcRomaji.RULES[lower.substring(i, i + length)]
                if (rule != null) break
                length--
            }
            if (rule == null) {
                out.append(source[i])
                ends += i + 1
                i++
                continue
            }
            val consumed = length - rule.pending.length
            rule.output.forEach {
                out.append(it)
                ends += i + consumed
            }
            i += consumed
        }
        return text.then(MappedText(out.toString(), ends.toIntArray()))
    }

    /**
     * One variant with all rules applied in order, a common set of Yomitan text replacements: digits 1–9 (ASCII and
     * full-width) to kanji numerals, a character followed by 々 doubled, ッ to っ, and ・、-. and whitespace removed.
     */
    override fun spellingVariants(text: MappedText): List<MappedText> {
        val replaced = SPELLING_RULES.fold(text) { current, (regex, replacement) -> current.replace(regex, replacement) }
        return if (replaced.text != text.text && replaced.text.isNotEmpty()) listOf(replaced) else emptyList()
    }

    private val SPELLING_RULES: List<Pair<Regex, String>> =
        "一二三四五六七八九".mapIndexed { index, numeral -> Regex("[${'1' + index}${'１' + index}]") to numeral.toString() } +
            listOf(
                Regex("(.)々") to "$1$1",
                Regex("ッ") to "っ",
                Regex("[・、\\-.\\s]") to "",
            )

    override fun singleCharacterEntries(matched: String): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < matched.length) {
            val codePoint = matched.codePointAt(index)
            if (index > 0 && isKanji(codePoint)) {
                val character = String(Character.toChars(codePoint))
                if (character !in result) result += character
            }
            index += Character.charCount(codePoint)
        }
        return result
    }

    override fun characterEntry(text: String): String? {
        if (text.isEmpty()) return null
        val codePoint = text.codePointAt(0)
        return if (isKanji(codePoint)) String(Character.toChars(codePoint)) else null
    }

    fun isKanji(codePoint: Int): Boolean =
        codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2FA1F

    private fun isKana(codePoint: Int): Boolean =
        codePoint in 0x3041..0x309F ||
            codePoint in 0x30A0..0x30FF ||
            codePoint in 0x31F0..0x31FF ||
            codePoint in 0xFF66..0xFF9F

    /** Kana, kanji and the marks 々〆〇. */
    private fun isJapaneseLetter(codePoint: Int): Boolean =
        isKana(codePoint) || isKanji(codePoint) || codePoint in 0x3005..0x3007

    private fun isLatinLetter(codePoint: Int): Boolean =
        codePoint in 'a'.code..'z'.code ||
            codePoint in 'A'.code..'Z'.code ||
            codePoint in 0xFF21..0xFF3A ||
            codePoint in 0xFF41..0xFF5A

    private fun isLatinOrDigit(codePoint: Int): Boolean =
        isLatinLetter(codePoint) || codePoint in '0'.code..'9'.code || codePoint in 0xFF10..0xFF19

    /** ASCII and full-width Latin letters to lower-case ASCII; other characters unchanged. */
    private fun asciiLower(c: Char): Char = when (c) {
        in 'A'..'Z' -> c + ('a' - 'A')
        in 'Ａ'..'Ｚ' -> 'a' + (c - 'Ａ')
        in 'ａ'..'ｚ' -> 'a' + (c - 'ａ')
        else -> c
    }
}
