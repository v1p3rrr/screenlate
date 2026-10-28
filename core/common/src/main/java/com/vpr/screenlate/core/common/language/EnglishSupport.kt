package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object EnglishSupport : SpacedLanguageSupport() {
    override val language = Language.ENGLISH
    override val glyph = "E"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q1860"
    override val languageTag = "en"
    override val fontSample = "The quick brown fox jumps over the lazy dog"
    override val translationSample = "The weather is nice today. Let's go for a walk."
    override val audioTestWord = "read"
    override val pronunciationSample = "water → /ˈwɔːtər/"
    override val flagCountries = listOf("us", "gb")
    override val audioRegions = listOf(AudioRegion("us", setOf("us")), AudioRegion("uk", setOf("uk", "gb")), AudioRegion.OTHER)

    /** Single accent names only; tags that list several places (`Southern US, Australia`) are left out. */
    override val transcriptionRegionNames = mapOf(
        "us" to listOf(
            "General-American", "of the US", "California", "Southern-US", "Northern-US", "Midwestern-US",
            "Inland-Northern-American", "New-York-City", "New-England", "Eastern-New-England", "Boston", "Philadelphia",
            "African-American Vernacular",
        ),
        "gb" to listOf(
            "British", "Received-Pronunciation", "England", "Southern-England", "Northern-England", "London",
            "Multicultural-London-English", "Estuary-English", "Cockney", "Northumbria", "Geordie", "Yorkshire",
            "Midlands", "Wales", "Scotland", "Northern-Ireland",
        ),
        "ca" to listOf("Standard-Canadian", "Canadian", "of Canada", "Atlantic-Canada"),
        "au" to listOf("General-Australian", "Australia"),
        "nz" to listOf("New-Zealand"),
        "ie" to listOf("Ireland", "Dublin", "Local Dublin"),
        "za" to listOf("General-South-African", "South-Africa"),
        "in" to listOf("India"),
        "sg" to listOf("Singapore"),
        "my" to listOf("Malaysia"),
        "pk" to listOf("Pakistan"),
        "jm" to listOf("Jamaica"),
    ).flatMap { (region, names) -> names.map { it to region } }.toMap()

    override val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "vs", "etc", "e.g", "i.e", "cf", "approx",
        "fig", "vol", "ch", "p", "pp", "ed", "inc", "ltd", "co", "corp", "dept", "est", "jan", "feb", "mar", "apr",
        "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec", "a.m", "p.m", "u.s", "u.k",
    )

    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
}
