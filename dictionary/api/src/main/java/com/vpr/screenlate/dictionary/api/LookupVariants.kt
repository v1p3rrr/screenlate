package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.settings.LookupSettings

/** The texts a lookup searches: the original, the language's spelling variants, and their romaji conversions. */
internal object LookupVariants {
    fun of(text: String, settings: LookupSettings, support: LanguageSupport): List<MappedText> {
        val original = MappedText.identity(text)
        val base = listOf(original) + support.spellingVariants(original)
        val all = if (settings.romaji) base.flatMap { listOfNotNull(it, support.fromLatin(it)) } else base
        return all.distinctBy { it.text }
    }
}
