package com.vpr.screenlate.core.ocr

/**
 * The page with [separator], the language's word separator, after a word hyphenated at a line end ("impor-" ending a
 * line, "tant" starting the next). Lens joins the halves with no separator, as one hyphenated word, and the language
 * rules join a word or keep its hyphen only across a break (`SpacedLanguageSupport.spellingVariants`), which
 * "impor-tant" would not show. A language written without spaces keeps the page as it is.
 */
internal fun OcrPage.withHyphenBreaks(separator: String): OcrPage {
    if (separator.isEmpty()) return this
    return copy(
        paragraphs = paragraphs.map { paragraph ->
            paragraph.copy(
                lines = paragraph.lines.mapIndexed { index, line ->
                    if (index == paragraph.lines.lastIndex) line else line.withHyphenBreak(separator)
                },
            )
        },
    )
}

private fun OcrLine.withHyphenBreak(separator: String): OcrLine {
    val last = words.lastOrNull() ?: return this
    val text = last.text
    val hyphenated = text.length >= 2 && text.last() in LINE_END_HYPHENS && text[text.length - 2].isLetter()
    if (!hyphenated || last.separator.isNotEmpty()) return this
    return copy(words = words.dropLast(1) + last.copy(separator = separator))
}

/** A hyphen, the Unicode hyphen and the soft hyphen. */
private val LINE_END_HYPHENS = charArrayOf('-', '‐', Char(0xAD))
