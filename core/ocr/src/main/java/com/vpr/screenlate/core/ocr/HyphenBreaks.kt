package com.vpr.screenlate.core.ocr

import com.vpr.screenlate.core.common.geometry.Box

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

/**
 * The words of a line whose [text] ends with a hyphen its last word lacks: ML Kit keeps a hyphen at the line end in
 * the line's text but leaves it out of the line's last element ("impor-" ends the line, the element is "impor"). The
 * hyphen is put back after the word, its box reaching the line's end, so the lookup can join the word across the
 * break. A hyphen after anything but a letter, and vertical lines, stay as they are.
 */
internal fun List<OcrWord>.withLineEndHyphen(text: String, lineBox: Box, vertical: Boolean): List<OcrWord> {
    val hyphen = text.trimEnd().lastOrNull()?.takeIf { it in LINE_END_HYPHENS } ?: return this
    val last = lastOrNull() ?: return this
    if (vertical || last.text.lastOrNull()?.isLetter() != true) return this
    val right = if (lineBox.right > last.box.right) lineBox.right else last.box.right + last.box.height / 3
    val hyphenBox = Box(last.box.right, last.box.top, right, last.box.bottom)
    return dropLast(1) + last.copy(
        text = last.text + hyphen,
        box = last.box.copy(right = right),
        characterBoxes = last.characterBoxes?.plus(hyphenBox),
    )
}

/** A hyphen, the Unicode hyphen and the soft hyphen. */
private val LINE_END_HYPHENS = charArrayOf('-', '‐', Char(0xAD))
