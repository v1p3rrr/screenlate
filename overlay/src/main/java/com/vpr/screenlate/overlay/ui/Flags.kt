package com.vpr.screenlate.overlay.ui

import androidx.annotation.DrawableRes
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
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
        "it" to R.drawable.flag_it,
        "br" to R.drawable.flag_br,
        "pt" to R.drawable.flag_pt,
        "pl" to R.drawable.flag_pl,
        "tr" to R.drawable.flag_tr,
        "lv" to R.drawable.flag_lv,
        "nl" to R.drawable.flag_nl,
        "cz" to R.drawable.flag_cz,
        "id" to R.drawable.flag_id,
        "my" to R.drawable.flag_my,
        "ph" to R.drawable.flag_ph,
        "vn" to R.drawable.flag_vn,
        "ru" to R.drawable.flag_ru,
        "hr" to R.drawable.flag_hr,
        "rs" to R.drawable.flag_rs,
        "ua" to R.drawable.flag_ua,
        "by" to R.drawable.flag_by,
        "gr" to R.drawable.flag_gr,
        "in" to R.drawable.flag_in,
        "bd" to R.drawable.flag_bd,
        "ge" to R.drawable.flag_ge,
        "am" to R.drawable.flag_am,
        "th" to R.drawable.flag_th,
        "kh" to R.drawable.flag_kh,
        "sa" to R.drawable.flag_sa,
        "ir" to R.drawable.flag_ir,
        "il" to R.drawable.flag_il,
        "kz" to R.drawable.flag_kz,
        "kg" to R.drawable.flag_kg,
        "se" to R.drawable.flag_se,
        "no" to R.drawable.flag_no,
        "dk" to R.drawable.flag_dk,
        "ro" to R.drawable.flag_ro,
        "lang_la" to R.drawable.flag_lang_la,
        "lt" to R.drawable.flag_lt,
        "sk" to R.drawable.flag_sk,
        "si" to R.drawable.flag_si,
        "lang_eo" to R.drawable.flag_lang_eo,
        "al" to R.drawable.flag_al,
        "tz" to R.drawable.flag_tz,
        "gb-wls" to R.drawable.flag_gb_wls,
    )

    /** The flag of [country] (or of a language, `lang_la`); null for a country without one. */
    @DrawableRes
    fun drawable(country: String): Int? = drawables[country]

    /** The flag [state] gives [language]. */
    @DrawableRes
    fun of(state: LanguageProfilesState, language: Language): Int? = drawable(state.flagCountry(language))

    /** The letters shown instead of a flag. */
    fun code(language: Language): String = language.support.codeLabel

    /**
     * The country's name in [locale], or the language's for a language's own flag, for a flag's choice and TalkBack. A
     * region's flag (`gb-wls`), which locales do not name, gives its code.
     */
    fun countryName(country: String, locale: Locale = Locale.getDefault()): String =
        if (country.startsWith(LANGUAGE_FLAG)) {
            Locale.forLanguageTag(country.removePrefix(LANGUAGE_FLAG)).getDisplayLanguage(locale)
        } else if ('-' in country) {
            country.uppercase(Locale.ROOT)
        } else {
            Locale.Builder().setRegion(country.uppercase(Locale.ROOT)).build().getDisplayCountry(locale)
        }

    /** The prefix of a language's own flag in `LanguageSupport.flagCountries`: Latin's laurel wreath, `lang_la`. */
    private const val LANGUAGE_FLAG = "lang_"
}
