package com.vpr.screenlate.languages

import androidx.annotation.StringRes
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language

/**
 * Popup font strings written for each language, so a name such as "Only for Japanese text" reads naturally in every
 * interface language.
 *
 * @property system the option for the phone's own font.
 * @property scriptOnly the switch that keeps an installed font to the language's text.
 * @property systemMissing the warning when the phone declares no font for the language; null where the page keeps the
 *   phone's default font.
 * @property catalogInfo the ⓘ next to the downloadable fonts; null for none.
 * @property weightInfo the ⓘ of the text weight while this is the only language.
 */
class FontStrings(
    @param:StringRes val system: Int,
    @param:StringRes val scriptOnly: Int,
    @param:StringRes val scriptOnlyHint: Int,
    @param:StringRes val systemMissing: Int?,
    @param:StringRes val catalogInfo: Int?,
    @param:StringRes val weightInfo: Int,
)

/** Dictionary labels that differ by language: pitch accents and kanji are Japanese, others have transcriptions. */
class DictionaryStrings(
    @param:StringRes val pitchKind: Int,
    @param:StringRes val pitchCount: Int,
    @param:StringRes val kanjiKind: Int,
    @param:StringRes val kanjiCount: Int,
)

/** Labels for dictionaries of the language with [code]; a dictionary without a language keeps the Japanese ones. */
fun dictionaryStrings(code: String?): DictionaryStrings = when (if (code == null) Language.JAPANESE else Language.of(code)) {
    Language.JAPANESE -> DictionaryStrings(
        pitchKind = R.string.dictionaries_kind_pitch,
        pitchCount = R.string.dictionaries_count_pitches,
        kanjiKind = R.string.dictionaries_kind_kanji,
        kanjiCount = R.string.dictionaries_count_kanji,
    )
    Language.ENGLISH, null -> DictionaryStrings(
        pitchKind = R.string.dictionaries_kind_pronunciation,
        pitchCount = R.string.dictionaries_count_transcriptions,
        kanjiKind = R.string.dictionaries_kind_characters,
        kanjiCount = R.string.dictionaries_count_characters,
    )
}

val Language.fontStrings: FontStrings
    get() = when (this) {
        Language.JAPANESE -> FontStrings(
            system = R.string.popup_font_system,
            scriptOnly = R.string.popup_font_script_only,
            scriptOnlyHint = R.string.popup_font_script_only_hint,
            systemMissing = R.string.popup_font_system_missing,
            catalogInfo = R.string.popup_font_proprietary,
            weightInfo = R.string.popup_text_weight_info,
        )
        Language.ENGLISH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_en,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
    }
