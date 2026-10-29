package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Kannada, in its own script. Yomitan's Kannada descriptor has no text processors or rules: the language's bundle
 * composes the text as headwords are (NFC), drops the zero-width joiners and spaces and soft hyphens inside words, and
 * adds the app's own case endings, plural and particles (ಮರದಲ್ಲಿ → ಮರ, ಮನೆಯಲ್ಲೂ → ಮನೆ), as Wiktionary lists some case
 * forms of about a thousand nouns. Its form rows written with -ಉ before another vowel sign (ಹೊರಡುೋಣ) are keyed without
 * it (ಹೊರಡೋಣ). Dictionaries give no readings. Lingua Libre has recordings of most common words, Wiktionary of about as
 * many.
 */
object KannadaSupport : DravidianSupport() {
    override val language = Language.KANNADA
    override val glyph = "ಕ"
    override val ocrScript = OcrScript.KANNADA
    override val wikidataId = "Q33673"
    override val languageTag = "kn"
    override val fontSample = "ಶ್ರೀ ಲಕ್ಷ್ಮಿ ಕ್ಷೇತ್ರದ ಹಬ್ಬದಲ್ಲಿ ಜ್ಞಾನಿಗಳ ಹಾಡುಗಳನ್ನು ಹಾಡಿದಳು. ಹೆಣ್ಣುಮಕ್ಕಳು ಅಂಗಳದಲ್ಲಿ ರಂಗೋಲಿ ಹಾಕಿದರು."
    override val translationSample = "ಇಂದು ಹವಾಮಾನ ಚೆನ್ನಾಗಿದೆ. ನಡೆಯಲು ಹೋಗೋಣವೇ?"
    override val audioTestWord = "ನೀರು"
    override val pronunciationSample = "ನೀರು → [niːɾu]"
    override val flagCountries = listOf("in")
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** The danda ends quoted Sanskrit verses (73 of 100k news sentences). */
    override val sentenceTerminators = setOf('.', '!', '?', '…', '\n', '।')

    /**
     * The phone's default font falls back to its Kannada one by itself; Noto Serif Kannada is reached by name. The
     * range is Google Fonts' Kannada subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifKannada-Regular", "Noto Serif Kannada Regular", "Noto Serif Kannada"),
        unicodeRange = "U+0951-0952, U+0964-0965, U+0B83, U+0C80-0CF3, U+1CD0, U+1CD2-1CD3, U+1CDA, U+1CF2, U+1CF4, " +
            "U+200C-200D, U+20B9, U+25CC, U+A830-A835",
        aliases = emptyMap(),
    )

    /** A few headwords carry a joiner, which text may leave out; each is also found without it. */
    override val headwordMarks = HeadwordMarks(
        marks = "",
        bases = "",
        everyHeadword = true,
        replacements = mapOf("\u200C" to "", "\u200D" to ""),
    )

    /** Wiktionary lists some case forms of its tabled nouns; the app's endings are for the words it gives none of. */
    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "ಡಾ." ಡಾಕ್ಟರ್, "ಪ್ರೊ." ಪ್ರೊಫೆಸರ್, "ಶ್ರೀ." and "ಶ್ರೀಮತಿ.", "ಕು." ಕುಮಾರಿ, "ಕ್ರಿ.ಪೂ." and "ಕ್ರಿ.ಶ.", "ಕಿ.ಮೀ.", "ಚ." ಚದರ, "ಮೀ.",
     * "ಸೆಂ.ಮೀ.", "ರೂ." and "ರು." ರೂಪಾಯಿ, "ಶೇ." ಶೇಕಡ, "ನಂ." ನಂಬರ್, "ಪು." ಪುಟ, "ಸಂ." ಸಂಖ್ಯೆ; the months before a date that are no word
     * ("ಜ. 15", "ಡಿ. 3"); and the names of Latin letters as initials ("ಎಚ್.ಡಿ. ದೇವೇಗೌಡ"). One-syllable abbreviations hold
     * the sentence by the syllable rule too ([syllableInitialScript]).
     */
    override val abbreviations = setOf(
        "ಡಾ", "ಪ್ರೊ", "ಶ್ರೀ", "ಶ್ರೀಮತಿ", "ಕು", "ಕ್ರಿ.ಪೂ", "ಕ್ರಿ.ಶ", "ಕಿ.ಮೀ", "ಚ", "ಮೀ", "ಸೆಂ.ಮೀ", "ರೂ", "ರು", "ಶೇ", "ನಂ", "ಪು",
        "ಸಂ", "ಜ", "ಫೆ", "ಮಾ", "ಜೂ", "ಜು", "ಸೆ", "ಡಿ",
    ) + LETTER_NAMES

    /**
     * The months before a date that are words or vowels as well (ಅ. ಅಕ್ಟೋಬರ್, ಆ. ಆಗಸ್ಟ್, ನ. ನವೆಂಬರ್, ಏ. ಏಪ್ರಿಲ್): they hold the
     * sentence only before a number (Kannada has no lowercase), so a sentence that trails off with "ಆ.." ends.
     */
    override val abbreviationsBeforeLowercase = setOf("ಅ", "ಆ", "ನ", "ಏ")

    /** Names of Latin letters, joined by periods in acronyms and initials: "ಬಿ.ಜೆ.ಪಿ.", "ಎಚ್.ಡಿ.". */
    override val initials = LETTER_NAMES

    /** Names are written with syllables for initials ("ಕು.ವೆಂ.ಪು.") and bodies as chains of them ("ಕ.ಸಾ.ಪ.", "ದ.ಕ."). */
    override val syllableInitialScript = Character.UnicodeScript.KANNADA

    /** A word of one syllable that ends sentences in dialogue: the imperative ಬಾ "come". */
    override val syllableSentenceEnds = setOf("ಬಾ")
}

/**
 * Kannada spells Latin letters as English says them, most two ways (ಬಿ and ಬೀ, ಎಚ್ and ಹೆಚ್); A is ಎ, as ಏ is also the
 * month ಏಪ್ರಿಲ್ ([KannadaSupport.abbreviationsBeforeLowercase]).
 */
private val LETTER_NAMES = setOf(
    "ಎ", "ಬಿ", "ಬೀ", "ಸಿ", "ಸೀ", "ಡಿ", "ಡೀ", "ಇ", "ಈ", "ಎಫ್", "ಜಿ", "ಜೀ", "ಎಚ್", "ಹೆಚ್", "ಐ", "ಜೆ", "ಜೇ", "ಕೆ", "ಕೇ", "ಎಲ್",
    "ಎಂ", "ಎಮ್", "ಎನ್", "ಒ", "ಓ", "ಪಿ", "ಪೀ", "ಕ್ಯೂ", "ಕ್ಯು", "ಆರ್", "ಎಸ್", "ಟಿ", "ಟೀ", "ಯು", "ಯೂ", "ವಿ", "ವೀ", "ಡಬ್ಲ್ಯು",
    "ಡಬ್ಲ್ಯೂ", "ಎಕ್ಸ್", "ವೈ", "ಜೆಡ್", "ಝಡ್",
)
