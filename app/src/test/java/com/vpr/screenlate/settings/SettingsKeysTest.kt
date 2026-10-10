package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.backup.BackupPreferences
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class SettingsKeysTest {

    private val sections = mapOf(
        SettingsSection.BUBBLE to listOf(
            "overlay_bubble_visible", "overlay_dock_side", "overlay_dock_y", "overlay_aim_mode", "overlay_highlight_word",
            "overlay_haptics", "overlay_hidden_packages", "overlay_text_source", "overlay_bubble_size_dp",
            "overlay_small_text", "overlay_ocr_engines", "overlay_ocr_saving", "overlay_dock_top_bottom",
            "e_ink_bubble_before", "e_ink_bubble_after",
        ),
        SettingsSection.BACKGROUND to listOf("overlay_keep_alive"),
        SettingsSection.LOOKUP to listOf(
            "lookup_scan_length", "lookup_max_results", "lookup_romaji", "lookup_single_kanji", "lookup_scan_length_en",
            "lookup_single_kanji_en",
        ),
        SettingsSection.ANKI to listOf("anki_settings", "audio_settings", "audio_sources_chosen", "anki_settings_en", "audio_settings_en"),
        SettingsSection.POPUP to listOf(
            "popup_font", "popup_font_all_text", "popup_font_size", "popup_text_weight", "popup_letter_thickness",
            "popup_custom_css", "popup_copy_definitions", "popup_copy_mode", "overlay_show_source_text", "e_ink_font_before",
            "e_ink_font_after", "popup_hide_after_add", "popup_hide_off_word", "popup_font_en", "popup_font_all_text_en",
            "popup_custom_css_en",
        ),
        SettingsSection.TRANSLATION to listOf(
            "translation_button", "translation_anki", "translation_services", "translation_language", "translation_favorites",
        ),
        SettingsSection.APPEARANCE to listOf("theme_mode", "theme_colors", "e_ink"),
    )
    private val globalOnly = listOf(
        "update_announce", "update_announced_tag", "e_ink_hint_seen", "background_tip_seen", "notification_permission_asked",
        "accessibility_agreed",
    )
    /** Turned-on languages: kept like the dictionaries they need. */
    private val languages = listOf("languages_turned_on", "language_active")
    private val kept = languages + listOf(
        "sort_dictionary_id", "sort_dictionary_id_en", "anki_settings_xx", "bundled_dictionaries_installed", "bundled_dictionaries_declined",
        "bundled_dictionaries_declined_checked", "bundled_dictionaries_records", "bundled_dictionaries_repair",
        "bundled_dictionaries_paused_for",
        "index_texts_decoded", "installed_languages_filled", "update_last_check", "dictionary_reset_attempts",
        "dictionary_reset_gave_up", "dictionary_reset_gave_up_told", "dictionary_imports_cancelled",
        "dictionary_imports_stopped", "languages_first_run", "some_future_key",
    )

    @Test
    fun `every page setting belongs to its page`() {
        for ((section, keys) in sections) {
            keys.forEach { assertThat(SettingsKeys.sectionOf(it)).isEqualTo(section) }
        }
    }

    @Test
    fun `a page reset clears only its own keys`() {
        val all = sections.values.flatten() + globalOnly + kept
        for ((section, keys) in sections) {
            assertThat(all.filter { SettingsKeys.resets(it, setOf(section), everything = false) }).containsExactlyElementsIn(keys)
        }
    }

    @Test
    fun `the full reset also clears announcements and hints but keeps dictionaries and app state`() {
        val all = sections.values.flatten() + globalOnly + kept
        val cleared = all.filter { SettingsKeys.resets(it, SettingsSection.entries.toSet(), everything = true) }
        assertThat(cleared).containsExactlyElementsIn(sections.values.flatten() + globalOnly)
    }

    @Test
    fun `the full reset clears every setting a backup holds`() {
        val all = sections.values.flatten() + globalOnly + kept
        val backedUp = all.filter { BackupPreferences.sectionOf(it) != null && it !in languages }
        assertThat(backedUp).isNotEmpty()
        backedUp.forEach { assertThat(SettingsKeys.resets(it, SettingsSection.entries.toSet(), everything = true)).isTrue() }
    }

    @Test
    fun `a language's keys carry its code, Japanese's none`() {
        assertThat(SettingsKeys.languageOf("anki_settings")).isEqualTo(Language.JAPANESE)
        assertThat(SettingsKeys.languageOf("anki_settings_en")).isEqualTo(Language.ENGLISH)
        assertThat(SettingsKeys.languageOf("popup_font_en")).isEqualTo(Language.ENGLISH)
        assertThat(SettingsKeys.languageOf("popup_font_all_text_en")).isEqualTo(Language.ENGLISH)
        assertThat(SettingsKeys.languageOf("popup_font_size")).isNull()
        assertThat(SettingsKeys.languageOf("lookup_max_results")).isNull()
        assertThat(SettingsKeys.languageOf("anki_settings_xx")).isNull()
    }

    @Test
    fun `a page reset for a language clears the shared keys and that language's`() {
        val lookup = sections.getValue(SettingsSection.LOOKUP)
        fun cleared(language: Language) =
            lookup.filter { SettingsKeys.resets(it, setOf(SettingsSection.LOOKUP), everything = false, language = language) }
        assertThat(cleared(Language.ENGLISH))
            .containsExactly("lookup_max_results", "lookup_romaji", "lookup_scan_length_en", "lookup_single_kanji_en")
        assertThat(cleared(Language.JAPANESE))
            .containsExactly("lookup_scan_length", "lookup_max_results", "lookup_romaji", "lookup_single_kanji")
    }
}
