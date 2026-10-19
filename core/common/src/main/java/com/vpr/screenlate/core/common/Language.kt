package com.vpr.screenlate.core.common

import com.vpr.screenlate.core.common.language.support
import java.util.Locale

/**
 * Source language of the text being looked up. Every layer (OCR, lookup, rendering) takes it as a parameter. A
 * language is listed once it has a support class (`core.common.language.support`).
 *
 * @property code the entry's own code: settings, routes, backups and recognition models go by it. The ISO 639-1 code,
 * with a script subtag for a language listed once per script (sh-Latn, sh-Cyrl).
 * @property dictionaryCode the language's code in dictionaries, Yomitan and wordfreq: entries of one language written in
 * two scripts share it, and with it their dictionaries.
 */
enum class Language(val code: String, val dictionaryCode: String = code) {
    JAPANESE("ja"),
    ENGLISH("en"),
    CHINESE("zh"),
    KOREAN("ko"),
    SPANISH("es"),
    FRENCH("fr"),
    GERMAN("de"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    POLISH("pl"),
    TURKISH("tr"),
    LATVIAN("lv"),
    DUTCH("nl"),
    CZECH("cs"),
    INDONESIAN("id"),
    MALAY("ms"),
    FILIPINO("tl"),
    VIETNAMESE("vi"),
    RUSSIAN("ru"),
    SERBO_CROATIAN_LATIN("sh-Latn", "sh"),
    SERBO_CROATIAN_CYRILLIC("sh-Cyrl", "sh"),
    UKRAINIAN("uk"),
    BELARUSIAN("be"),
    ;

    companion object {
        /**
         * The language with [code] (ISO 639-1, or another code the systems use for it: Android's fil for tl), or null
         * when the app does not support it.
         */
        fun of(code: String?): Language? = entries.firstOrNull { it.code == code } ?: OTHER_CODES[code]

        /** The entries whose dictionaries have the source language [code] ([dictionaryCode]); empty for none. */
        fun withDictionaries(code: String?): List<Language> = entries.filter { it.dictionaryCode == code }

        /**
         * The first entry whose dictionaries have the source language [code], for what entries sharing a code do alike
         * (reading dictionaries, building frequency lists); null when the app does not support it.
         */
        fun ofDictionaries(code: String?): Language? = withDictionaries(code).firstOrNull()

        private val OTHER_CODES = mapOf("fil" to FILIPINO)
    }
}

/**
 * Whether [code], the language a catalog entry or a download is for, is this entry's: a recognition model's names the
 * entry itself ([Language.code]), a dictionary's the [Language.dictionaryCode] it shares with the entries of its other
 * scripts.
 */
fun Language.hasCode(code: String?): Boolean = code == this.code || code == dictionaryCode

/**
 * The ISO 639 code of the locale's language as language tags write it: "id" for Indonesian and "he" for Hebrew, where
 * Android's [Locale.getLanguage] keeps the old "in" and "iw".
 */
val Locale.languageCode: String get() = toLanguageTag().substringBefore('-')

/** The language's name in [locale] (the interface language by default), capitalized as a heading or list item. */
fun Language.displayName(locale: Locale = Locale.getDefault()): String =
    support.nameIn(locale).replaceFirstChar { it.titlecase(locale) }

/**
 * The name of the language with [code] (ISO 639, or an entry's [Language.code]) in [locale] as running text writes it:
 * the app's own name for an entry ("Filipino (Tagalog)" for tl), else the system's ("Serbo-Croatian" for the
 * dictionaries' sh).
 */
fun languageName(code: String, locale: Locale = Locale.getDefault()): String =
    Language.of(code)?.support?.nameIn(locale) ?: Locale.forLanguageTag(code).getDisplayLanguage(locale)
