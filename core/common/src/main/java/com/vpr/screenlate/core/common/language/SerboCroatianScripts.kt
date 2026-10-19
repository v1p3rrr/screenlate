package com.vpr.screenlate.core.common.language

import java.util.Locale

/**
 * Serbo-Croatian Latin letters written in Cyrillic, one Cyrillic letter for each Latin letter or digraph (lj → љ,
 * nj → њ, dž → џ). A digraph that stands for two letters across a prefix (nadživjeti, konjugacija) comes out as one,
 * as in any letter-by-letter conversion.
 */
object SerboCroatianScripts {
    private val letters = mapOf(
        'a' to 'а', 'b' to 'б', 'c' to 'ц', 'č' to 'ч', 'ć' to 'ћ', 'd' to 'д', 'đ' to 'ђ', 'e' to 'е', 'f' to 'ф',
        'g' to 'г', 'h' to 'х', 'i' to 'и', 'j' to 'ј', 'k' to 'к', 'l' to 'л', 'm' to 'м', 'n' to 'н', 'o' to 'о',
        'p' to 'п', 'r' to 'р', 's' to 'с', 'š' to 'ш', 't' to 'т', 'u' to 'у', 'v' to 'в', 'z' to 'з', 'ž' to 'ж',
        'ǆ' to 'џ', 'ǉ' to 'љ', 'ǌ' to 'њ',
    )

    private val digraphs = mapOf("lj" to 'љ', "nj" to 'њ', "dž" to 'џ')

    /**
     * [text] in Cyrillic; null when it has no Latin letter, or one outside the alphabet (q, w, x, y, ä), as foreign
     * words and names have. Cyrillic letters, marks and other characters stay.
     */
    fun toCyrillic(text: String): String? {
        val out = StringBuilder(text.length)
        var converted = false
        var index = 0
        while (index < text.length) {
            val c = text[index]
            val digraph = if (index + 1 < text.length) digraphs[text.substring(index, index + 2).lowercase(Locale.ROOT)] else null
            val cyrillic = digraph ?: letters[c.lowercaseChar()]
            when {
                cyrillic != null -> {
                    out.append(if (c.isUpperCase() || c.isTitleCase()) cyrillic.uppercaseChar() else cyrillic)
                    converted = true
                }
                c.isLetter() && Character.UnicodeScript.of(c.code) != Character.UnicodeScript.CYRILLIC -> return null
                else -> out.append(c)
            }
            index += if (digraph != null) 2 else 1
        }
        return out.toString().takeIf { converted }
    }
}
