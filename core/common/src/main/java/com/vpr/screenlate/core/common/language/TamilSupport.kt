package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Tamil, in its own script. Yomitan has no Tamil descriptor: the language's bundle takes Kannada's under its own code,
 * composes the text as headwords are (NFC), reads the vowel sign ௌ and ெள (which look the same) as each other and
 * ஸ்ரீ as the headwords' ஶ்ரீ, and adds the app's own case endings, plural and particles (மரத்திலும் → மரம்), as
 * Wiktionary's tables leave out the locative and genitive and many nouns have none. Dictionaries give no readings.
 * Words are long (3% of running news words pass 16 characters), so the popup looks at 24. Wiktionary has recordings
 * of more common words than Lingua Libre.
 */
object TamilSupport : DravidianSupport() {
    override val language = Language.TAMIL
    override val glyph = "த"
    override val ocrScript = OcrScript.TAMIL
    override val wikidataId = "Q5885"
    override val languageTag = "ta"
    override val fontSample = "ஸ்ரீ ஜானகி க்ஷேத்திரத்தில் ஔவையாரின் பாடல்களைப் படித்தாள். பெண்கள் வாசலில் கோலம் போட்டனர்."
    override val translationSample = "இன்று வானிலை நன்றாக உள்ளது. நடைக்குப் போகலாமா?"
    override val audioTestWord = "தண்ணீர்"
    override val pronunciationSample = "தண்ணீர் → /t̪ɐɳːiːɾ/"
    override val flagCountries = listOf("in")
    override val defaultScanLength = 24
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /**
     * The phone's default font falls back to its Tamil one by itself; Noto Serif Tamil is reached by name. The range is
     * Google Fonts' Tamil subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifTamil-Regular", "Noto Serif Tamil Regular", "Noto Serif Tamil"),
        unicodeRange = "U+0964-0965, U+0B82-0BFA, U+200C-200D, U+20B9, U+25CC",
        aliases = emptyMap(),
    )

    /** Wiktionary lists the forms of its tabled nouns and verbs; the app's endings are for the words it gives none of. */
    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "திரு." திருவாளர், "திருமதி." and "செல்வி.", "மரு." மருத்துவர், "கி.மு." and "கி.பி." (and "பொ.ஊ.", "பொ.ஊ.மு."), "அண்."
     * அண்ணளவாக, "கி.மீ.", "மி.மீ.", "செ.மீ.", "கி.கி.", "ரூ." ரூபாய், "எண்." and "ப." பக்கம், "எ.கா." எடுத்துக்காட்டு, "இரா." and
     * "யாழ்." (இராமநாதன், யாழ்ப்பாணம் before names); and the names of Latin letters as initials ("ஏ. ஆர். ரஹ்மான்").
     * One-syllable abbreviations hold the sentence by the syllable rule too ([syllableInitialScript]).
     */
    override val abbreviations = setOf(
        "திரு", "திருமதி", "செல்வி", "மரு", "கி.மு", "கி.பி", "பொ.ஊ", "பொ.ஊ.மு", "அண்", "கி.மீ", "மி.மீ", "செ.மீ", "கி.கி",
        "ரூ", "எண்", "ப", "எ.கா", "இரா", "யாழ்",
    ) + LETTER_NAMES

    /**
     * Months before a date ("ஜன. 15", "மார்ச். 3"): மே and ஜூலை are words as well, and the others end few sentences, so
     * they hold only before a number (Tamil has no lowercase).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "ஜன", "பிப்", "மார்", "மார்ச்", "ஏப்", "மே", "ஜூன்", "ஜூலை", "ஆக", "செப்", "அக்", "நவ", "டிச",
    )

    /** Names of Latin letters, joined by periods in acronyms and initials: "பி.ஜே.பி.", "எம்.ஜி.ஆர்.". */
    override val initials = LETTER_NAMES

    /**
     * Names are written with syllables for initials ("க. சண்முகம்", "மு. கருணாநிதி") and parties as chains of them
     * ("தி.மு.க."); in news nearly every one-syllable word before a period is one.
     */
    override val syllableInitialScript = Character.UnicodeScript.TAMIL

    /** Words of one syllable that end sentences in dialogue: the imperatives வா "come" and போ "go", and நீ "you". */
    override val syllableSentenceEnds = setOf("வா", "போ", "நீ")
}

/** Tamil spells Latin letters as English says them, some two ways (டி and டீ, எஃப் and எப், இசட் and ஜெட்). */
private val LETTER_NAMES = setOf(
    "ஏ", "பி", "சி", "டி", "டீ", "இ", "ஈ", "எஃப்", "எப்", "ஜி", "எச்", "ஹெச்", "ஐ", "ஜே", "கே", "எல்", "எம்", "என்", "ஓ",
    "க்யூ", "ஆர்", "எஸ்", "யு", "யூ", "வி", "டபிள்யூ", "டபுள்யூ", "எக்ஸ்", "ஒய்", "வை", "இசட்", "ஜெட்", "இஸட்",
)
