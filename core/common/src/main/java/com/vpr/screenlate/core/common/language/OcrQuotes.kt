package com.vpr.screenlate.core.common.language

/**
 * Quotes that engines write in ASCII, written as the language's ([LanguageSupport.ocrQuotes]). Cloud recognition reads
 * » and « as >> and <<, and both on-device and cloud recognition read curly quotes as ": a " right after a full stop,
 * ! or ? closes the latest quote still open, a " at a word's start with no quote open is the language's first opening
 * quote, and a " that ends a word with a quote open closes it too (“Hello," he said), so the quote it opened does not
 * stay open up to a later sentence. A " inside a word (צה"ל) stays.
 */
internal object OcrQuotes {
    private val GUILLEMETS = mapOf(">>" to '»', "<<" to '«')

    fun fix(lines: List<List<String>>, pairs: Map<Char, Char>): List<List<String>> {
        // Brackets and corner brackets close with Pe; quotation marks with Pi or Pf (» « “ ” ‘ ’).
        val quotes = pairs.filterValues { Character.getType(it).toByte() in QUOTE_TYPES }
        val opening = quotes.keys.firstOrNull()
        val written = pairs.keys + pairs.values
        val guillemets = GUILLEMETS.filterValues { it in written }
        // The closing quotes of the quotes open so far, the latest last, across lines and words in reading order.
        val open = ArrayList<Char>()
        // Cloud recognition gives punctuation as words of its own (aici ! "), so a word's first character follows the
        // last one of the word before it.
        var last: Char? = null
        return lines.map { line ->
            line.map { word -> fixWord(word, last, quotes, opening, guillemets, open).also { last = it.lastOrNull() ?: last } }
        }
    }

    private fun fixWord(
        word: String,
        before: Char?,
        quotes: Map<Char, Char>,
        opening: Char?,
        guillemets: Map<String, Char>,
        open: MutableList<Char>,
    ): String {
        var text = word
        for ((standIn, quote) in guillemets) {
            if (text.startsWith(standIn)) text = quote + text.substring(standIn.length)
            if (text.length > standIn.length && text.endsWith(standIn)) text = text.dropLast(standIn.length) + quote
        }
        val out = StringBuilder(text)
        for (index in out.indices) {
            val c = out[index]
            // A " before a letter at a word's start opens a quote, even after the word before it ends a sentence.
            val startsWord = index == 0 && out.getOrNull(1)?.isLetterOrDigit() == true
            when {
                c == '"' && !startsWord && (if (index > 0) out[index - 1] else before) in SENTENCE_ENDS && open.isNotEmpty() ->
                    out[index] = open.removeAt(open.lastIndex)
                c == '"' && index == 0 && open.isEmpty() && opening != null -> {
                    out[index] = opening
                    open += quotes.getValue(opening)
                }
                c == '"' && index == out.lastIndex && open.isNotEmpty() -> out[index] = open.removeAt(open.lastIndex)
                c in open -> repeat(open.size - open.lastIndexOf(c)) { open.removeAt(open.lastIndex) }
                c in quotes -> open += quotes.getValue(c)
            }
        }
        return out.toString()
    }

    private val SENTENCE_ENDS = setOf('.', '!', '?')
    private val QUOTE_TYPES = setOf(Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION)
}
