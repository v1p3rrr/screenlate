package com.vpr.screenlate.overlay.fonts

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.catalog.localeKey
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * A font file in the app's font folder.
 *
 * @param weight CSS `font-weight` it covers: "400", "700", or a range such as "100 900" for variable fonts.
 */
@Serializable
data class FontFile(val name: String, val weight: String = "400")

/**
 * A font the user downloaded from the catalog or added from a file.
 *
 * @param family the CSS family name, also usable in custom CSS.
 * @param catalogId the catalog entry it was downloaded from; null for the user's own files.
 */
@Serializable
data class InstalledFont(
    val id: String,
    val family: String,
    val files: List<FontFile>,
    val catalogId: String? = null,
)

@Serializable
data class CatalogFontFile(val url: String, val weight: String = "400")

/**
 * A freely licensed font offered for download.
 *
 * @param languages the codes of the languages whose pages alone offer it (`Language.code`), Mukta's Hindi and
 *   Marathi; empty for a font offered by the letters it has.
 * @param subsets the Google Fonts subsets the font covers; a font without [languages] is offered for every language
 *   whose [LanguageSupport.fontSubsets] are among them and whose [LanguageSupport.fontLetters] it has.
 * @param letters the letters and marks below U+2000 the font has, as hex ranges (`0041-005A,00AA`), written by
 *   `scripts/fonts/font_letters.py`; null for a font made for one language.
 * @param description by the key of an interface locale (`localeKey`: the language, `zh-Hant` for Traditional Chinese).
 */
@Serializable
data class CatalogFont(
    val id: String,
    val family: String,
    val languages: List<String> = emptyList(),
    val subsets: List<String> = emptyList(),
    val letters: String? = null,
    val description: Map<String, String> = emptyMap(),
    val files: List<CatalogFontFile>,
    val sizeMb: Double,
    val license: String,
    val homepage: String,
) {
    fun description(locale: Locale): String = description[localeKey(locale)] ?: description["en"].orEmpty()

    fun isOfferedFor(language: Language): Boolean {
        if (languages.isNotEmpty()) return language.code in languages
        val needed = language.support.fontSubsets
        return needed.isNotEmpty() && subsets.containsAll(needed) && hasLetters(language.support.fontLetters)
    }

    /** Whether the font has each of [text]'s characters and their capitals; true when its letters are not listed. */
    fun hasLetters(text: String): Boolean {
        val ranges = letterRanges ?: return true
        return (text + text.uppercase(Locale.ROOT)).codePoints().allMatch { point -> ranges.any { point in it } }
    }

    private val letterRanges: List<IntRange>? by lazy {
        letters?.split(',')?.map { range ->
            val (first, last) = range.split('-').let { it.first() to it.last() }
            first.toInt(16)..last.toInt(16)
        }
    }
}

@Serializable
data class FontCatalog(val fonts: List<CatalogFont>)
