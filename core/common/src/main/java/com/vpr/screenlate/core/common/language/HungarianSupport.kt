package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Hungarian. Wiktionary's English edition lists the case and verb forms; the bundle adds the app's own rule for verb
 * prefixes, as the tables list the forms of the bare verb only (megtartottam → tartottam, a form of tart), tried only
 * where the text has no entry of its own. Words without marks find the words with them, and words from OCR are also
 * looked up without theirs, which recognition reads ő and ű wrong for (füzfa, fúzfa → fűzfa; [foldsMarks]). Long
 * compounds are split into the words they are made of ([compoundLinks]). An ending after a number or an abbreviation in capitals with a hyphen
 * (2023-ban, EU-ban) is also looked up with the hyphen.
 */
object HungarianSupport : SpacedLanguageSupport() {
    override val language = Language.HUNGARIAN
    override val glyph = "M"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9067"
    override val languageTag = "hu"
    override val fontSample = "Öt hűvös őszi éjjel az árvíztűrő tükörfúrógép zúgott."
    override val translationSample = "Ma szép idő van. Elmegyünk sétálni?"
    override val audioTestWord = "víz"
    override val pronunciationSample = "víz → /ˈviːz/"
    override val flagCountries = listOf("hu")
    override val systemFonts = LATIN_SYSTEM_FONTS

    /** ő and ű are in the fonts' Latin extension. */
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "áéíóöőúüű"

    /** Wiktionary had recordings of seventeen of twenty common words checked, Lingua Libre of three. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** „…” outside, »…« inside; English quotes are common too. */
    override val quotePairs = mapOf('„' to '”', '»' to '«', '“' to '”', '(' to ')', '[' to ']')

    /** The prefix rule would put tart (meg-) beside megtart, and vesz (be-, through its noun vétel) beside bevétel. */
    override val inflectionRulesOnlyWithoutEntry = true

    /** Wiktionary's verb forms say both definite and object-definite for the definite conjugation; nouns have neither. */
    override val formTagAliases = mapOf("definite" to "object-definite", "indefinite" to "object-indefinite")

    /** Compounds join their words without a link (fűzfa: fűz + fa). */
    override val compoundLinks = emptyList<String>()

    /** Dates and ordinals: 2023. május 3., a 19. század. */
    override val ordinalPeriod = true

    /**
     * "pl." (például), "ill." (illetve), "ún." (úgynevezett), "kb." (körülbelül), "ld." (lásd), "vö." (vesd össze),
     * "szül." (született), and before a name "ford." (fordította), "id." (idősebb), "ifj." (ifjabb), "dr." and "gr."
     * (gróf), which come before more of the sentence.
     */
    override val abbreviations = setOf(
        "pl", "ill", "ún", "kb", "ld", "vö", "szül", "ford", "id", "ifj", "dr", "gr",
    )

    /**
     * Often a sentence's last word: "stb." (s a többi), the company forms "kft.", "zrt.", "nyrt.", "bt." and "rt."; after
     * a number "sz." (szám, század), "ker." (kerület), "évf." (évfolyam) and "u." (utca), and the months of dates
     * (2023. jan. 5.).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "stb", "kft", "zrt", "nyrt", "bt", "rt", "sz", "ker", "évf", "u",
        "jan", "febr", "márc", "ápr", "jún", "júl", "aug", "szept", "okt", "nov", "dec",
    )

    /** Endings after a number or an abbreviation in capitals: 2023-ban, 3-án, EU-ban. */
    override val hyphenedEndings = HyphenedEndings.AFTER_NUMBERS_AND_CAPITALS
}
