package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Hebrew, written right to left, mostly without vowel points (niqqud): text with them, and with cantillation, is also
 * looked up without them, and the import keys Wiktionary's pointed forms (כֶּלֶב) under their letters ([headwordMarks]).
 * The prefixes ו ה ב כ ל מ ש and כש are written joined to their word (והבית, בבית, כשהגעתי); the app's own rules in the
 * bundle split up to three letters of them off in the order the grammar allows. Abbreviations and loan sounds use the
 * geresh and gershayim (צה״ל, ג׳ירפה), which recognition and keyboards give as ASCII ' and ": both belong to the word,
 * and the bundle tries the ASCII ones as the Hebrew marks. The maqaf (בית־ספר) joins words as the hyphen does.
 * Dictionaries give no readings; Lingua Libre has recordings of more common words than Wiktionary, whose titles are
 * mostly pointed (`He-כֶּלֶב.ogg`).
 */
object HebrewSupport : SpacedLanguageSupport() {
    override val language = Language.HEBREW
    override val glyph = "א"
    override val ocrScript = OcrScript.HEBREW
    override val wikidataId = "Q9288"
    override val languageTag = "he"
    override val rightToLeft = true
    override val fontSample = "הַיֶּלֶד קוֹרֵא סֵפֶר בְּבֵית־הַסֵּפֶר. צה״ל, ג׳ירפה, ₪123"
    override val translationSample = "מזג האוויר יפה היום. נצא לטייל?"
    override val audioTestWord = "כלב"
    override val pronunciationSample = "כלב → [ˈkelev]"
    override val flagCountries = listOf("il")

    /** Pointed words with a prefix run long: 16% of them pass 16 characters, none 24. */
    override val defaultScanLength = 24

    override val quotePairs = mapOf('„' to '”', '“' to '”', '(' to ')', '[' to ']')

    /**
     * The geresh and gershayim (ג׳ירפה, צה״ל), the ASCII quote that stands for the gershayim (צה"ל) and the maqaf
     * (בית־ספר) join letters into one word too. None of them starts a word: a gershayim or quote that opens a quotation
     * stays out of it.
     */
    override val wordConnectors = super.wordConnectors + "\"\u05BE\u05F3\u05F4"

    /** Downloadable fonts made for the Hebrew script. */
    override val fontSubsets = setOf("hebrew")

    /**
     * The phone's default font falls back to Noto Sans Hebrew by itself; Noto Serif Hebrew is reached by its PostScript
     * and full names, which load on the page where the family name does not. The range is Google Fonts' Hebrew subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifHebrew-Regular", "Noto Serif Hebrew Regular"),
        unicodeRange = "U+0307-0308, U+0590-05FF, U+200C-2010, U+20AA, U+25CC, U+FB1D-FB4F",
        aliases = emptyMap(),
    )

    /**
     * Wiktionary's forms are pointed (82% of them), its words are not: a form is also found under its letters, and the
     * lemmas forms name lose their points. Forms in no Hebrew letter (Phoenician spellings) are left out. A construct
     * form written with its maqaf (כֶּלֶב־) is also found without it (the import's `form_headwords`).
     */
    override val headwordMarks = HeadwordMarks(
        marks = HEBREW_POINTS,
        bases = HEBREW_LETTERS,
        formsInScript = true,
    )

    /** 78% of Wiktionary's Hebrew recordings are titled pointed (`He-כֶּלֶב.ogg`). */
    override val recordingMarks = HEBREW_POINTS

    /** The dictionaries give no reading, so `{reading}` is the word itself, Hebrew text too. */
    override val rightToLeftMarkers = RIGHT_TO_LEFT_TEXT_MARKERS + "reading"

    /**
     * Wiktionary's transcriptions of a Hebrew word start with the Biblical or Tiberian one for 6% of the words that
     * have a Modern Israeli one too (כלב: /kalb/ before [ˈkelev]); the Modern Israeli ones go first.
     */
    override fun firstTranscription(tags: List<String>): Boolean =
        tags.any { it.startsWith("Modern-Israeli") || it.startsWith("Modern Israeli") }

    /** wordfreq writes the gershayim and geresh as ASCII quotes (צה"ל), dictionaries as the Hebrew marks. */
    override fun frequencyWord(word: String): String = hebrewQuotes(word)

    private fun hebrewQuotes(text: String): String =
        text.replace(ASCII_GERSHAYIM, "\u05F4").replace(ASCII_GERESH, "\u05F3")
}

/**
 * The points and cantillation marks of the Hebrew block that dictionaries leave out of words: the accents
 * U+0591-05AF, the vowels, dagesh, meteg and rafe, the shin and sin dots, the upper and lower dots and qamats qatan.
 * Not the maqaf, paseq, sof pasuq and nun hafukha, which are punctuation.
 */
internal const val HEBREW_POINTS = "\u0591\u0592\u0593\u0594\u0595\u0596\u0597\u0598\u0599\u059A\u059B\u059C\u059D" +
    "\u059E\u059F\u05A0\u05A1\u05A2\u05A3\u05A4\u05A5\u05A6\u05A7\u05A8\u05A9\u05AA\u05AB\u05AC\u05AD\u05AE\u05AF" +
    "\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BA\u05BB\u05BC\u05BD\u05BF\u05C1\u05C2\u05C4" +
    "\u05C5\u05C7"

/** The letters of the Hebrew block, final forms and Yiddish ligatures included. */
internal val HEBREW_LETTERS: String = buildString {
    for (codePoint in 0x05D0..0x05F2) if (Character.isLetter(codePoint)) appendCodePoint(codePoint)
}

/** An ASCII double quote between Hebrew letters, which stands for the gershayim (צה"ל). */
private val ASCII_GERSHAYIM = Regex("(?<=[\u05D0-\u05EA])\"(?=[\u05D0-\u05EA])")

/** An ASCII apostrophe between Hebrew letters, which stands for the geresh (ג'ירפה). */
private val ASCII_GERESH = Regex("(?<=[\u05D0-\u05EA])'(?=[\u05D0-\u05EA])")
