package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Vietnamese writes each syllable apart, so a word of several (học sinh) is looked up from the syllables before the
 * aimed one as well. The old and new places of the tone mark (hoà, hòa) are matched by the dictionary engine's text
 * processors. Transcription tags name cities (Hanoi, Hue, Saigon), not countries, so there are no regions.
 */
object VietnameseSupport : SpacedLanguageSupport() {
    override val language = Language.VIETNAMESE
    override val glyph = "T"
    override val ocrScript = OcrScript.LATIN
    override val wikidataId = "Q9199"
    override val languageTag = "vi"
    override val fontSample = "Trăm năm trong cõi người ta, chữ tài chữ mệnh khéo là ghét nhau. Đà Nẵng, Huế."
    override val translationSample = "Hôm nay trời đẹp quá. Chúng ta đi dạo nhé?"
    override val audioTestWord = "nước"
    override val pronunciationSample = "nước → [nɨək̚˧˦]"
    override val flagCountries = listOf("vn")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = setOf("latin", "vietnamese")
    override val fontLetters = "àáảãạăằắẳẵặâầấẩẫậđèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵ"

    /** Words of up to three syllables are found from any of them. */
    override val earlierWords = 2

    /** Wiktionary lists "cái" as the classifier form of hundreds of nouns. */
    override val ignoredFormTags = setOf("classifier")

    /** Its marks are tones: a word without them is another word. */
    override val foldsMarks = false

    /** The dialect forms of Central Vietnam (tau for tao) carry `Central` alone. */
    override val formTagNames = mapOf("central" to "central-vietnam")

    /**
     * Titles and administrative units: TP. (thành phố), Q. and P. (quận, phường in addresses), TS., ThS., GS., PGS.
     * (academic degrees). "v.v." (and so on) usually ends a sentence and is left out.
     */
    override val abbreviations = setOf("tp", "q", "p", "ts", "ths", "gs", "pgs", "bs", "ks", "th.s", "tt", "st")
}
