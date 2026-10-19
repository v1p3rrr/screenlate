package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has no Malay descriptor; the bundle takes Indonesian's text processors (`scripts/yomitan-language`).
 * Wiktionary titles name Malaysia (`Ms-MY-kucing.ogg`) or nothing, so there are no audio regions.
 */
object MalaySupport : SpacedLanguageSupport() {
    override val language = Language.MALAY
    override val glyph = "MS"
    override val ocrScript = OcrScript.LATIN
    override val iso639Part3 = "msa"
    override val wikidataId = "Q9237"
    override val languageTag = "ms"
    override val fontSample = "Xavier dan Zakiah membeli kuih, sayur dan teh di pasar Johor; Faqih menunggu dalam van."
    override val translationSample = "Cuaca hari ini baik. Mahu pergi berjalan-jalan?"
    override val audioTestWord = "air"
    override val pronunciationSample = "air → /ˈair/"
    override val flagCountries = listOf("my")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS

    /**
     * Titles and companies: Encik, Puan, Tuan, Haji, Sendirian Berhad ("Sdn. Bhd."); "Jln." is jalan, "hlm." halaman.
     * "dll." is left out: it ends sentences as often as not.
     */
    override val abbreviations = setOf(
        "jln", "en", "pn", "tn", "hj", "sdn", "bhd", "hlm", "ms", "no", "dsb", "dr", "prof", "sdr", "tel",
    )
}
