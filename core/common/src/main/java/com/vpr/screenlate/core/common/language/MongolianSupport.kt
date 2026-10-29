package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has only capitalization for Mongolian; the language bundle adds the app's own endings (cases, plural,
 * possessives, verb forms, every vowel-harmony variant) with the stem rules of the dictionary form in -х. Cyrillic only:
 * the traditional script is neither recognized nor looked up. Few recordings and no voice exist, so the speaker button
 * mostly says there is no audio.
 */
object MongolianSupport : SpacedLanguageSupport() {
    override val language = Language.MONGOLIAN
    override val glyph = "М"
    override val codeLabel = "МН"
    override val ocrScript = OcrScript.CYRILLIC
    override val wikidataId = "Q9246"
    override val languageTag = "mn"
    override val fontSample = "Улаанбаатар хотод өнөөдөр цас орж, хүйтэн салхи үлээв."
    override val translationSample = "Өнөөдөр цаг агаар сайхан байна. Гадуур зугаалах уу?"
    override val audioTestWord = "ус"
    override val pronunciationSample = "ус → [ʊs]"
    override val flagCountries = listOf("mn")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS

    /** ө and ү are in the fonts' Cyrillic extension. */
    override val fontSubsets = setOf("latin", "cyrillic", "cyrillic-ext")
    override val fontLetters = "абвгдеёжзийклмноөпрстуүфхцчшщъыьэюя"

    /** News write “…” and «…». */
    override val quotePairs = mapOf('“' to '”', '«' to '»', '(' to ')', '[' to ']')

    /** Wiktionary's forms of Russian loans carry their stress as an acute. */
    override val headwordMarks = HeadwordMarks(marks = "́", bases = "аэиоуөүыяеёю")
}
