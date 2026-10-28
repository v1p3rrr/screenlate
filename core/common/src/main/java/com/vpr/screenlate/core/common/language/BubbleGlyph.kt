package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.text.Normalizer

/**
 * What the docked bubble shows for [language]: its [LanguageSupport.glyph], or its [LanguageSupport.codeLabel] when
 * another of the [quick] languages has a glyph of the same shape (E for English and Spanish, P and Р, Č and C), so the
 * bubble tells apart the languages switched between. [language] need not be one of them (a photo's language).
 */
fun bubbleGlyph(language: Language, quick: List<Language>): String {
    val glyph = language.support.glyph
    val shape = glyphShape(glyph)
    val clash = quick.any { it != language && glyphShape(it.support.glyph) == shape }
    return if (clash) language.support.codeLabel else glyph
}

/** [glyph] without marks over or under its letters, with Cyrillic and Greek letters that look Latin as those. */
internal fun glyphShape(glyph: String): String =
    Normalizer.normalize(glyph, Normalizer.Form.NFD)
        .filter { it.category != CharCategory.NON_SPACING_MARK }
        .map { CyrillicLookAlikes.latinOf(it) ?: GreekLookAlikes.latinOf(it) ?: it }
        .joinToString("")
