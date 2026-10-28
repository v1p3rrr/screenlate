package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Azerbaijani. Yomitan has nothing for it; the language bundle borrows Turkish's capitalization, whose case pairs
 * Azerbaijani writes too (İ ↔ i, I ↔ ı), and adds the app's own Turkic ending rules (kitablarımızda → kitab, gəlirəm →
 * gəlmək). Wiktionary's English edition lists the forms of nouns and verbs, those of the two-letter stems the rules
 * leave alone among them (olur, edir). Words from OCR are also tried with ə for e and ı for i, which recognition reads
 * them as (seher → səhər); app text and typed text are looked up as written. An ending after a number or an
 * abbreviation in capitals with a hyphen (2020-ci, ABŞ-da) is also looked up with the hyphen.
 */
object AzerbaijaniSupport : SpacedLanguageSupport() {
    override val language = Language.AZERBAIJANI
    override val glyph = "Ə"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9292"
    override val languageTag = "az"
    override val fontSample = "İsmayıllıda güclü külək əsir, göl və çay qırağında ağaclar yıxılıb, şəhər işıqsız qalıb."
    override val translationSample = "Bu gün hava çox gözəldir. Gəzməyə gedək?"
    override val audioTestWord = "su"
    override val pronunciationSample = "kitab → [ciˈtɑb]"
    override val flagCountries = listOf("az")
    override val systemFonts = LATIN_SYSTEM_FONTS

    /** ə Ə, ğ, ı and İ are in the fonts' Latin extension. */
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS

    /** İ is the capital of i. */
    override val fontLetters = "çəğıİöşü"

    /** Lingua Libre had recordings of nineteen of twenty common words checked, Wiktionary of two. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** English quotes mostly, «…» too. */
    override val quotePairs = super.quotePairs + ('«' to '»')

    /** Abbreviations in capitals lower by the Turkish case rules Azerbaijani shares (İ → i, I → ı). */
    override val caseLocale: Locale = Locale.forLanguageTag("az")

    /** The ending rules would put bel "waist" (dative -ə) beside belə "so". */
    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "məs." (məsələn, for example); before a name "prof.", "dos." (dosent) and "akad." (akademik); in addresses, before
     * the street's name or the number, "şəh." (şəhəri, city) and "küç." (küçəsi, street): Bakı şəh. Nizami küç. 5.
     */
    override val abbreviations = setOf("məs", "prof", "dos", "akad", "şəh", "küç")

    /**
     * Often a sentence's last word: "və s." (və sairə, and so on), "və b." (və başqaları, and others); after a number
     * "mln.", "mlrd." and the eras "e.ə.", "b.e.ə." and "b.e.".
     */
    override val abbreviationsBeforeLowercase = setOf("s", "b", "mln", "mlrd", "e.ə", "b.e.ə", "b.e")

    /** Endings after a number or an abbreviation in capitals: 2020-ci, 5-də, ABŞ-da, AB-nin. */
    override val hyphenedEndings = HyphenedEndings.AFTER_NUMBERS_AND_CAPITALS
}
