package com.vpr.screenlate.overlay

import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition

/** Texts offered by the bubble's copy menu. */
internal object CopyMenuText {
    /**
     * The paragraph under the aim ([aimed] in [layout]); with the aim off text, the paragraph of the word shown in the
     * popup ([shown], in the layout it was found in). Empty when there is neither.
     */
    fun paragraph(layout: TextLayout, aimed: TextPosition?, shown: Pair<TextLayout, TextPosition>?): String {
        val (source, position) = when {
            aimed != null -> layout to aimed
            shown != null -> shown
            else -> return ""
        }
        return source.paragraphText(position).first.trim()
    }

    /** Every recognized paragraph, one per line. */
    fun all(layout: TextLayout, separator: String): String = layout.paragraphs
        .map { characters -> characters.joinToString("") { it.text }.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator)
}
