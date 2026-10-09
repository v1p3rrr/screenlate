package com.vpr.screenlate.core.translate

import com.vpr.screenlate.core.common.Language
import java.util.Locale

/**
 * A language the services translate into or from.
 *
 * @property tag BCP 47 tag, stored in the settings.
 * @property englishName the services' name, for languages the system cannot name.
 * @property microsoft the code Bing and Edge use; null when they lack the language.
 * @property google the code Google uses; null when it lacks the language.
 */
data class TranslationLanguage(val tag: String, val englishName: String, val microsoft: String?, val google: String?) {
    /** The language's name in [locale]; the services' English name when the system does not know it. */
    fun displayName(locale: Locale): String {
        val parsed = Locale.forLanguageTag(tag)
        val language = parsed.getDisplayLanguage(locale)
        // The system repeats the code for a language it does not know.
        if (language.isEmpty() || language.equals(parsed.language, ignoreCase = true)) return englishName
        return parsed.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

object TranslationLanguages {
    val all: List<TranslationLanguage> get() = TRANSLATION_LANGUAGES

    private val byTag by lazy { TRANSLATION_LANGUAGES.associateBy { it.tag.lowercase(Locale.ROOT) } }

    fun of(tag: String): TranslationLanguage? = byTag[tag.lowercase(Locale.ROOT)]

    /** The language of [source] text, as the services know it. */
    fun of(source: Language): TranslationLanguage? = of(source.code)

    /**
     * The language translations go into by default: [locale]'s (the interface language), or English when that is
     * the [source] language or no service has it.
     */
    fun defaultFor(locale: Locale, source: Language): TranslationLanguage {
        val english = of(ENGLISH)!!
        val language = forLocale(locale) ?: return english
        return if (Locale.forLanguageTag(language.tag).language == source.code) english else language
    }

    /**
     * The service language closest to [locale]: its own region or script where all the services tell them apart
     * (Mexican Spanish only Microsoft has, so it is plain Spanish here).
     */
    fun forLocale(locale: Locale): TranslationLanguage? {
        val language = OLD_CODES[locale.language] ?: locale.language
        val tag = when (language) {
            "zh" -> if (locale.script == "Hant" || (locale.script.isEmpty() && locale.country in TRADITIONAL_REGIONS)) "zh-Hant" else "zh-Hans"
            "pt" -> if (locale.country == "PT") "pt-PT" else "pt-BR"
            "sr" -> if (locale.script == "Latn") "sr-Latn" else "sr-Cyrl"
            "fr" -> if (locale.country == "CA") "fr-CA" else "fr"
            "no", "nn" -> "nb"
            "tl" -> "fil"
            "fa" -> if (locale.country == "AF") "prs" else "fa"
            else -> language
        }
        return of(tag)
    }

    private const val ENGLISH = "en"

    /** Java's old codes for some languages, and their tags. */
    private val OLD_CODES = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
}
