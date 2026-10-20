package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Armenian. A sentence ends at the full stop ։ (and the colon OCR and keyboards give for it), not at the period, which
 * Armenian uses as a colon. The emphasis, exclamation and question marks sit on the stressed vowel inside a word
 * (ինչո՞ւ, կարդա՛): they are part of the word under the aim, the lookup drops them, and the import keys Wiktionary's
 * forms written with them without them as well ([headwordMarks]). The ligature և is also looked up for եվ and եւ
 * (capitals have none: ԵՐԵՎԱՆ finds Երևան), and the classical spellings of Wiktionary's "orthography spelling" rows
 * find their word. Words run long, so a lookup considers 24 characters by default. Dictionaries give no readings;
 * Wiktionary has recordings of more common words than Lingua Libre.
 */
object ArmenianSupport : SpacedLanguageSupport() {
    override val language = Language.ARMENIAN
    override val glyph = "Հ"
    override val ocrScript = OcrScript.ARMENIAN
    override val wikidataId = "Q8785"
    override val languageTag = "hy"
    override val fontSample = "Երևանը Հայաստանի մայրաքաղաքն է։ Արարատը երևում է հեռվից։"
    override val translationSample = "Այսօր լավ եղանակ է։ Գնա՞նք զբոսնելու։"
    override val audioTestWord = "ջուր"
    override val pronunciationSample = "ջուր → [d͡ʒuɾ]"
    override val flagCountries = listOf("am")
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
    override val defaultScanLength = 24
    override val sentenceTerminators = setOf('։', ':', '!', '?', '…', '\n')
    override val wordMarks = "\u055A\u055B\u055C\u055E"

    /** The marks after any Armenian letter. */
    override val headwordMarks = HeadwordMarks(
        marks = "\u055B\u055C\u055E",
        bases = "աբգդեզէըթժիլխծկհձղճմյնշոչպջռսվտրցւփքօֆև",
    )

    /**
     * The phone's default font falls back to its Armenian one by itself; Noto Serif Armenian is reached by name. The
     * range is Google Fonts' Armenian subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifArmenian-Regular", "Noto Serif Armenian Regular", "Noto Serif Armenian"),
        unicodeRange = "U+0308, U+0530-058F, U+2010, U+2024, U+25CC, U+FB13-FB17",
        aliases = emptyMap(),
    )

    /** «…». */
    override val quotePairs = mapOf('«' to '»', '(' to ')', '[' to ']')

    /** The full stop and the colon end a sentence unless a letter or digit follows at once (10:30). */
    override fun endsSentence(text: String, index: Int): Boolean {
        val character = text[index]
        if (character !in sentenceTerminators) return false
        if (character == ':' || character == '։') return index + 1 >= text.length || !text[index + 1].isLetterOrDigit()
        return true
    }
}
