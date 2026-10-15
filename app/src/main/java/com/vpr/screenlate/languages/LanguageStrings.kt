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

/** The switch that colors words by their pronunciation (`PopupAppearance.pronunciationColors`). */
class PronunciationColorStrings(
    @param:StringRes val label: Int,
    @param:StringRes val hint: Int,
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
    Language.ENGLISH, Language.CHINESE, Language.KOREAN, null -> DictionaryStrings(
        pitchKind = R.string.dictionaries_kind_pronunciation,
        pitchCount = R.string.dictionaries_count_transcriptions,
        kanjiKind = R.string.dictionaries_kind_characters,
        kanjiCount = R.string.dictionaries_count_characters,
    )
}

/** The pronunciation color switch for this language; null for a language without such colors. */
val Language.pronunciationColorStrings: PronunciationColorStrings?
    get() = when (this) {
        Language.JAPANESE -> PronunciationColorStrings(R.string.popup_pitch_colors, R.string.popup_pitch_colors_hint)
        Language.CHINESE -> PronunciationColorStrings(R.string.popup_tone_colors, R.string.popup_tone_colors_hint)
        Language.ENGLISH, Language.KOREAN -> null
    }

/** The switch for entries of the other characters of a found word (`LanguageSupport.hasCharacterEntries`). */
class CharacterEntryStrings(
    @param:StringRes val label: Int,
    @param:StringRes val hint: Int,
)

val Language.characterEntryStrings: CharacterEntryStrings
    get() = when (this) {
        Language.JAPANESE -> CharacterEntryStrings(R.string.lookup_single_kanji, R.string.lookup_single_kanji_hint)
        Language.ENGLISH, Language.CHINESE, Language.KOREAN ->
            CharacterEntryStrings(R.string.lookup_single_characters, R.string.lookup_single_characters_hint)
    }

/** The name of one of `LanguageSupport.scriptTags`. */
@StringRes
fun scriptName(tag: String): Int = when (tag) {
    "zh-Hans" -> R.string.lookup_script_simplified
    "zh-Hant" -> R.string.lookup_script_traditional
    else -> error("No name for the script $tag")
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
        Language.CHINESE -> FontStrings(
            system = R.string.popup_font_system_zh,
            scriptOnly = R.string.popup_font_script_only_zh,
            scriptOnlyHint = R.string.popup_font_script_only_hint,
            systemMissing = null,
            catalogInfo = R.string.popup_font_proprietary_zh,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.KOREAN -> FontStrings(
            system = R.string.popup_font_system_ko,
            scriptOnly = R.string.popup_font_script_only_ko,
            scriptOnlyHint = R.string.popup_font_script_only_hint,
            systemMissing = null,
            catalogInfo = R.string.popup_font_proprietary_ko,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
    }
