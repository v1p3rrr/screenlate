package com.vpr.screenlate.core.common

import com.vpr.screenlate.core.common.language.support
import java.util.Locale

/**
 * Source language of the text being looked up. Every layer (OCR, lookup, rendering) takes it as a parameter. A
 * language is listed once it has a support class (`core.common.language.support`).
 */
enum class Language(val code: String) {
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
    ;

    companion object {
        /**
         * The language with [code] (ISO 639-1, or another code the systems use for it: Android's fil for tl), or null
         * when the app does not support it.
         */
        fun of(code: String?): Language? = entries.firstOrNull { it.code == code } ?: OTHER_CODES[code]

        private val OTHER_CODES = mapOf("fil" to FILIPINO)
    }
}

/**
 * The ISO 639 code of the locale's language as language tags write it: "id" for Indonesian and "he" for Hebrew, where
 * Android's [Locale.getLanguage] keeps the old "in" and "iw".
 */
val Locale.languageCode: String get() = toLanguageTag().substringBefore('-')

/** The language's name in [locale] (the interface language by default), capitalized as a heading or list item. */
fun Language.displayName(locale: Locale = Locale.getDefault()): String =
    support.nameIn(locale).replaceFirstChar { it.titlecase(locale) }

/**
 * The name of the language with [code] (ISO 639) in [locale] as running text writes it: the app's own name for a
 * language it supports ("Filipino (Tagalog)" for tl), else the system's.
 */
fun languageName(code: String, locale: Locale = Locale.getDefault()): String =
    Language.of(code)?.support?.nameIn(locale) ?: Locale.forLanguageTag(code).getDisplayLanguage(locale)
