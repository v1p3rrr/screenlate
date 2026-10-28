package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Estonian. Wiktionary's English edition lists the case and verb forms; the bundle adds the app's own rule for the
 * negative forms of olema (pole, polnud → olema), which Wiktionary lists as words of their own. Words without marks find
 * the words with them, and words from OCR are also looked up without theirs, which recognition reads õ wrong for (möte
 * → mõte; [foldsMarks]). Long compounds are split into the words they are made of ([compoundLinks]). An ending after a number or an
 * abbreviation in capitals with a hyphen (NATO-ga, 1990-ndatel) is also looked up with the hyphen.
 */
object EstonianSupport : SpacedLanguageSupport() {
    override val language = Language.ESTONIAN
    override val glyph = "E"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9072"
    override val languageTag = "et"
    override val fontSample = "Põdur Zagrebi tšellomängija-följetonist Ciqo külmetas kehvas garaažis."
    override val translationSample = "Täna on ilus ilm. Kas lähme jalutama?"
    override val audioTestWord = "vesi"
    override val pronunciationSample = "vesi → [ˈvesi]"
    override val flagCountries = listOf("ee")
    override val systemFonts = LATIN_SYSTEM_FONTS

    /** š and ž of loanwords (tšello, garaaž) are in the fonts' Latin extension. */
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "äõöüšž"

    /** Lingua Libre had recordings of eleven of twenty common words checked, Wiktionary of two. */
    override val defaultAudioSources = listOf("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH")

    /** „…“ closes with the character English opens with; English quotes and «…» are common too. */
    override val quotePairs = mapOf('„' to '“', '“' to '”', '«' to '»', '(' to ')', '[' to ']')

    /** Compounds join their words without a link (linnavalitsus: the genitive linna is a form row). */
    override val compoundLinks = emptyList<String>()

    /** Dates and ordinals: 24. veebruar, 2020. aastal. */
    override val ordinalPeriod = true

    /**
     * "u." (umbes), "nn." (nii-nimetatud), "nt." (näiteks), "s.o." (see on), "s.t." (see tähendab), "v.a." (välja
     * arvatud), "lk." (lehekülg), "nr." (number), and before a name "hr." (härra), "pr." (proua), "dr." and "prof.",
     * which come before more of the sentence. Most texts write them without the period.
     */
    override val abbreviations = setOf(
        "u", "nn", "nt", "s.o", "s.t", "v.a", "lk", "nr", "hr", "pr", "dr", "prof",
    )

    /**
     * Often a sentence's last word: "jne." (ja nii edasi), "jm." (ja muu), "jms." (ja muud sellist), "jt." (ja teised);
     * after a number "a." (aasta) and the eras "e.m.a." and "m.a.j."; after a name "tn." (tänav), "mnt." (maantee) and
     * "pst." (puiestee).
     */
    override val abbreviationsBeforeLowercase = setOf(
        "jne", "jm", "jms", "jt", "a", "e.m.a", "m.a.j", "tn", "mnt", "pst",
    )

    /** Endings after a number or an abbreviation in capitals: 1990-ndatel, 5-ga, NATO-ga, USA-s. */
    override val hyphenedEndings = HyphenedEndings.AFTER_NUMBERS_AND_CAPITALS
}
