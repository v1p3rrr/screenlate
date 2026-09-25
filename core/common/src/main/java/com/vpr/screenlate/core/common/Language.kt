package com.vpr.screenlate.core.common

/** Source language of the text being looked up. Every layer (OCR, lookup, rendering) takes it as a parameter. */
enum class Language(val code: String) {
    JAPANESE("ja"),
}
