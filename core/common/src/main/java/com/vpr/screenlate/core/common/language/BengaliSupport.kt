package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Bengali (Bangla), in the Bengali script. Yomitan has no Bengali descriptor: the language's bundle takes Hindi's under
 * its own code, composes the text as headwords are (NFC: recognized text may give ো as ে and া, য় as one character)
 * and adds the app's own list of noun endings (classifiers and cases: বইটির → বই), as Wiktionary lists few Bengali
 * forms. Dictionaries give no readings. Lingua Libre has recordings of most common words, Wiktionary of few.
 */
object BengaliSupport : SpacedLanguageSupport() {
    override val language = Language.BENGALI
    override val glyph = "বা"
    override val ocrScript = OcrScript.BENGALI
    override val wikidataId = "Q9610"
    override val languageTag = "bn"
    override val fontSample = "জ্ঞানী ক্ষত্রিয় শ্রদ্ধায় দ্বার খুললেন। ঋষির হৃদয় প্রসন্ন হলো।"
    override val translationSample = "আজ আবহাওয়া ভালো। বেড়াতে যাবে?"
    override val audioTestWord = "বই"
    override val pronunciationSample = "বই → /boi̯/"
    override val flagCountries = listOf("bd")

    /** Android and Java name the language "Bangla" in English and Vietnamese; Wiktionary and the translators, Bengali. */
    override fun nameIn(locale: Locale): String = when (locale.language) {
        "en" -> "Bengali"
        "vi" -> "tiếng Bengal"
        else -> super.nameIn(locale)
    }

    /**
     * The phone's default font falls back to its Bengali one by itself; Noto Serif Bengali is reached by name. The range
     * is Google Fonts' Bengali subset.
     */
    override val systemFonts = SystemFonts(
        sans = emptyList(),
        serif = listOf("NotoSerifBengali-Regular", "Noto Serif Bengali Regular", "Noto Serif Bengali"),
        unicodeRange = "U+0951-0952, U+0964-0965, U+0980-09FE, U+1CD0, U+1CD2, U+1CD5-1CD6, U+1CD8, U+1CE1, U+1CEA, " +
            "U+1CED, U+1CF2, U+1CF5-1CF7, U+200C-200D, U+20B9, U+25CC, U+A8F1",
        aliases = emptyMap(),
    )

    /** The danda । ends a sentence, as in Hindi; many texts end sentences with a period instead. */
    override val sentenceTerminators = setOf('।', '॥', '.', '!', '?', '…', '\n')

    override val quotePairs = mapOf('“' to '”', '‘' to '’', '(' to ')', '[' to ']')

    /**
     * "ড." and "ডা." ডক্টর and ডাক্তার, "মো.", "মোহা." মোহাম্মদ, "মোসা." and "মোছা." মোসাম্মৎ, "মি." মিস্টার, "খ্রি."
     * খ্রিস্টাব্দ, "পৃ." পৃষ্ঠা, "লি." লিমিটেড, "কি.মি." and "সে.মি.", "সা." and "রা." after names of the Prophet and his
     * companions; and the names of Latin letters as initials ("এ. কে. ফজলুল হক"), but এল, এস and ও, which are words that
     * end sentences as well (সে এল, তুমি এস).
     */
    override val abbreviations = setOf(
        "ড", "ডা", "মো", "মোহা", "মোসা", "মোছা", "মি", "খ্রি", "পৃ", "লি", "কি.মি", "সে.মি", "সা", "রা",
    ) + LETTER_NAMES - setOf("এল", "এস", "ও")

    /** Names of Latin letters, joined by periods in acronyms and initials: "এ.কে.", "বি.এন.পি.". */
    override val initials = LETTER_NAMES
}

private val LETTER_NAMES = setOf(
    "এ", "বি", "সি", "ডি", "ই", "এফ", "জি", "এইচ", "আই", "জে", "কে", "এল", "এম", "এন", "ও", "পি", "কিউ", "আর", "এস", "টি",
    "ইউ", "ভি", "ডব্লিউ", "এক্স", "ওয়াই", "জেড",
)
