package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.backup.BackupPreferences
import org.junit.Test

class SettingsKeysTest {

    private val sections = mapOf(
        SettingsSection.BUBBLE to listOf(
            "overlay_bubble_visible", "overlay_dock_side", "overlay_dock_y", "overlay_aim_mode", "overlay_highlight_word",
            "overlay_haptics", "overlay_hidden_packages", "overlay_text_source", "overlay_bubble_size_dp",
            "overlay_small_text", "overlay_show_source_text", "overlay_ocr_engines", "overlay_ocr_saving",
            "e_ink_bubble_before", "e_ink_bubble_after",
        ),
        SettingsSection.BACKGROUND to listOf("overlay_keep_alive"),
        SettingsSection.LOOKUP to listOf("lookup_scan_length", "lookup_max_results", "lookup_romaji", "lookup_single_kanji"),
        SettingsSection.ANKI to listOf("anki_settings", "audio_settings", "audio_sources_chosen"),
        SettingsSection.POPUP to listOf(
            "popup_font", "popup_font_all_text", "popup_font_size", "popup_text_weight", "popup_letter_thickness",
            "popup_custom_css", "popup_copy_definitions", "popup_copy_mode", "e_ink_font_before", "e_ink_font_after",
        ),
        SettingsSection.APPEARANCE to listOf("theme_mode", "theme_colors", "e_ink"),
    )
    private val globalOnly = listOf(
        "update_announce", "update_announced_tag", "e_ink_hint_seen", "background_tip_seen", "notification_permission_asked",
    )
    private val kept = listOf(
        "sort_dictionary_id", "bundled_dictionaries_installed", "bundled_dictionaries_declined",
        "bundled_dictionaries_declined_checked", "bundled_dictionaries_records", "bundled_dictionaries_repair",
        "bundled_dictionaries_paused_for",
        "index_texts_decoded", "installed_languages_filled", "update_last_check", "dictionary_reset_attempts",
        "dictionary_reset_gave_up", "dictionary_reset_gave_up_told", "dictionary_imports_cancelled",
        "dictionary_imports_stopped", "some_future_key",
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
        val backedUp = all.filter { BackupPreferences.sectionOf(it) != null }
        assertThat(backedUp).isNotEmpty()
        backedUp.forEach { assertThat(SettingsKeys.resets(it, SettingsSection.entries.toSet(), everything = true)).isTrue() }
    }
}
