package com.vpr.screenlate.overlay

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import kotlin.math.max

/** Characters before the aim that may belong to the aimed word or to the words the lookup starts at as well. */
internal const val WORD_LOOKBACK = 32

/**
 * Characters from the aim on that a word segmenter reads to tell which word the aimed character is in
 * (`LanguageSupport.wordStartOffset`).
 */
internal const val WORD_LOOKAHEAD = 32

/** Where the lookup aimed at [aimed] starts: the first character of a word the language reads as a whole. */
internal fun wordStart(layout: TextLayout, aimed: TextPosition, support: LanguageSupport): TextPosition {
    val characters = layout.paragraphs[aimed.paragraphIndex]
    val after = layout.textFrom(aimed.copy(offset = aimed.offset + 1), WORD_LOOKAHEAD - 1)
    val back = support.wordStartOffset(textBefore(layout, aimed), characters[aimed.offset].text, after)
    return if (back == 0) aimed else aimed.copy(offset = aimed.offset - back)
}

/**
 * Where the lookup of the word at [word] begins: at the words before it that the language looks up from as well
 * (`LanguageSupport.earlierStartOffset`), else at the word.
 */
internal fun earliestStart(layout: TextLayout, word: TextPosition, support: LanguageSupport): TextPosition {
    val back = support.earlierStartOffset(textBefore(layout, word), layout.textFrom(word, WORD_LOOKAHEAD))
    return if (back == 0) word else word.copy(offset = word.offset - back)
}

/** Up to [WORD_LOOKBACK] characters (code points, as the language rules count them) before [position]. */
private fun textBefore(layout: TextLayout, position: TextPosition): String {
    val characters = layout.paragraphs[position.paragraphIndex]
    return characters.subList(max(0, position.offset - WORD_LOOKBACK), position.offset).joinToString("") { it.text }
}
