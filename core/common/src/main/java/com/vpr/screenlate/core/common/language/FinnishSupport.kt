package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Finnish. Wiktionary's English edition lists the case and verb forms; the bundle adds the app's own rules for what its
 * tables leave out, clitics and possessive suffixes (talossanikin → talo), tried only where the text has no entry of
 * its own. Long compounds are split into the words they are made of ([compoundLinks]). An ending written after a colon
 * (EU:n, STT:lle) or after a number with a hyphen (1990-luvulla) belongs to the word before it.
 */
object FinnishSupport : SpacedLanguageSupport() {
    override val language = Language.FINNISH
    override val glyph = "Ä"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q1412"
    override val languageTag = "fi"
    override val fontSample = "Vähäinen sää ja öinen myrsky veivät laivan Ålandin saaristoon."
    override val translationSample = "Tänään on kaunis sää. Lähdetäänkö kävelylle?"
    override val audioTestWord = "vesi"
    override val pronunciationSample = "vesi → /ˈʋesi/"
    override val flagCountries = listOf("fi")
    override val systemFonts = LATIN_SYSTEM_FONTS

    /** š and ž of loanwords (šakki, žonglööri) are in the fonts' Latin extension. */
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "äöåšž"

    /** Wiktionary had recordings of eleven of twenty common words checked, Lingua Libre of two. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** ”…” on both sides, »…» in books; English quotes are common too. */
    override val quotePairs = mapOf('”' to '”', '»' to '»', '“' to '”', '(' to ')', '[' to ']')

    /** The clitic and possessive rules would put kuu (-si) beside kuusi and talo (-on) beside taloon. */
    override val inflectionRulesOnlyWithoutEntry = true

    /** Compounds join their words without a link (kirjastonhoitaja: the genitive kirjaston is a form row). */
    override val compoundLinks = emptyList<String>()

    /** Long compounds: 3.1% of the words of news text are longer than 16 letters. */
    override val defaultScanLength = 24

    /** Dates and ordinals: 24.5., 3. kerran. */
    override val ordinalPeriod = true

    /**
     * "esim." (esimerkiksi), "mm." (muun muassa), "ns." (niin sanottu), "ks." (katso), "vrt." (vertaa), "ts." (toisin
     * sanoen), "ko." (kyseinen), "em." (edellä mainittu), "ao." (alla oleva), "huom." (huomaa), "ed." (edellinen),
     * "prof." and "ev.lut.", which come before more of the sentence, often a name.
     */
    override val abbreviations = setOf(
        "esim", "mm", "ns", "ks", "vrt", "ts", "ko", "em", "ao", "huom", "ed", "prof", "ev.lut",
    )

    /**
     * Often a sentence's last word: "jne." (ja niin edelleen), "ym." and "yms." (ynnä muuta), "tms." (tai muuta
     * sellaista), the eras "eaa.", "jaa.", "eKr." and "jKr."; before a number "n." (noin), "s." (sivu), "v." (vuonna),
     * "puh." (puhelin), and after one "milj." and "mrd.".
     */
    override val abbreviationsBeforeLowercase = setOf(
        "jne", "ym", "yms", "tms", "eaa", "jaa", "ekr", "jkr", "n", "s", "v", "puh", "milj", "mrd",
    )

    /** Endings after a number: 1990-luvulla, 20-vuotias. After an abbreviation the hyphen joins a compound (EU-maat). */
    override val hyphenedEndings = HyphenedEndings.AFTER_NUMBERS

    /**
     * A lowercase ending of up to four letters written after a colon onto a word or a number (EU:n, STT:lle, 23:een)
     * belongs to it: a tap on the ending looks the whole up from its start, which the engine shortens to the part
     * before the colon when no dictionary has the whole.
     */
    override fun wordStartOffset(before: String, aimed: String, after: String): Int {
        val own = wordStartOffset(before, aimed)
        if (aimed.length != 1 || !aimed[0].isLowerCase()) return own
        val colon = before.length - own - 1
        if (colon < 1 || before[colon] != ':' || !before[colon - 1].isLetterOrDigit()) return own
        if ((colon + 1 until before.length).any { !before[it].isLowerCase() }) return own
        val rest = after.takeWhile { it.isLowerCase() }.length
        if (own + 1 + rest > MAX_COLON_ENDING || after.getOrNull(rest)?.isLetterOrDigit() == true) return own
        return own + 1 + wordLengthBefore(before.substring(0, colon))
    }

    /** The most letters an ending after a colon has: -n, -lle, -ssa, -iden. */
    private const val MAX_COLON_ENDING = 4
}
