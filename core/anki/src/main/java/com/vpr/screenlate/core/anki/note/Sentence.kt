package com.vpr.screenlate.core.anki.note

import com.vpr.screenlate.core.common.Language

/** A sentence split around the looked-up word, for `{sentence}` and the cloze markers. */
data class Sentence(val prefix: String, val body: String, val suffix: String) {
    val text: String get() = prefix + body + suffix

    companion object {
        private val JAPANESE_TERMINATORS = setOf('。', '！', '？', '!', '?', '．', '…', '\n')
        private val JAPANESE_QUOTES = mapOf('「' to '」', '『' to '』', '（' to '）', '(' to ')')

        /**
         * Extracts the sentence containing `paragraph[start, start + length)`. A terminator inside quotes opened
         * before the word does not end the sentence, and a closing quote right after a terminator stays in it.
         */
        fun extract(paragraph: String, start: Int, length: Int, language: Language): Sentence {
            val safeStart = start.coerceIn(0, paragraph.length)
            val end = (safeStart + length).coerceIn(safeStart, paragraph.length)
            val (terminators, quotes) = when (language) {
                Language.JAPANESE -> JAPANESE_TERMINATORS to JAPANESE_QUOTES
            }
            val closing = quotes.values.toSet()

            // Walk back to the previous terminator that is not inside an open quote.
            var from = safeStart
            var depth = 0
            while (from > 0) {
                val c = paragraph[from - 1]
                when {
                    c in closing -> depth++
                    c in quotes -> if (depth > 0) depth-- else Unit
                    c in terminators && depth == 0 -> break
                }
                from--
            }
            // Walk forward to the next terminator outside quotes opened after the word.
            var to = end
            depth = 0
            while (to < paragraph.length) {
                val c = paragraph[to]
                to++
                when {
                    c in quotes -> depth++
                    c in closing -> if (depth > 0) depth-- else Unit
                    c in terminators && depth == 0 -> {
                        while (to < paragraph.length && (paragraph[to] in closing || paragraph[to] in terminators)) to++
                        break
                    }
                }
            }
            return Sentence(
                prefix = paragraph.substring(from, safeStart).trimStart(),
                body = paragraph.substring(safeStart, end),
                suffix = paragraph.substring(end, to).trimEnd(),
            )
        }
    }
}
