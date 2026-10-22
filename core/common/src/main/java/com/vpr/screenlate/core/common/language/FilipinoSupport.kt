package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Filipino, the standard form of Tagalog. Yomitan, Wiktionary and Lingua Libre call it Tagalog (tl, tgl); Android,
 * the on-device models, wordfreq and the voices call it Filipino (fil). Wiktionary's few recordings name no regions.
 */
object FilipinoSupport : SpacedLanguageSupport() {
    override val language = Language.FILIPINO
    override val glyph = "Ng"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q34057"
    override val languageTag = "fil"
    override val speechTag = "fil"
    override val fontSample = "Mabilis na lumakad si Javier sa Parañaque para bumili ng quesong puti at dyaryo."
    override val translationSample = "Maganda ang panahon ngayon. Gusto mo bang mamasyal?"
    override val audioTestWord = "tubig"
    override val pronunciationSample = "tubig → /tuˈbiɡ/"
    override val flagCountries = listOf("ph")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS
    override val fontLetters = "ñ"

    /** Both names, as the systems differ: "Filipino (Tagalog)". */
    override fun nameIn(locale: Locale): String {
        fun name(code: String) = Locale.forLanguageTag(code).getDisplayLanguage(locale)
        return "${name("fil")} (${name("tl")})"
    }

    /**
     * Words name their Baybayin spelling (basa → ᜊᜐ), which leads only back to them and to the other words written
     * alike (Basa); Baybayin text is not read from the screen.
     */
    override val ignoredFormTags = setOf("Baybayin")

    /**
     * "G.", "Gng." and "Bb." are Ginoo, Ginang and Binibini, "Kgg." Kagalang-galang, "Hen." Heneral, "Blg." bilang
     * (number), "p." pahina, "Sto." and "Sta." Santo and Santa, "Brgy." barangay. "atbp." (at iba pa) is left out: it
     * ends sentences as often as not.
     */
    override val abbreviations = setOf(
        "g", "gng", "bb", "kgg", "hen", "blg", "p", "sto", "sta", "brgy", "dr", "atty", "engr", "sen", "gob", "jr", "sr",
    )
}
