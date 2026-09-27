package com.vpr.screenlate.settings

import java.util.Locale

/** An interface language to pick; [tag] is empty for the phone's language. */
data class LanguageOption(val tag: String, val name: String, val flag: String?)

object LanguageOptions {
    /**
     * The country whose flag stands for a language given without a region. Languages without an obvious country get
     * no flag; a locale with a region (pt-BR) uses that region's flag.
     */
    private val FLAG_REGIONS = mapOf(
        "en" to "GB", "ru" to "RU", "ja" to "JP", "de" to "DE", "fr" to "FR", "es" to "ES", "it" to "IT",
        "ko" to "KR", "zh" to "CN", "uk" to "UA", "pl" to "PL", "pt" to "PT", "nl" to "NL", "tr" to "TR",
        "vi" to "VN", "id" to "ID", "th" to "TH", "cs" to "CZ", "sv" to "SE", "fi" to "FI", "el" to "GR",
    )

    /** The flag emoji for [locale], built from regional indicator letters. */
    fun flag(locale: Locale): String? {
        val region = locale.country.ifEmpty { FLAG_REGIONS[locale.language] } ?: return null
        if (region.length != 2 || !region.all { it in 'A'..'Z' }) return null
        return region.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }.joinToString("")
    }

    /** Each locale named in its own language and sorted by that name. */
    fun of(locales: List<Locale>): List<LanguageOption> = locales
        .map { locale ->
            val name = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
            LanguageOption(locale.toLanguageTag(), name, flag(locale))
        }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
}
