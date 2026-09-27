package com.vpr.screenlate.dictionaries

import java.text.Collator
import java.util.Locale

/** Target languages listed after the interface language, English and the source language. */
private val PREFERRED_TARGETS = listOf("ru", "es", "fr", "de")

/**
 * Order of the term dictionary sections of one source language: the interface language, English, the source
 * language itself (monolingual dictionaries), Russian, Spanish, French, German, then the rest by name in the
 * interface language.
 */
internal fun catalogTargetOrder(source: String, interfaceLocale: Locale): Comparator<String?> {
    val preferred = (listOf(interfaceLocale.language, "en", source) + PREFERRED_TARGETS).distinct()
    val collator = Collator.getInstance(interfaceLocale)
    val name = { target: String -> Locale.forLanguageTag(target).getDisplayLanguage(interfaceLocale) }
    return compareBy<String?> { target -> preferred.indexOf(target).takeIf { it >= 0 } ?: preferred.size }
        .thenBy { it == null }
        .thenComparing({ it?.let(name).orEmpty() }, collator)
        // toSortedMap merges keys that compare equal, so distinct codes must never tie.
        .thenBy { it.orEmpty() }
}
