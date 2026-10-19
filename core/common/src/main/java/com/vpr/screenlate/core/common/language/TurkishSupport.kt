package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Wiktionary recordings of Turkish name no regions, so there are no audio regions. Dotted and dotless i are matched in
 * the dictionary engine's case processors (İ ↔ i, I ↔ ı). A suffix after an apostrophe (İstanbul'da) belongs to the
 * word before it, so a word aimed there is looked up from the name.
 */
object TurkishSupport : SpacedLanguageSupport() {
    override val language = Language.TURKISH
    override val glyph = "Ğ"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q256"
    override val languageTag = "tr"
    override val fontSample = "Pijamalı hasta yağız şoföre çabucak güvendi. İstanbul, Iğdır, ılık."
    override val translationSample = "Bugün hava çok güzel. Yürüyüşe çıkalım mı?"
    override val audioTestWord = "su"
    override val flagCountries = listOf("tr")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS

    override val quotePairs = super.quotePairs + ('«' to '»')

    /** Ordinals in digits take a period: "15. yüzyıl", "3. sınıf". */
    override val ordinalPeriod = true

    /** ŞTİ. and DOÇ. match their lowercase forms only under Turkish case rules (İ → i, I → ı). */
    override val caseLocale: Locale = Locale.forLanguageTag("tr")

    /** "s" is sayfa ("s. 5"); month abbreviations are left out, as ara and kas are words. */
    override val abbreviations = setOf(
        "vb", "vs", "bkz", "örn", "yy", "dr", "prof", "doç", "av", "sn", "s", "no", "cad", "sok", "mah", "apt", "ltd",
        "şti", "a.ş", "t.c", "m.ö", "m.s", "çev", "haz", "yay", "bl", "tel", "vd", "st", "dk", "sa",
    )
}
