package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object EnglishSupport : SpacedLanguageSupport() {
    override val language = Language.ENGLISH
    override val glyph = "A"
    override val ocrScript = OcrScript.LATIN
    override val iso639Part3 = "eng"
    override val wikidataId = "Q1860"
    override val languageTag = "en"
    override val fontSample = "The quick brown fox jumps over the lazy dog"
    override val translationSample = "The weather is nice today. Let's go for a walk."
    override val audioTestWord = "read"
    override val pronunciationSample = "water → /ˈwɔːtər/"
    override val audioRegions = listOf(AudioRegion("us", setOf("us")), AudioRegion("uk", setOf("uk", "gb")), AudioRegion.OTHER)

    override val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "vs", "etc", "e.g", "i.e", "cf", "approx",
        "fig", "vol", "ch", "p", "pp", "ed", "inc", "ltd", "co", "corp", "dept", "est", "jan", "feb", "mar", "apr",
        "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec", "a.m", "p.m", "u.s", "u.k",
    )

    /**
     * Latin text keeps the phone's own font, so no sans-serif face is named; the serif one is Noto Serif, which
     * Android ships.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerif-Regular", "Noto Serif"),
        unicodeRange = "U+0000-024F, U+1E00-1EFF, U+2000-206F",
        aliases = emptyMap(),
    )
}
