package com.vpr.screenlate.dictionary.api

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.MappedText
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.TextReplacement

/**
 * The texts a lookup searches: the original, the result of user-defined text replacements, the language's spelling
 * variants, the text in the chosen script when every word is shown in it, and their romaji conversions.
 */
internal object LookupVariants {
    fun of(text: String, settings: LookupSettings, support: LanguageSupport): List<MappedText> {
        val original = MappedText.identity(text)
        val replaced = applyReplacements(original, settings.textReplacements)
        val roots = listOfNotNull(original, replaced?.takeIf { it.text != original.text && it.text.isNotEmpty() })
        val script = settings.script?.takeIf { settings.fixedScript }
        val base = roots.flatMap { root ->
            listOf(root) + support.spellingVariants(root) + script?.let { support.inScript(root, it) }.orEmpty()
        }
        val all = if (settings.romaji) base.flatMap { listOfNotNull(it, support.fromLatin(it)) } else base
        return all.distinctBy { it.text }
    }

    /**
     * Applies enabled [rules] in order to [input]. Invalid regexes, bad replacement group references and runaway
     * expansions are skipped so a malformed rule never breaks lookups.
     */
    fun applyReplacements(input: MappedText, rules: List<TextReplacement>): MappedText? {
        val active = rules.filter { it.enabled && it.pattern.isNotEmpty() }
        if (active.isEmpty()) return null
        val maxLength = maxOf(input.text.length * MAX_EXPANSION_FACTOR, MIN_EXPANSION_LIMIT)
        var current = input
        for (rule in active) {
            val regex = compile(rule) ?: continue
            val replacement = if (rule.regex) rule.replacement else rule.replacement.replace("$", "$$")
            val next = runCatching { current.replace(regex, replacement) }.getOrNull() ?: continue
            if (next.text.length <= maxLength) current = next
        }
        return current.takeIf { it.text != input.text }
    }

    private fun compile(rule: TextReplacement): Regex? = runCatching {
        val pattern = if (rule.regex) rule.pattern else Regex.escape(rule.pattern)
        val options = if (rule.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        Regex(pattern, options)
    }.getOrNull()

    private const val MAX_EXPANSION_FACTOR = 4
    private const val MIN_EXPANSION_LIMIT = 512
}
