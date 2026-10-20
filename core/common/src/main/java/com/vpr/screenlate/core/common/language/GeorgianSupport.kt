package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Georgian, in the Mkhedruli script. Wiktionary's form rows cover most nouns and verbs; Yomitan's Georgian rules (case
 * and plural endings cut, -ნ → -ნი) also match its entries, which give no part of speech, when the word left has three
 * letters at least. Headings in Mtavruli capitals are lowercased to the ordinary letters (ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ → საქართველო).
 * Words run long, so a lookup considers 24 characters by default. Dictionaries give no readings; Wiktionary has
 * recordings of more common words than Lingua Libre.
 */
object GeorgianSupport : SpacedLanguageSupport() {
    override val language = Language.GEORGIAN
    override val glyph = "ქ"
    override val ocrScript = OcrScript.GEORGIAN
    override val wikidataId = "Q8108"
    override val languageTag = "ka"
    override val fontSample = "ჟღალმა ძაღლმა ჭაობში ყვავი შეაშინა. წყალი ფხიზლად ჩქეფს."
    override val translationSample = "დღეს კარგი ამინდია. გავისეირნოთ?"
    override val audioTestWord = "წყალი"
    override val pronunciationSample = "წყალი → [t͡sʼqʼali]"
    override val flagCountries = listOf("ge")
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")
    override val defaultScanLength = 24

    /**
     * The phone's default font falls back to its Georgian one by itself; Noto Serif Georgian is reached by name. The
     * range is Google Fonts' Georgian subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifGeorgian-Regular", "Noto Serif Georgian Regular", "Noto Serif Georgian"),
        unicodeRange = "U+0589, U+10A0-10FF, U+1C90-1CBA, U+1CBD-1CBF, U+205A, U+2D00-2D2F, U+2E31",
        aliases = emptyMap(),
    )

    /** „…“ and «…». */
    override val quotePairs = mapOf('„' to '“', '«' to '»', '(' to ')', '[' to ']')

    /**
     * "ე.ი." ესე იგი, "მაგ." მაგალითად, "წ." წელი ("1991 წ."), "ძვ. წ." ძველი წელთაღრიცხვით, "ქ." ქალაქი
     * ("ქ. თბილისი"), "გვ." გვერდი, "ტ." ტომი, "იხ." იხილეთ, "შდრ." შეადარეთ, "პროფ." and "აკად." პროფესორი and
     * აკადემიკოსი. "ა.შ." (and so on) usually ends a sentence and is left out.
     */
    override val abbreviations = setOf("ე.ი", "მაგ", "წ", "ძვ", "ძვ.წ", "ქ", "გვ", "ტ", "იხ", "შდრ", "პროფ", "აკად")
}
