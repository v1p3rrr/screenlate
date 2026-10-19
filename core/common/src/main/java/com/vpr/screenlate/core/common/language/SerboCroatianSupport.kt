package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * Serbo-Croatian, one language as in Yomitan, Wiktionary and wordfreq, listed once per script: each entry has its own
 * recognition model, voice, flag and settings, and both read the same dictionaries (sh), whose Latin and Cyrillic
 * headwords are full entries each. Wiktionary's recordings are Serbian and titled in Cyrillic (`Sr-кућа.flac`), so a
 * Latin word is looked for in Cyrillic; they name no regions.
 */
abstract class SerboCroatianSupport : SpacedLanguageSupport() {
    override val wikidataId = "Q9299"
    override val languageTag get() = language.code
    override val recordingPrefix = "sr"
    override val localeLanguages = setOf("sh", "hr", "sr", "bs", "cnr")

    /** "Serbo-Croatian (Latin)": the system's names of the language and of the script. */
    override fun nameIn(locale: Locale): String {
        val tag = Locale.forLanguageTag(language.code)
        return "${tag.getDisplayLanguage(locale)} (${tag.getDisplayScript(locale)})"
    }

    /** Wiktionary has recordings of more words than Lingua Libre, in Cyrillic. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    override fun recordingTitle(term: String, reading: String): String = SerboCroatianScripts.toCyrillic(term) ?: term

    /** wordfreq writes the language in Latin letters only (it transliterates Serbian Cyrillic). */
    override fun frequencySpellings(word: String): List<String> = listOfNotNull(SerboCroatianScripts.toCyrillic(word))

    /** Serbian „…“, Croatian »…« (and „…”, whose ” closes in every language), ‚…‘ inside. */
    override val quotePairs = mapOf('„' to '“', '»' to '«', '‚' to '‘', '(' to ')', '[' to ']')

    /** Ordinals in digits take a period: "5. maja", "1918. godine". */
    override val ordinalPeriod = true

    /**
     * Each word's Cyrillic entry is also listed as a form of its Latin one (ма̏чка → mačka), which adds nothing to the
     * Cyrillic entry itself.
     */
    override val ignoredFormTags = setOf("Cyrillic")

    /** Accents of the readings Wiktionary writes into form headwords (ку̏ћа), on vowels and syllabic r. */
    override val headwordMarks = HeadwordMarks(marks = "̀́̂̄̏̑", bases = "aeiourаеиоур")

    /**
     * "god." and "g." are godina, "st." stoljeće or stoleće, "v." vijek or vek, "br." broj, "str." strana, "pr." prije
     * or pre (pr. Kr.); Cyrillic the same. "itd.", "i sl." and "i dr." (and the like) usually end a sentence and are
     * left out, though dr. is also doktor before a name, which is kept.
     */
    override val abbreviations = setOf(
        "npr", "tj", "tzv", "odn", "god", "g", "st", "v", "str", "br", "dr", "mr", "prof", "doc", "ing", "ul", "sv",
        "pr", "tel",
        "нпр", "тј", "тзв", "одн", "год", "г", "ст", "в", "стр", "бр", "др", "мр", "проф", "доц", "инж", "ул", "св",
        "пр", "тел",
    )

    /** Krista and Христа after pr. and пр. ("pr. Kr. godine"); "Kr." before a capital ends the sentence. */
    override val abbreviationsBeforeLowercase = setOf("kr", "хр")

    /** The letters of "p. n. e." and "n. e." (before and in the Common Era), and of "п. н. е.". */
    override val pairedLetters = setOf("p", "n", "e", "п", "н", "е")
}

/** Serbo-Croatian in Latin letters, the Croatian voice and flag. */
object SerboCroatianLatinSupport : SerboCroatianSupport() {
    override val language = Language.SERBO_CROATIAN_LATIN
    override val glyph = "Đ"
    override val ocrScript = OcrScript.LATIN
    override val speechTag = "hr"
    override val codeLabel = "SH"
    override val fontSample = "Gojazni đačić s biciklom drži hmelj i finu vatu u džepu nošnje."
    override val translationSample = "Danas je lijepo vrijeme. Hoćemo li u šetnju?"
    override val audioTestWord = "voda"
    override val pronunciationSample = "voda → /ʋǒda/"
    override val flagCountries = listOf("hr")

    /** The accents of readings (kȕća) are combining marks after the letters. */
    override val systemFonts = LATIN_SYSTEM_FONTS.copy(unicodeRange = "U+0000-024F, U+0300-036F, U+1E00-1EFF, U+2000-206F")
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
}

/** Serbo-Croatian in Cyrillic letters, the Serbian voice and flag. */
object SerboCroatianCyrillicSupport : SerboCroatianSupport() {
    override val language = Language.SERBO_CROATIAN_CYRILLIC
    override val glyph = "Ћ"
    override val ocrScript = OcrScript.CYRILLIC
    override val speechTag = "sr"
    override val codeLabel = "СХ"
    override val fontSample = "Гојазни ђачић с бициклом држи хмељ и фину вату у џепу ношње."
    override val translationSample = "Данас је лепо време. Хоћемо ли у шетњу?"
    override val audioTestWord = "вода"
    override val pronunciationSample = "вода → /ʋǒda/"
    override val flagCountries = listOf("rs")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS

    /** Grammar lines name the Latin spelling (č, ć, đ). */
    override val fontSubsets = setOf("latin", "latin-ext", "cyrillic")

    /** Serbian ј looks like the Latin j, which the Cyrillic models also know. */
    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, SERBIAN_LOOK_ALIKES)

    private val SERBIAN_LOOK_ALIKES = mapOf('j' to 'ј', 'J' to 'Ј')
}
