package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.settings.LookupSettings

/** The texts a lookup searches: the original, one per text replacement group, and their romaji conversions. */
internal object LookupVariants {
    fun of(text: String, settings: LookupSettings, support: LanguageSupport): List<MappedText> {
        val original = MappedText.identity(text)
        val replaced = settings.replacementGroups.mapNotNull { group ->
            group.filter { it.enabled }
                .fold(original) { current, rule -> rule.regex()?.let { current.replace(it, rule.replacement) } ?: current }
                .takeIf { it.text != text && it.text.isNotEmpty() }
        }
        val base = if (settings.searchOriginal || replaced.isEmpty()) listOf(original) + replaced else replaced
        val all = if (settings.romaji) base.flatMap { listOfNotNull(it, support.fromLatin(it)) } else base
        return all.distinctBy { it.text }
    }
}
