package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

object GermanSupport : SpacedLanguageSupport() {
    override val language = Language.GERMAN
    override val glyph = "ß"
    override val ocrScript = OcrScript.LATIN
    override val iso639Part3 = "deu"
    override val wikidataId = "Q188"
    override val languageTag = "de"
    override val fontSample = "Victor jagt zwölf Boxkämpfer quer über den großen Sylter Deich."
    override val translationSample = "Heute ist schönes Wetter. Gehen wir spazieren?"
    override val audioTestWord = "Wasser"
    override val pronunciationSample = "Wasser → /ˈvasɐ/"
    override val flagCountries = listOf("de")
    override val systemFonts = LATIN_SYSTEM_FONTS

    /** „…“ and ‚…‘ close with the characters English opens with; »…« point inwards. */
    override val quotePairs = mapOf('„' to '“', '‚' to '‘', '»' to '«', '(' to ')', '[' to ']')

    /**
     * Recordings from Germany carry no code in Wiktionary titles (`De-Wasser.ogg`) and fall into "other"; Austrian ones
     * are `De-at-…`. The `de` region orders transcriptions tagged with the German flag.
     */
    override val audioRegions = listOf(
        AudioRegion("de", setOf("de")),
        AudioRegion.OTHER,
        AudioRegion("at", setOf("at")),
        AudioRegion("ch", setOf("ch")),
    )

    /**
     * Standard German counts as Germany. Regional variants inside Germany (`Southern-Germany`, `Bavarian`, flags with a
     * mark such as 🇩🇪⬆️ for the north) are left out, so they do not rank above the standard form.
     */
    override val transcriptionRegionNames = mapOf(
        "std" to "de",
        "Standard" to "de",
        "Standard-German" to "de",
        "prescriptive standard" to "de",
        "Austrian" to "at",
    )

    /** "z. B." and "d. h." written with spaces end in single letters, which are listed as well. */
    override val abbreviations = setOf(
        "z.b", "z", "b", "d.h", "d", "h", "u.a", "u", "a", "v.a", "v", "o.ä", "u.ä", "s", "o", "usw", "bzw", "ca",
        "vgl", "evtl", "ggf", "inkl", "exkl", "bspw", "sog", "etc", "nr", "str", "dr", "prof", "hr", "fr", "jh", "jhd",
        "mio", "mrd", "tel", "abs", "bd", "kap", "abb", "s.o", "s.u", "z.t", "zt", "u.v.m", "st", "jan", "feb", "febr",
        "apr", "aug", "sept", "okt", "nov", "dez",
    )

    /**
     * A period after a number of one or two digits makes it an ordinal ("am 3. Oktober", "im 19. Jahrhundert") and
     * does not end the sentence; a year at the end of a sentence still does.
     */
    override fun endsSentence(text: String, index: Int): Boolean {
        if (text[index] == '.' && digitsBefore(text, index) in 1..2) return false
        return super.endsSentence(text, index)
    }

    private fun digitsBefore(text: String, index: Int): Int {
        var start = index
        while (start > 0 && text[start - 1].isDigit()) start--
        if (start > 0 && text[start - 1].isLetter()) return 0
        return index - start
    }
}
