package com.vpr.screenlate.core.anki.note

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support

/** A sentence split around the looked-up word, for `{sentence}` and the cloze markers. */
data class Sentence(val prefix: String, val body: String, val suffix: String) {
    val text: String get() = prefix + body + suffix

    companion object {
        /** Quotes that close and never open in the supported languages. */
        private val CLOSING_ONLY = setOf('”', '’')

        /**
         * Extracts the sentence containing `paragraph[start, start + length)`. A terminator inside quotes opened
         * before the word does not end the sentence, and a closing quote right after a terminator stays in it. The
         * language decides whether a terminator ends the sentence ("Mr." does not). A quote or bracket the paragraph
         * does not close (a list's "1)", quotes the language pairs otherwise) would take in the rest of the
         * paragraph: then the nearest terminator outside the quotes that do close ends the sentence. ” and ’ right
         * after a terminator stay with it even where the language pairs its quotes otherwise (English quotes in German).
         */
        fun extract(paragraph: String, start: Int, length: Int, language: Language): Sentence {
            val safeStart = start.coerceIn(0, paragraph.length)
            val end = (safeStart + length).coerceIn(safeStart, paragraph.length)
            val support = language.support
            val terminators = support.sentenceTerminators
            val quotes = support.quotePairs
            val closing = quotes.values.toSet()

            // Walk back to the previous terminator that is not inside an open quote.
            var from = safeStart
            var depth = 0
            val before = Unclosed()
            while (from > 0) {
                val c = paragraph[from - 1]
                when {
                    c in closing -> depth++
                    c in quotes -> if (depth > 0) before.closed(--depth)
                    support.endsSentence(paragraph, from - 1) -> if (depth == 0) break else before.end(from, depth)
                }
                from--
            }
            if (depth > 0) from = before.nearest ?: from
            // A quote that closes in every language here, right after the previous sentence's terminator, is its end.
            if (from > 0) while (from < safeStart && paragraph[from] in CLOSING_ONLY) from++
            // Walk forward to the next terminator outside quotes opened after the word.
            var to = end
            depth = 0
            val after = Unclosed()
            while (to < paragraph.length) {
                val c = paragraph[to]
                to++
                when {
                    c in quotes -> depth++
                    c in closing -> if (depth > 0) after.closed(--depth)
                    support.endsSentence(paragraph, to - 1) -> if (depth == 0) break else after.end(to, depth)
                }
            }
            if (depth > 0) {
                // The unclosed character may be a closing quote the language pairs otherwise („Idę.“ in Polish).
                to = after.nearest ?: to
                while (to < paragraph.length && paragraph[to] in quotes) to++
            }
            while (to < paragraph.length && paragraph[to].let { it in closing || it in terminators || it in CLOSING_ONLY }) to++
            return Sentence(
                prefix = paragraph.substring(from, safeStart).trimStart(),
                body = paragraph.substring(safeStart, end),
                suffix = paragraph.substring(end, to).trimEnd(),
            )
        }
    }

    /**
     * Sentence ends met inside quotes while walking away from the word, nearest first. Those inside a quote that
     * closes later in the walk drop out, so the ones left are outside every closed quote.
     */
    private class Unclosed {
        private val ends = ArrayList<Pair<Int, Int>>()

        val nearest: Int? get() = ends.firstOrNull()?.first

        fun end(position: Int, depth: Int) {
            ends += position to depth
        }

        fun closed(depth: Int) {
            ends.removeAll { it.second > depth }
        }
    }
}
