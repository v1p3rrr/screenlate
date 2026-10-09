package com.vpr.screenlate.core.common

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
