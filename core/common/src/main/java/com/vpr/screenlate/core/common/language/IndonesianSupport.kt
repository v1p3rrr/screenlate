package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/** Wiktionary has almost no recordings of Indonesian words, and none name a region. */
object IndonesianSupport : SpacedLanguageSupport() {
    override val language = Language.INDONESIAN
    override val glyph = "ID"
    override val ocrScript = OcrScript.LATIN
    override val iso639Part3 = "ind"
    override val wikidataId = "Q9240"
    override val languageTag = "id"
    override val fontSample = "Muharjo seorang xenofobia universal yang takut pada warga jazirah, contohnya Qatar."
    override val translationSample = "Hari ini cuacanya cerah. Bagaimana kalau kita jalan-jalan?"
    override val audioTestWord = "air"
    override val pronunciationSample = "air → /a.ʔɪr/"
    override val flagCountries = listOf("id")
    override val systemFonts = LATIN_SYSTEM_FONTS
    override val fontSubsets = LATIN_FONT_SUBSETS

    /**
     * The Jawi spelling a word names leads only back to the word (derita → دريتا → derita), and Jawi text is not
     * read from the screen.
     */
    override val ignoredFormTags = setOf("Jawi")

    /**
     * "Jl." is jalan (street), "No." nomor, "hlm." halaman, "thn." and "tgl." tahun and tanggal. "dll." and "dkk." are
     * left out: they end sentences as often as not.
     */
    override val abbreviations = setOf(
        "dsb", "dst", "yth", "jl", "jln", "no", "hlm", "tsb", "sbb", "spt", "thn", "tgl", "bpk", "sdr", "ny", "dr",
        "prof", "ir", "drs", "s.h", "s.e", "s.pd", "a.n", "u.p", "s.d", "kab", "kec", "kel", "rp",
    )
}
