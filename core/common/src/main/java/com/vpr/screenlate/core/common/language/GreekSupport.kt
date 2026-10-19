package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan's Greek code drops a second acute accent (πρόσωπό μου) and undoes the prefix ξανα-. Headwords carry their
 * tonos while capitals and quick typing leave it out, so the import also indexes them without it ([headwordMarks]): a
 * word written without any tonos finds those with one (σκυλος, ΣΚΥΛΟΣ → σκύλος), a word with one only itself.
 * Dictionaries give no readings. Wiktionary has almost no Greek recordings: Lingua Libre's come first, the phone's
 * voice reads the rest.
 */
object GreekSupport : SpacedLanguageSupport() {
    override val language = Language.GREEK
    override val glyph = "Ω"
    override val ocrScript = OcrScript.GREEK
    override val wikidataId = "Q9129"
    override val languageTag = "el"
    override val fontSample = "Ξεσκεπάζω την ψυχοφθόρα βδελυγμία. Πρωτεΐνες στον Ταΰγετο;"
    override val translationSample = "Σήμερα ο καιρός είναι ωραίος. Πάμε βόλτα;"
    override val audioTestWord = "νερό"
    override val pronunciationSample = "νερό → [neˈɾo]"
    override val flagCountries = listOf("gr")

    /** The phone's sans-serif and Noto Serif have the Greek letters, the polytonic ones (ἄλλα) included. */
    override val systemFonts = LATIN_SYSTEM_FONTS.copy(
        unicodeRange = "U+0000-024F, U+0300-036F, U+0370-03FF, U+1E00-1FFF, U+2000-206F",
    )
    override val fontSubsets = setOf("latin", "greek")

    /** The Greek question mark is ; (U+037E, which NFC writes as ;); the ano teleia · is a pause and ends nothing. */
    override val sentenceTerminators = setOf('.', '!', '?', '…', '\n', ';', '\u037E')

    /** «…» outside, “…” inside. */
    override val quotePairs = mapOf('«' to '»', '“' to '”', '(' to ')', '[' to ']')

    override val headwordMarks = HeadwordMarks(marks = "\u0301", bases = "αεηιουω", everyHeadword = true)

    /** Words elided before a vowel and written joined to it: σ'αγαπώ, απ'το, γι'αυτό. */
    override val elisions = setOf("σ", "τ", "μ", "θ", "ν", "κ", "απ", "γι")

    /** wordfreq writes every sigma as σ (τησ, σ'αυτούσ); dictionaries end words with ς. */
    override fun frequencyWord(word: String): String = FINAL_SIGMA.replace(word, "ς")

    /** A σ after a letter that no letter, mark or apostrophe follows (σ'αγαπώ keeps its σ). */
    private val FINAL_SIGMA = Regex("""(?<=\p{L})σ(?![\p{L}\p{M}'’])""")

    /**
     * "π.χ." is για παράδειγμα (and π.Χ., before Christ, which lowercase spells alike), "αι." αιώνας ("τον 19ο αι."),
     * "κ." κύριος, "ν." νόμος, "χιλ.", "εκατ." and "δισ." thousands, millions and billions. "κ.λπ.", "κ.τ.λ.", "κ.ά.",
     * "κ.ο.κ." (and so on) and "μ.Χ." (after Christ) usually end a sentence and are left out, as are units.
     */
    override val abbreviations = setOf(
        "π.χ", "λ.χ", "δηλ", "βλ", "πρβλ", "κ", "σελ", "σσ", "αρ", "αριθ", "τηλ", "οδ", "λεωφ", "πλ", "ν", "αρθ",
        "εικ", "πίν", "κεφ", "παρ", "σημ", "εκδ", "αι", "χιλ", "εκατ", "δισ", "π.μ", "μ.μ", "ό.π", "ο.π", "α.ε",
        "καθ", "δρ", "αγ", "υπ", "αγγλ", "γαλλ", "γερμ", "λατ", "αρχ",
    )
}
