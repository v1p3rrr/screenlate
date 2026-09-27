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

    /**
     * Japanese text starts a word. Latin letters and digits count only when Japanese follows them directly, as in
     * Tシャツ or ３月, so English words and romanized names in Japanese text are not looked up, unless romaji is
     * converted.
     */
    override fun isLookupStart(text: String, latinAsNative: Boolean): Boolean {
        if (text.isEmpty()) return false
        val first = text.codePointAt(0)
        if (isJapaneseLetter(first)) return true
        if (!isLatinOrDigit(first)) return false
        if (latinAsNative && isLatinLetter(first)) return true
        var index = 0
        while (index < text.length && isLatinOrDigit(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index))
        }
        return index < text.length && isJapaneseLetter(text.codePointAt(index))
    }

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
