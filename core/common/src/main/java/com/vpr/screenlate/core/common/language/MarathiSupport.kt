package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Marathi, in Devanagari. Yomitan has no Marathi descriptor: the language's bundle takes Hindi's under its own code,
 * composes the text as headwords are (NFC), reads an old font's य़ as the य of a conjunct (मोठय़ा), drops a nukta the
 * headwords leave out, writes अॅ, ऍ and ँ as the headwords' ॲ and ॅं, and adds the app's own list of case endings and
 * particles (घरातही → घर), as Wiktionary lists verb tables and few noun forms. Dictionaries give no readings. The page
 * is tagged `mr`, under which the fonts that know Marathi draw its own ल and श. Lingua Libre has recordings of most
 * common words, Wiktionary none.
 */
object MarathiSupport : SpacedLanguageSupport() {
    override val language = Language.MARATHI
    override val glyph = "म"
    override val ocrScript = OcrScript.DEVANAGARI
    override val wikidataId = "Q1571"
    override val languageTag = "mr"
    override val fontSample = "ज्ञानेश्वरांनी श्रद्धेने ओव्या लिहिल्या. शाळेत तऱ्हेतऱ्हेची फुले फुलली."
    override val translationSample = "आज हवामान छान आहे. फिरायला जाऊया?"
    override val audioTestWord = "पाणी"
    override val pronunciationSample = "पाणी → /paːɳiː/"
    override val flagCountries = listOf("in")

    /**
     * The phone's default font falls back to its Devanagari one by itself; Noto Serif Devanagari is reached by name.
     * The range is Google Fonts' Devanagari subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifDevanagari-Regular", "Noto Serif Devanagari Regular", "Noto Serif Devanagari"),
        unicodeRange = "U+0900-097F, U+1CD0-1CF9, U+200C-200D, U+20A8, U+20B9, U+20F0, U+25CC, U+A830-A839, U+A8E0-A8FF",
        aliases = emptyMap(),
    )

    /** The danda । and the double danda ॥ end a sentence, as in Hindi, though most Marathi text ends it with a period. */
    override val sentenceTerminators = setOf('।', '॥', '.', '!', '?', '…', '\n')

    override val quotePairs = mapOf('“' to '”', '‘' to '’', '(' to ')', '[' to ']')

    /**
     * Wiktionary's verb tables give their labels as form headwords of every tabled verb (होकारार्थी "affirmative",
     * नकारार्थी "negative", 238 verbs each); the import leaves them out. Hindi's nukta and candrabindu keys are not
     * used: they change 10 headwords and find nothing more.
     */
    override val headwordMarks = HeadwordMarks(marks = "", bases = "", droppedForms = TABLE_LABELS)

    /** Wiktionary lists the forms of its tabled verbs; the app's endings are for the words it gives no forms of. */
    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "डॉ." डॉक्टर, "प्रा." and "प्रो." प्राध्यापक and प्रोफेसर, "श्री." and "सौ.", "कु." कुमार or कुमारी, "उदा." उदाहरणार्थ,
     * "सायं." सायंकाळी, "इ.स." and "इ.स.पू." इसवी सन and its पूर्व, "कि.मी." and "से.मी.", "ह.भ.प." हरिभक्तपरायण, "प.पू."
     * परमपूज्य, "क्र." क्रमांक, "रु." रुपये, "पृ." पृष्ठ, "स्व." स्वर्गीय; and the names of Latin letters as initials ("ए. पी.
     * जे. अब्दुल कलाम"), but आय, which is a word ("income") as well. One-syllable abbreviations hold the sentence by the
     * syllable rule too ([syllableInitialScript]).
     */
    override val abbreviations = setOf(
        "डॉ", "प्रा", "प्रो", "श्री", "सौ", "कु", "उदा", "सायं", "इ.स", "इ.स.पू", "कि.मी", "से.मी", "ह.भ.प", "प.पू", "क्र",
        "रु", "पृ", "स्व",
    ) + LETTER_NAMES - setOf("आय")

    /** Names of Latin letters, joined by periods in acronyms and initials: "आय.आय.टी.", "एस.टी.". */
    override val initials = LETTER_NAMES

    /**
     * Names are written with syllables for initials ("पु. ल. देशपांडे", "ग. दि. माडगूळकर") and titles as chains of them
     * ("ह.भ.प.", "प.पू."); in news and Wikipedia nearly every one-syllable word before a period is one.
     */
    override val syllableInitialScript = Character.UnicodeScript.DEVANAGARI

    /**
     * Words of one syllable that end sentences in dialogue: हो "yes", the pronouns, and short imperatives ("चहा घ्या.").
     */
    override val syllableSentenceEnds = setOf(
        "हो", "तो", "ती", "मी", "तू", "जा", "ये", "घे", "दे", "ने", "या", "घ्या", "द्या", "न्या", "व्हा",
    )
}

/** Marathi spells Latin letters as English says them: आय, व्ही, वाय, झेड, where Hindi writes आई, वी, वाई, ज़ेड. */
private val LETTER_NAMES = setOf(
    "ए", "बी", "सी", "डी", "ई", "एफ", "जी", "एच", "आय", "जे", "के", "एल", "एम", "एन", "ओ", "पी", "क्यू", "आर", "एस", "टी",
    "यू", "व्ही", "डब्ल्यू", "एक्स", "वाय", "झेड",
)

/** The labels of Wiktionary's Marathi verb tables that its export gives as form headwords. */
private val TABLE_LABELS = setOf(
    "होकारार्थी", "नकारार्थी", "Affirmative", "Negative", "Progressive", "Oblique Perfective", "+", "m f n",
)
