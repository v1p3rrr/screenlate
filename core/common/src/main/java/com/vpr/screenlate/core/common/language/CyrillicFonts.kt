package com.vpr.screenlate.core.common.language

/**
 * The phone's fonts for languages written in Cyrillic letters: as [LATIN_SYSTEM_FONTS], with the Cyrillic blocks and the
 * combining accents that mark stress (ко́шка) in the range.
 */
internal val CYRILLIC_SYSTEM_FONTS = LATIN_SYSTEM_FONTS.copy(
    unicodeRange = "U+0000-024F, U+0300-036F, U+0400-052F, U+1C80-1C8F, U+1E00-1EFF, U+2000-206F, U+2DE0-2DFF, U+A640-A69F",
)
