package com.vpr.screenlate.core.common

import java.util.Locale

/**
 * Source language of the text being looked up. Every layer (OCR, lookup, rendering) takes it as a parameter. A
 * language is listed once it has a support class (`core.common.language.support`).
 */
enum class Language(val code: String) {
    JAPANESE("ja"),
    ENGLISH("en"),
    ;

    companion object {
        /** The language with [code] (ISO 639-1), or null when the app does not support it. */
        fun of(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}

/** The language's name in [locale] (the interface language by default), capitalized as a heading or list item. */
fun Language.displayName(locale: Locale = Locale.getDefault()): String =
    Locale.forLanguageTag(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
