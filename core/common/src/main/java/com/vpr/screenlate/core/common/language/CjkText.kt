package com.vpr.screenlate.core.common.language

/** Character classes of text written without spaces (Japanese, Chinese), and the Latin words inside it. */
internal object CjkText {
    /** CJK ideographs: the unified blocks, their extensions and the compatibility ideographs. */
    fun isHan(codePoint: Int): Boolean =
        codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2FA1F

    /**
     * Kangxi radicals and the CJK radicals supplement: look-alikes of ideographs (⽇ for 日) that text copied from PDFs
     * often has. The lookup reads them as their ideographs (Yomitan's radical normalization).
     */
    fun isRadical(codePoint: Int): Boolean = codePoint in 0x2E80..0x2EF3 || codePoint in 0x2F00..0x2FD5

    fun isLatinLetter(codePoint: Int): Boolean =
        codePoint in 'a'.code..'z'.code ||
            codePoint in 'A'.code..'Z'.code ||
            codePoint in 0xFF21..0xFF3A ||
            codePoint in 0xFF41..0xFF5A

    fun isLatinOrDigit(codePoint: Int): Boolean =
        isLatinLetter(codePoint) || codePoint in '0'.code..'9'.code || codePoint in 0xFF10..0xFF19

    /**
     * A Latin word at the start of [text]: one followed directly by the language's own script starts a word (Tシャツ,
     * T恤); one on its own counts only as a whole and with at least two characters, one of them a letter, since
     * dictionaries list single letters. Null when [text] does not start with a Latin letter or digit.
     */
    fun latinStart(text: String, isNative: (Int) -> Boolean): LookupStart? {
        if (text.isEmpty() || !isLatinOrDigit(text.codePointAt(0))) return null
        val word = latinWordLength(text)
        if (word < text.length && isNative(text.codePointAt(word))) return LookupStart.Any
        val letters = text.substring(0, word).count { isLatinLetter(it.code) }
        return if (letters > 0 && word >= 2) LookupStart.Whole(word) else null
    }

    /**
     * Whether the character at [index] ends the sentence: one of [terminators], except a full-width period between
     * digits, which is a decimal point (３．１４).
     */
    fun endsSentence(text: String, index: Int, terminators: Set<Char>): Boolean {
        val character = text[index]
        if (character !in terminators) return false
        if (character != '．' || index == 0 || index + 1 >= text.length) return true
        return !(isDigit(text[index - 1].code) && isDigit(text[index + 1].code))
    }

    private fun isDigit(codePoint: Int): Boolean = codePoint in '0'.code..'9'.code || codePoint in 0xFF10..0xFF19

    /** A Latin word is looked up from its first character wherever the aim is inside it. */
    fun latinWordStartOffset(before: String, aimed: String): Int {
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
     * The characters of [matched] after its first that [isCharacter] accepts, each once: the first one is already
     * among the results as a word of its own.
     */
    fun laterCharacters(matched: String, isCharacter: (Int) -> Boolean): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < matched.length) {
            val codePoint = matched.codePointAt(index)
            if (index > 0 && isCharacter(codePoint)) {
                val character = String(Character.toChars(codePoint))
                if (character !in result) result += character
            }
            index += Character.charCount(codePoint)
        }
        return result
    }
}
