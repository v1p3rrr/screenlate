package com.vpr.screenlate.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import org.junit.Test

class BackupFormatTest {

    @Test
    fun `keys belong to their sections and app state is left out`() {
        assertThat(BackupPreferences.sectionOf("theme_mode")).isEqualTo(BackupSection.GENERAL)
        assertThat(BackupPreferences.sectionOf("e_ink")).isEqualTo(BackupSection.GENERAL)
        assertThat(BackupPreferences.sectionOf("overlay_hidden_packages")).isEqualTo(BackupSection.BUBBLE)
        assertThat(BackupPreferences.sectionOf("lookup_scan_length")).isEqualTo(BackupSection.LOOKUP)
        assertThat(BackupPreferences.sectionOf("popup_custom_css")).isEqualTo(BackupSection.POPUP)
        assertThat(BackupPreferences.sectionOf("anki_settings")).isEqualTo(BackupSection.ANKI)
        assertThat(BackupPreferences.sectionOf("audio_settings")).isEqualTo(BackupSection.AUDIO)
        listOf(
            "e_ink_hint_seen", "update_last_check", "update_announced_tag", "sort_dictionary_id", "index_texts_decoded",
            "bundled_dictionaries_installed", "background_tip_seen", "notification_permission_asked",
            "installed_languages_filled",
        ).forEach { assertThat(BackupPreferences.sectionOf(it)).isNull() }
    }

    @Test
    fun `preferences survive encoding with their types`() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("overlay_haptics") to true,
            intPreferencesKey("popup_font_size") to 18,
            floatPreferencesKey("overlay_dock_y") to 0.3f,
            stringPreferencesKey("theme_mode") to "DARK",
            stringSetPreferencesKey("overlay_hidden_packages") to setOf("a.b", "c.d"),
            longPreferencesKey("update_last_check") to 5L,
        )
        val decoded = BackupPreferences.decode(BackupPreferences.encode(prefs))
        assertThat(decoded).containsExactly(
            booleanPreferencesKey("overlay_haptics"), true,
            intPreferencesKey("popup_font_size"), 18,
            floatPreferencesKey("overlay_dock_y"), 0.3f,
            stringPreferencesKey("theme_mode"), "DARK",
            stringSetPreferencesKey("overlay_hidden_packages"), setOf("a.b", "c.d"),
        )
    }

    @Test
    fun `restoring a section replaces it and leaves the others`() {
        val current = mutablePreferencesOf(
            booleanPreferencesKey("overlay_haptics") to true,
            intPreferencesKey("overlay_bubble_size_dp") to 40,
            stringPreferencesKey("theme_mode") to "LIGHT",
            longPreferencesKey("update_last_check") to 5L,
        )
        val backup = BackupPreferences.decode(
            BackupPreferences.encode(
                mutablePreferencesOf(
                    intPreferencesKey("overlay_bubble_size_dp") to 56,
                    stringPreferencesKey("theme_mode") to "DARK",
                ),
            ),
        )
        BackupPreferences.restore(current, backup, setOf(BackupSection.BUBBLE))
        // The bubble section is exactly the backup's: haptics not in it is reset to its default.
        assertThat(current[booleanPreferencesKey("overlay_haptics")]).isNull()
        assertThat(current[intPreferencesKey("overlay_bubble_size_dp")]).isEqualTo(56)
        assertThat(current[stringPreferencesKey("theme_mode")]).isEqualTo("LIGHT")
        assertThat(current[longPreferencesKey("update_last_check")]).isEqualTo(5L)
    }

    @Test
    fun `dictionary list sets order, switches and languages of installed dictionaries`() {
        val installed = listOf(
            entity(1, "JMdict", priority = 0),
            entity(2, "Jitendex.org [2026-01-04]", priority = 1),
            entity(3, "JPDB", priority = 2, kind = DictionaryKind.FREQUENCY),
            entity(4, "Local only", priority = 3),
        )
        val backup = listOf(
            backup("JPDB", priority = 0, enabled = false),
            backup("Jitendex.org [2026-08-11]", priority = 1, source = "ja", target = "en"),
            backup("Gone", priority = 2),
            backup("JMdict", priority = 3),
        )
        val outcome = BackupDictionaries.applyList(installed, backup)
        assertThat(outcome.updated.map { it.id }).containsExactly(3L, 2L, 1L, 4L).inOrder()
        assertThat(outcome.updated.map { it.priority }).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(outcome.updated.first { it.id == 3L }.enabled).isFalse()
        assertThat(outcome.updated.first { it.id == 2L }.targetLanguage).isEqualTo("en")
        assertThat(outcome.missing).containsExactly("Gone")
    }

    @Test
    fun `frequency dictionaries keep no target language`() {
        val outcome = BackupDictionaries.applyList(
            listOf(entity(1, "JPDB", priority = 0, kind = DictionaryKind.FREQUENCY)),
            listOf(backup("JPDB", priority = 0, source = "ja", target = "en")),
        )
        assertThat(outcome.updated.single().targetLanguage).isNull()
        assertThat(outcome.updated.single().sourceLanguage).isEqualTo("ja")
    }

    @Test
    fun `entries outside the backup layout are rejected`() {
        assertThat(BackupLayout.dictionaryFile("dictionary-files/2/terms/index.bin")).isEqualTo(2 to "terms/index.bin")
        assertThat(BackupLayout.dictionaryFile("dictionary-files/2/../../evil")).isNull()
        assertThat(BackupLayout.dictionaryFile("dictionary-files/x/file")).isNull()
        assertThat(BackupLayout.dictionaryFile("dictionary-files/1/")).isNull()
        assertThat(BackupLayout.fontFile("fonts/noto.ttf")).isEqualTo("noto.ttf")
        assertThat(BackupLayout.fontFile("fonts/sub/noto.ttf")).isNull()
        assertThat(BackupLayout.fontFile("fonts/..")).isNull()
    }

    @Test
    fun `a backup dictionary becomes the same registry entry`() {
        val original = entity(7, "JMdict", priority = 4).copy(author = "EDRDG", downloadUrl = "https://example.org/jmdict.zip")
        val restored = BackupDictionary.of(original, files = true).toEntity(directory = "new")
        assertThat(restored).isEqualTo(original.copy(id = 0, directory = "new"))
    }

    private fun entity(id: Long, title: String, priority: Int, kind: DictionaryKind = DictionaryKind.TERM) = DictionaryEntity(
        id = id,
        title = title,
        revision = "1",
        kind = kind,
        sourceLanguage = "ja",
        targetLanguage = if (kind.hasTarget) "de" else null,
        frequencyMode = null,
        enabled = true,
        priority = priority,
        directory = "dir$id",
        termCount = 1,
        frequencyCount = 0,
        pitchCount = 0,
        kanjiCount = 0,
        mediaCount = 0,
        isUpdatable = false,
        indexUrl = null,
        downloadUrl = null,
        author = null,
        url = null,
        description = null,
        attribution = null,
        bundled = false,
        importedAt = 1,
    )

    private fun backup(title: String, priority: Int, enabled: Boolean = true, source: String? = "ja", target: String? = "de") =
        BackupDictionary(
            title = title,
            revision = "1",
            kind = "TERM",
            sourceLanguage = source,
            targetLanguage = target,
            enabled = enabled,
            priority = priority,
        )
}
