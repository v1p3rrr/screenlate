package com.vpr.screenlate.core.common.language

/**
 * Text derived from a source string (by spelling rules or transliteration) that remembers where each character
 * came from, so the length of a match in [text] can be mapped back to the source, e.g. to highlight the right
 * characters on the screen.
 *
 * @param sourceEnds for every character of [text], the end (exclusive) of the source part it was made from;
 * non-decreasing.
 */
class MappedText(val text: String, private val sourceEnds: IntArray) {
    init {
        require(sourceEnds.size == text.length)
    }

    /** Length of the source prefix that produced the first [length] characters of [text]. */
    fun sourceLength(length: Int): Int = if (length <= 0) 0 else sourceEnds[length.coerceAtMost(text.length) - 1]

    /** [next] was derived from this [text]; the result maps [next]'s text back to this text's source. */
    fun then(next: MappedText): MappedText =
        MappedText(next.text, IntArray(next.text.length) { sourceLength(next.sourceEnds[it]) })

    /**
     * Replaces every match of [regex] in [text]. [replacement] uses JavaScript syntax (`$1`, `$&`, `$<name>`,
     * `$$`) as in Yomitan's text replacements. A replaced part maps to the whole match.
     */
    fun replace(regex: Regex, replacement: String): MappedText {
        val matcher = regex.toPattern().matcher(text)
        val javaReplacement = javaReplacement(replacement)
        // The StringBuffer overload of appendReplacement exists on every API level; the StringBuilder one needs 34.
        val out = StringBuffer()
        val ends = ArrayList<Int>(text.length)
        var copiedUntil = 0
        while (matcher.find()) {
            val before = out.length
            matcher.appendReplacement(out, javaReplacement)
            val copied = matcher.start() - copiedUntil
            for (i in 0 until copied) ends += copiedUntil + i + 1
            repeat(out.length - before - copied) { ends += matcher.end() }
            copiedUntil = matcher.end()
        }
        for (i in copiedUntil until text.length) {
            out.append(text[i])
            ends += i + 1
        }
        return then(MappedText(out.toString(), ends.toIntArray()))
    }

    override fun toString(): String = text

    companion object {
        fun identity(text: String): MappedText = MappedText(text, IntArray(text.length) { it + 1 })

        /** Translates a JavaScript replacement string into `java.util.regex` syntax. */
        internal fun javaReplacement(replacement: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < replacement.length) {
                val c = replacement[i]
                val next = replacement.getOrNull(i + 1)
                when {
                    c == '\\' -> out.append("\\\\")
                    c != '$' -> out.append(c)
                    next == '$' -> { out.append("\\$"); i++ }
                    next == '&' -> { out.append("$0"); i++ }
                    next != null && next.isDigit() -> { out.append('$').append(next); i++ }
                    next == '<' && replacement.indexOf('>', i) > i -> {
                        val end = replacement.indexOf('>', i)
                        out.append("\${").append(replacement, i + 2, end).append('}')
                        i = end
                    }
                    else -> out.append("\\$")
                }
                i++
            }
            return out.toString()
        }
    }
}
