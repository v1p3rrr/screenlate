package com.vpr.screenlate.overlay.ui

import androidx.annotation.DrawableRes
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.overlay.R
import java.util.Locale

/**
 * The round flags that stand for languages, made from circle-flags by `scripts/flags/circle_flags.py`. On e-ink the
 * language's [code] takes a flag's place: colors turn into greys there, and many flags would look alike.
 */
object Flags {
    private val drawables = mapOf(
        "jp" to R.drawable.flag_jp,
        "us" to R.drawable.flag_us,
        "gb" to R.drawable.flag_gb,
        "cn" to R.drawable.flag_cn,
        "kr" to R.drawable.flag_kr,
        "es" to R.drawable.flag_es,
        "fr" to R.drawable.flag_fr,
        "de" to R.drawable.flag_de,
    )

    /** The flag of [country]; null for a country without one. */
    @DrawableRes
    fun drawable(country: String): Int? = drawables[country]

    /** The flag [state] gives [language]. */
    @DrawableRes
    fun of(state: LanguageProfilesState, language: Language): Int? = drawable(state.flagCountry(language))

    /** The two letters shown instead of a flag. */
    fun code(language: Language): String = language.code.uppercase(Locale.ROOT)

    /** The country's name in [locale], for a flag's choice and TalkBack. */
    fun countryName(country: String, locale: Locale = Locale.getDefault()): String =
        Locale.Builder().setRegion(country.uppercase(Locale.ROOT)).build().getDisplayCountry(locale)
}
