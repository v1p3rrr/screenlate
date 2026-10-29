package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Telugu, in its own script. Yomitan has no Telugu descriptor: the language's bundle takes Kannada's under its own code,
 * composes the text as headwords are (NFC), drops the zero-width joiners and spaces and soft hyphens inside words, and
 * adds the app's own case endings, postpositions and particles (రాష్ట్రంలోనే → రాష్ట్రం), as Wiktionary lists plurals
 * and few case forms. Dictionaries give no readings. Lingua Libre has recordings of nearly every common word,
 * Wiktionary of few.
 */
object TeluguSupport : DravidianSupport() {
    override val language = Language.TELUGU
    override val glyph = "తె"
    override val ocrScript = OcrScript.TELUGU
    override val wikidataId = "Q8097"
    override val languageTag = "te"
    override val fontSample = "శ్రీ లక్ష్మి క్షేత్రంలో సామర్థ్యం గురించి పాటలు పాడింది. ఆడపిల్లలు వాకిట్లో ముగ్గులు వేశారు."
    override val translationSample = "ఈ రోజు వాతావరణం బాగుంది. నడకకు వెళ్దామా?"
    override val audioTestWord = "నీరు"
    override val pronunciationSample = "నీరు → [niːɾu]"
    override val flagCountries = listOf("in")
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /**
     * The phone's default font falls back to its Telugu one by itself; Noto Serif Telugu is reached by name. The range
     * is Google Fonts' Telugu subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifTelugu-Regular", "Noto Serif Telugu Regular", "Noto Serif Telugu"),
        unicodeRange = "U+0951-0952, U+0964-0965, U+0B83, U+0C00-0C7F, U+1CD5-1CD6, U+1CD8, U+1CDA, U+1CF2, " +
            "U+200C-200D, U+25CC",
        aliases = emptyMap(),
    )

    /**
     * Wiktionary writes older spellings than running text: the arasunna ఁ (665 headwords, none in news), ఱ for ర, a
     * final -ము where text writes -ం (నిర్ణయము 1, నిర్ణయం 756 in news), and a few headwords with joiners. Each headword
     * and form is also found under the modern spelling.
     */
    override val headwordMarks = HeadwordMarks(
        marks = "",
        bases = "",
        everyHeadword = true,
        replacements = mapOf(ARASUNNA to "", "ఱ" to "ర", "\u200C" to "", "\u200D" to ""),
        endingReplacements = mapOf("ము" to "ం"),
    )

    /** Wiktionary lists the plurals and a few tables; the app's endings are for the words it gives no forms of. */
    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "డా." డాక్టర్, "ప్రొ." ప్రొఫెసర్, "శ్రీ.", "క్రీ.పూ." and "క్రీ.శ." (and "సా.శ.", "సా.శ.పూ."), "కి.మీ.", "చ.కి.మీ.",
     * "కి.గ్రా.", "సెం.మీ.", "రూ." రూపాయలు, "నం." and "నెం." నంబరు, "సం." సంవత్సరం, "ఉదా." ఉదాహరణ, "పు." పుట; and the names of
     * Latin letters as initials ("ఎన్.టి.ఆర్.", "వై.ఎస్. జగన్"). One-syllable abbreviations hold the sentence by the
     * syllable rule too ([syllableInitialScript]).
     */
    override val abbreviations = setOf(
        "డా", "ప్రొ", "శ్రీ", "క్రీ.పూ", "క్రీ.శ", "సా.శ", "సా.శ.పూ", "కి.మీ", "చ.కి.మీ", "కి.గ్రా", "సెం.మీ", "రూ", "నం", "నెం",
        "సం", "ఉదా", "పు",
    ) + LETTER_NAMES

    /** Names of Latin letters, joined by periods in acronyms and initials: "బి.జె.పి.", "ఎన్.టి.ఆర్.". */
    override val initials = LETTER_NAMES

    /** Names are written with syllables for initials ("కె. చంద్రశేఖర్ రావు") and titles as chains of them ("సా.శ.పూ."). */
    override val syllableInitialScript = Character.UnicodeScript.TELUGU

    /** A word of one syllable that ends sentences in dialogue: the imperative రా "come". */
    override val syllableSentenceEnds = setOf("రా")
}

private const val ARASUNNA = "\u0C01"

/** Telugu spells Latin letters as English says them, most two ways (బి and బీ, డబ్ల్యు and డబ్ల్యూ). */
private val LETTER_NAMES = setOf(
    "ఎ", "ఏ", "బి", "బీ", "సి", "సీ", "డి", "డీ", "ఇ", "ఈ", "ఎఫ్", "జి", "జీ", "హెచ్", "ఎచ్", "ఐ", "జె", "జే", "కె", "కే",
    "ఎల్", "ఎం", "ఎమ్", "ఎన్", "ఒ", "ఓ", "పి", "పీ", "క్యూ", "ఆర్", "ఎస్", "టి", "టీ", "యు", "యూ", "వి", "వీ", "డబ్ల్యు",
    "డబ్ల్యూ", "ఎక్స్", "వై", "జెడ్", "జడ్",
)
