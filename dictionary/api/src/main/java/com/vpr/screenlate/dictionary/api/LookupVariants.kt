package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.settings.LookupSettings

/**
 * The texts a lookup searches: the original, the language's spelling variants, the text in the chosen script when
 * every word is shown in it, and their romaji conversions.
 */
internal object LookupVariants {
    fun of(text: String, settings: LookupSettings, support: LanguageSupport): List<MappedText> {
        val original = MappedText.identity(text)
        val script = settings.script?.takeIf { settings.fixedScript }
        val base = listOf(original) + support.spellingVariants(original) +
            script?.let { support.inScript(original, it) }.orEmpty()
        val all = if (settings.romaji) base.flatMap { listOfNotNull(it, support.fromLatin(it)) } else base
        return all.distinctBy { it.text }
    }
}
