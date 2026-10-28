package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Yomitan has no Swahili descriptor; the bundle takes Czech's (capitalization only) with the app's own verb prefixes
 * (subject, tense, relative, object), which reach the verb a dictionary lists (alisoma → soma, kusoma, -soma) only where
 * the word has no entry of its own ([inflectionRulesOnlyWithoutEntry]). The apostrophe of ng' (ng'ombe, ng’ombe) is
 * read either way. Wiktionary's recordings name Kenya (`Sw-ke-maji.ogg`), which the default region pattern reads.
 */
object SwahiliSupport : SpacedLanguageSupport() {
    override val language = Language.SWAHILI
    override val glyph = "SW"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q7838"
    override val languageTag = "sw"
    override val fontSample = "Ng'ombe wa Zawadi walikunywa maji safi karibu na ziwa Victoria jioni."
    override val translationSample = "Leo hali ya hewa ni nzuri. Je, twende tukatembee?"
    override val audioTestWord = "maji"
    override val pronunciationSample = "maji → /ˈmad͡ʒi/"
    override val flagCountries = listOf("tz")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS

    /** Wiktionary had recordings of 18 of twenty common words checked, Lingua Libre none. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    override val inflectionRulesOnlyWithoutEntry = true

    /**
     * "k.m." (kwa mfano), titles before a name (Bw. Bwana, Bi. Bibi, Dkt. and Dk. Daktari, Dr., Prof., Mhe.
     * Mheshimiwa, Mt. Mtakatifu, Nd. Ndugu), "S.L.P." (sanduku la posta) and "tar." (tarehe) before a number.
     */
    override val abbreviations = setOf("k.m", "bw", "bi", "dkt", "dk", "dr", "prof", "mhe", "mt", "nd", "s.l.p", "tar")

    /**
     * Often a sentence's last word: "n.k." and "nk." (na kadhalika), the eras (K.K. and KK. kabla ya Kristo, B.K. and
     * BK. baada ya Kristo), "uk." (ukurasa, and the UK) and units (km., kg.).
     */
    override val abbreviationsBeforeLowercase = setOf("n.k", "nk", "k.k", "kk", "b.k", "bk", "uk", "km", "kg")
}
