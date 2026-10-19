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
    Language.ENGLISH, Language.CHINESE, Language.KOREAN, Language.SPANISH, Language.FRENCH, Language.GERMAN, Language.ITALIAN, Language.PORTUGUESE, Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.DUTCH, Language.CZECH, Language.INDONESIAN, Language.MALAY, Language.VIETNAMESE, Language.RUSSIAN,
    null,
    -> DictionaryStrings(
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
        Language.ENGLISH, Language.KOREAN, Language.SPANISH, Language.FRENCH, Language.GERMAN, Language.ITALIAN, Language.PORTUGUESE, Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.DUTCH, Language.CZECH, Language.INDONESIAN, Language.MALAY, Language.VIETNAMESE, Language.RUSSIAN -> null
    }

/**
 * Second lines under Anki's furigana markers in a field's + menu, by marker: what they give in a language whose
 * readings are not kana. Empty where the marker names say it (Japanese) or the markers are not offered.
 */
val Language.furiganaMarkerHints: Map<String, Int>
    get() = when (this) {
        Language.CHINESE -> mapOf(
            "furigana" to R.string.anki_marker_furigana_zh,
            "furigana-plain" to R.string.anki_marker_furigana_plain_zh,
            "sentence-furigana" to R.string.anki_marker_sentence_furigana_zh,
            "sentence-furigana-plain" to R.string.anki_marker_sentence_furigana_plain_zh,
        )
        Language.RUSSIAN -> mapOf(
            "furigana" to R.string.anki_marker_furigana_ru,
            "furigana-plain" to R.string.anki_marker_furigana_plain_ru,
            "sentence-furigana" to R.string.anki_marker_sentence_furigana_ru,
            "sentence-furigana-plain" to R.string.anki_marker_sentence_furigana_plain_ru,
        )
        Language.JAPANESE, Language.ENGLISH, Language.KOREAN, Language.SPANISH, Language.FRENCH, Language.GERMAN, Language.ITALIAN, Language.PORTUGUESE, Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.DUTCH, Language.CZECH, Language.INDONESIAN, Language.MALAY, Language.VIETNAMESE -> emptyMap()
    }

/** The switch for entries of the other characters of a found word (`LanguageSupport.hasCharacterEntries`). */
class CharacterEntryStrings(
    @param:StringRes val label: Int,
    @param:StringRes val hint: Int,
)

val Language.characterEntryStrings: CharacterEntryStrings
    get() = when (this) {
        Language.JAPANESE -> CharacterEntryStrings(R.string.lookup_single_kanji, R.string.lookup_single_kanji_hint)
        Language.ENGLISH, Language.CHINESE, Language.KOREAN, Language.SPANISH, Language.FRENCH, Language.GERMAN, Language.ITALIAN, Language.PORTUGUESE, Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.DUTCH, Language.CZECH, Language.INDONESIAN, Language.MALAY, Language.VIETNAMESE, Language.RUSSIAN ->
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
        Language.SPANISH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_es,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.FRENCH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_fr,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.GERMAN -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_de,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.ITALIAN -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_it,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.PORTUGUESE -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_pt,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.POLISH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_pl,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.TURKISH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_tr,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.LATVIAN -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_lv,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.DUTCH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_nl,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.CZECH -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_cs,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.INDONESIAN -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_id,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.MALAY -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_ms,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.VIETNAMESE -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_vi,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
        Language.RUSSIAN -> FontStrings(
            system = R.string.popup_font_system_default,
            scriptOnly = R.string.popup_font_script_only_ru,
            scriptOnlyHint = R.string.popup_font_script_only_hint_any,
            systemMissing = null,
            catalogInfo = null,
            weightInfo = R.string.popup_text_weight_info_languages,
        )
    }
