package com.vpr.screenlate.core.common.language

/**
 * The phone's fonts for languages written in Latin letters: text keeps the phone's own sans-serif font, so none is
 * named; the serif one is Noto Serif, which Android ships.
 */
internal val LATIN_SYSTEM_FONTS = SystemFonts(
    sans = emptyList(),
    serif = listOf("NotoSerif-Regular", "Noto Serif"),
    unicodeRange = "U+0000-024F, U+1E00-1EFF, U+2000-206F",
    aliases = emptyMap(),
)

/** The Google Fonts subset with the letters of English and the Western European languages (Latin-1, œ). */
internal val LATIN_FONT_SUBSETS = setOf("latin")

/** Latin letters with the marks of Central and Eastern European languages and Turkish (ł, ğ, ā, č). */
internal val LATIN_EXT_FONT_SUBSETS = setOf("latin", "latin-ext")
