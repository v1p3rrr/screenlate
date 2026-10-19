package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Hindi, in Devanagari. Yomitan has no Hindi rules; the language's bundle composes the text as headwords are (NFC)
 * and reads candrabindu as anusvara. Wiktionary writes nukta (पढ़ना) and candrabindu (हूँ) where much text writes
 * neither (पढना, हूं), so the import also indexes headwords and forms without nukta and with anusvara
 * ([headwordMarks]): such text finds them, while a word written with nukta finds only itself. Dictionaries give no
 * readings. Lingua Libre has recordings of most common words, Wiktionary almost none.
 */
object HindiSupport : SpacedLanguageSupport() {
    override val language = Language.HINDI
    override val glyph = "हि"
    override val ocrScript = OcrScript.DEVANAGARI
    override val wikidataId = "Q1568"
    override val languageTag = "hi"
    override val fontSample = "ज्ञानी क्षत्रिय ने श्रद्धा से द्वार खोला। ऋषि का हृदय प्रसन्न हुआ।"
    override val translationSample = "आज मौसम अच्छा है। घूमने चलें?"
    override val audioTestWord = "पानी"
    override val pronunciationSample = "पानी → /pɑː.niː/"
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

    /** The danda । ends a sentence and the double danda ॥ a verse; many texts end sentences with a period instead. */
    override val sentenceTerminators = setOf('।', '॥', '.', '!', '?', '…', '\n')

    override val quotePairs = mapOf('“' to '”', '‘' to '’', '(' to ')', '[' to ']')

    override val headwordMarks = HeadwordMarks(
        marks = NUKTA,
        bases = CONSONANTS,
        everyHeadword = true,
        replacements = mapOf(CANDRABINDU to ANUSVARA),
    )

    /** wordfreq mostly writes anusvara (हूं) where headwords write candrabindu (हूँ); both spellings share the rank. */
    override fun frequencySpellings(word: String): List<String> =
        listOf(word.replace(ANUSVARA, CANDRABINDU), word.replace(CANDRABINDU, ANUSVARA)).filter { it != word }.distinct()

    /**
     * "डॉ." डॉक्टर, "प्रो." प्रोफ़ेसर, "सं." संपादक or संवत्, "ई." and "ई.पू." ईसवी and ईसा पूर्व, "पृ." पृष्ठ, "क्र." क्रमांक,
     * "रु." रुपये, "लि." and "कं." लिमिटेड and कंपनी, "कि.मी." and "से.मी.", "पं." पंडित, "स्व." स्वर्गीय; and the names of Latin
     * letters as initials ("बी. आर. अम्बेडकर"), but आई, जी and पी, which are words that end sentences as well (वह आई,
     * उसने चाय पी).
     */
    override val abbreviations = setOf(
        "डॉ", "प्रो", "सं", "ई", "ई.पू", "पृ", "क्र", "रु", "लि", "कं", "कि.मी", "से.मी", "पं", "स्व",
    ) + LETTER_NAMES - setOf("आई", "जी", "पी")

    /** Names of Latin letters, joined by periods in acronyms and initials: "ए.पी.जे.", "आई.आई.टी.". */
    override val initials = LETTER_NAMES
}

private val LETTER_NAMES = setOf(
    "ए", "बी", "सी", "डी", "ई", "एफ", "जी", "एच", "आई", "जे", "के", "एल", "एम", "एन", "ओ", "पी", "क्यू", "आर", "एस", "टी",
    "यू", "वी", "डब्ल्यू", "एक्स", "वाई", "जेड", "ज़ेड",
)

private const val NUKTA = "\u093C"
private const val CANDRABINDU = "\u0901"
private const val ANUSVARA = "\u0902"

/** The consonants क to ह, which nukta follows in the decomposed form of क़, ख़, ग़, ज़, ड़, ढ़, फ़ and य़. */
private val CONSONANTS = (0x0915..0x0939).joinToString("") { it.toChar().toString() }
