package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Arabic (Modern Standard), written right to left. Yomitan's rules undo its prefixes, suffixes and verb forms; nouns
 * rules also reach Wiktionary's adjectives and adverbs, which give no part of speech. Headwords are written by the norm
 * (أكبر, إسلام, على, لغة) and running text often without hamza, with ي for a final ى and ه for a final ة, so the
 * import keys headwords under those spellings as well ([headwordMarks]): such text finds them, while a word written
 * with hamza finds only itself. Vowel marks (harakat) are dropped from the text and from the lemmas forms name.
 * Dictionaries give the vowelled spelling as the reading. Wiktionary has recordings of more common words than Lingua
 * Libre; Lingua Libre's dialect recordings are left out.
 */
object ArabicSupport : SpacedLanguageSupport() {
    override val language = Language.ARABIC
    override val glyph = "ع"
    override val ocrScript = OcrScript.ARABIC
    override val wikidataId = "Q13955"
    override val languageTag = "ar"
    override val rightToLeft = true
    override val fontSample = "قَرَأَ الطّالِبُ الكِتابَ في المَكتَبةِ؛ «ما أجمَلَ اللغةَ!» ١٢٣ ٤٥٦"
    override val translationSample = "الطقس جميل اليوم. هل نخرج في نزهة؟"
    override val audioTestWord = "كتاب"
    override val pronunciationSample = "كتاب → /ki.taːb/"
    override val flagCountries = listOf("sa")
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** Vowelled words with a prefix run long: 18% of them pass 16 characters, 0.2% pass 24. */
    override val defaultScanLength = 24

    /** The Arabic question mark ؟ ends a sentence; the Arabic comma ، and semicolon ؛ do not. */
    override val sentenceTerminators = setOf('.', '!', '?', '؟', '…', '\n')

    override val quotePairs = mapOf('«' to '»', '“' to '”', '(' to ')', '[' to ']')

    /**
     * "د." دكتور, "أ." أستاذ, "ص." صفحة, "ج." جزء and "ق.م." قبل الميلاد before a name or a number; "م." and "هـ." after
     * a year are left out, as they often end the sentence.
     */
    override val abbreviations = setOf("د", "أ", "ص", "ج", "ق.م")

    /** Downloadable fonts made for the Arabic script, which Persian shares. */
    override val fontSubsets = setOf(ARABIC_FONT_SUBSET)
    override val fontLetters = "ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي"

    /**
     * The phone's default font falls back to Noto Naskh Arabic by itself, which is also its book face. Android's
     * regular file names itself NotoNaskhArabic and Noto Naskh Arabic (PostScript and full name, API 37); Google
     * Fonts' copy adds "Regular". The range is Google Fonts' Arabic subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoNaskhArabic", "Noto Naskh Arabic", "NotoNaskhArabic-Regular", "Noto Naskh Arabic Regular"),
        unicodeRange = ARABIC_UNICODE_RANGE,
        aliases = emptyMap(),
    )

    /**
     * Headwords and forms are also found with a final ى and ة written ي and ه, and further with their hamza alefs
     * written as a bare alif; the lemmas forms name lose their vowel marks (كِتَاب), as headwords have none. Romanized
     * forms (ḡarafāt) are left out.
     */
    override val headwordMarks = HeadwordMarks(
        marks = ARABIC_VOWEL_MARKS,
        bases = ARABIC_LETTERS,
        everyHeadword = true,
        replacements = mapOf("ى" to "ي", "ة" to "ه"),
        furtherReplacements = mapOf("أ" to "ا", "إ" to "ا", "آ" to "ا", "ٱ" to "ا"),
        lemmasAsWritten = false,
        formsInScript = true,
    )

    /**
     * Wiktionary's words for Arabic forms that name other languages' categories elsewhere: `elative` is the أفعل
     * form, not the Finnish case; `non-past` and `subjunctive` are المضارع and المنصوب to an Arabic reader.
     */
    override val formTagAliases = mapOf(
        "elative" to "arabic-elative",
        "non-past" to "arabic-non-past",
        "subjunctive" to "arabic-subjunctive",
    )

    /** 4% of Wiktionary's Arabic recordings and 17% of Lingua Libre's are titled vowelled (`Ar-كِتَابٌ.oga`). */
    override val recordingMarks = ARABIC_VOWEL_MARKS

    /** The vowelled word (كِتَاب), the dictionaries' reading, is Arabic text too. */
    override val rightToLeftMarkers = RIGHT_TO_LEFT_TEXT_MARKERS + "reading"

    /** Recognizers trained on Persian text give its kaf and yeh (ک, ی) in Arabic words, which write ك and ي. */
    override fun ocrWord(text: String): String = text.replace('ک', 'ك').replace('ی', 'ي')
}

/** The Google Fonts subset of the Arabic script, which Arabic and Persian share. */
internal const val ARABIC_FONT_SUBSET = "arabic"

/** Google Fonts' `arabic` subset. */
internal const val ARABIC_UNICODE_RANGE = "U+0600-06FF, U+0750-077F, U+0870-088E, U+0890-0891, U+0897-08E1, " +
    "U+08E3-08FF, U+200C-200E, U+2010-2011, U+204F, U+2E41, U+FB50-FDFF, U+FE70-FE74, U+FE76-FEFC, U+102E0-102FB, " +
    "U+10E60-10E7E, U+10EC2-10EC4, U+10EFC-10EFF, U+1EE00-1EEFF"

/**
 * The vowel marks of the Arabic script that dictionaries leave out of headwords (fathatan to sukun, the dagger alef,
 * the small fatha, damma and kasra); not the hamza above and below, which NFD splits off أ إ ؤ ئ.
 */
internal const val ARABIC_VOWEL_MARKS = "\u0618\u0619\u061A\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0656\u0670"

/** The letters of the Arabic script block, Persian and Urdu ones included. */
internal val ARABIC_LETTERS: String = buildString {
    for (codePoint in 0x0620..0x06FF) if (Character.isLetter(codePoint)) appendCodePoint(codePoint)
}
