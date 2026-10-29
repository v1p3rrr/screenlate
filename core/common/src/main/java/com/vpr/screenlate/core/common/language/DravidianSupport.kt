package com.vpr.screenlate.core.common.language

/**
 * Rules Tamil, Telugu and Kannada share: spaced languages in scripts of their own, written with Western punctuation
 * and quotes. The language bundles compose their text as the headwords are (NFC).
 */
abstract class DravidianSupport : SpacedLanguageSupport() {
    override val quotePairs = mapOf('“' to '”', '‘' to '’', '(' to ')', '[' to ']')

    /**
     * Without the zero-width joiners: Tesseract's Tamil model writes a non-joiner after every virama (after the pulli of
     * பொங்கல்), which no headword has. Typed and copied text keeps them.
     */
    override fun ocrLines(lines: List<List<String>>): List<List<String>> =
        super.ocrLines(lines).map { line -> line.map { word -> word.filterNot { it == '\u200C' || it == '\u200D' } } }
}
