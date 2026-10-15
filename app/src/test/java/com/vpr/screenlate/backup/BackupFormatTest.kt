package com.vpr.screenlate.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
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
        assertThat(BackupPreferences.sectionOf("overlay_show_source_text")).isEqualTo(BackupSection.POPUP)
        assertThat(BackupPreferences.sectionOf("anki_settings")).isEqualTo(BackupSection.ANKI)
        assertThat(BackupPreferences.sectionOf("audio_settings")).isEqualTo(BackupSection.AUDIO)
        assertThat(BackupPreferences.sectionOf("translation_services")).isEqualTo(BackupSection.TRANSLATION)
        assertThat(BackupPreferences.sectionOf("translation_favorites")).isEqualTo(BackupSection.TRANSLATION)
        listOf(
            "e_ink_hint_seen", "update_last_check", "update_announced_tag", "sort_dictionary_id", "index_texts_decoded",
            "bundled_dictionaries_installed", "background_tip_seen", "notification_permission_asked",
            "installed_languages_filled", "e_ink_bubble_before", "e_ink_font_after", "accessibility_agreed",
            "backup_sections_off", "backup_fonts_flipped",
        ).forEach { assertThat(BackupPreferences.sectionOf(it)).isNull() }
    }

    @Test
    fun `each language's settings go with their page and the turned-on languages with the general ones`() {
        assertThat(BackupPreferences.sectionOf("languages_turned_on")).isEqualTo(BackupSection.GENERAL)
        assertThat(BackupPreferences.sectionOf("language_active")).isEqualTo(BackupSection.GENERAL)
        assertThat(BackupPreferences.sectionOf("languages_starred")).isEqualTo(BackupSection.GENERAL)
        assertThat(BackupPreferences.sectionOf("language_flags")).isEqualTo(BackupSection.GENERAL)
        assertThat(BackupPreferences.sectionOf("anki_settings_en")).isEqualTo(BackupSection.ANKI)
        assertThat(BackupPreferences.sectionOf("audio_settings_en")).isEqualTo(BackupSection.AUDIO)
        assertThat(BackupPreferences.sectionOf("lookup_scan_length_en")).isEqualTo(BackupSection.LOOKUP)
        assertThat(BackupPreferences.sectionOf("popup_custom_css_en")).isEqualTo(BackupSection.POPUP)
        // A language this version does not know, and the sort dictionaries, which go by title with the list.
        assertThat(BackupPreferences.sectionOf("anki_settings_xx")).isNull()
        assertThat(BackupPreferences.sectionOf("sort_dictionary_id_en")).isNull()
    }

    @Test
    fun `an older backup's sort dictionary is Japanese's`() {
        val older = BackupDictionaryList(dictionaries = emptyList(), sortDictionary = "JPDB")
        assertThat(older.sortDictionary(Language.JAPANESE)).isEqualTo("JPDB")
        assertThat(older.sortDictionary(Language.ENGLISH)).isNull()
        val newer = BackupDictionaryList(
            dictionaries = emptyList(),
            sortDictionary = "JPDB",
            sortDictionaries = mapOf("ja" to "JPDB", "en" to "Wordfreq"),
        )
        assertThat(newer.sortDictionary(Language.ENGLISH)).isEqualTo("Wordfreq")
    }

    @Test
    fun `only the chosen sections go into the settings`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "DARK",
            stringPreferencesKey("anki_settings") to "{}",
            intPreferencesKey("popup_font_size") to 18,
        )
        val encoded = BackupPreferences.encode(prefs, setOf(BackupSection.GENERAL, BackupSection.POPUP))
        assertThat(encoded.keys).containsExactly("popup_font_size", "theme_mode")
    }

    @Test
    fun `a language left out takes its own settings out`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "DARK",
            stringPreferencesKey("anki_settings") to "ja",
            stringPreferencesKey("anki_settings_en") to "en",
            intPreferencesKey("lookup_scan_length_en") to 20,
            stringPreferencesKey("language_script_zh") to "HANT",
        )
        val sections = setOf(BackupSection.GENERAL, BackupSection.ANKI, BackupSection.LOOKUP)
        assertThat(BackupPreferences.encode(prefs, sections, setOf(Language.JAPANESE)).keys).containsExactly("anki_settings", "theme_mode")
        assertThat(BackupPreferences.encode(prefs, sections, setOf(Language.ENGLISH, Language.CHINESE)).keys)
            .containsExactly("anki_settings_en", "language_script_zh", "lookup_scan_length_en", "theme_mode")
    }

    @Test
    fun `without Japanese its audio settings keep only the values every language shares`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("audio_settings") to """{"sources":[{"type":"FORVO"}],"autoPlay":true,"volume":70}""",
            stringPreferencesKey("audio_settings_en") to """{"sources":[]}""",
            booleanPreferencesKey("audio_sources_chosen") to true,
        )
        val encoded = BackupPreferences.decode(BackupPreferences.encode(prefs, setOf(BackupSection.AUDIO), setOf(Language.ENGLISH)))
        assertThat(encoded).containsExactly(
            stringPreferencesKey("audio_settings"), """{"autoPlay":true,"volume":70}""",
            stringPreferencesKey("audio_settings_en"), """{"sources":[]}""",
        )
        assertThat(BackupPreferences.encode(prefs, setOf(BackupSection.BUBBLE), setOf(Language.ENGLISH))).isEmpty()
    }

    @Test
    fun `restoring leaves the settings of languages not chosen`() {
        val current = mutablePreferencesOf(
            stringPreferencesKey("anki_settings") to "old ja",
            stringPreferencesKey("anki_settings_en") to "old en",
            stringPreferencesKey("anki_settings_zh") to "old zh",
        )
        val backup = BackupPreferences.decode(
            BackupPreferences.encode(
                mutablePreferencesOf(
                    stringPreferencesKey("anki_settings") to "new ja",
                    stringPreferencesKey("anki_settings_en") to "new en",
                ),
            ),
        )
        BackupPreferences.restore(current, backup, setOf(BackupSection.ANKI), setOf(Language.JAPANESE, Language.CHINESE))
        assertThat(current[stringPreferencesKey("anki_settings")]).isEqualTo("new ja")
        assertThat(current[stringPreferencesKey("anki_settings_en")]).isEqualTo("old en")
        // Chosen but not in the backup: back to its defaults, as a restored section is exactly the backup's.
        assertThat(current[stringPreferencesKey("anki_settings_zh")]).isNull()
    }

    @Test
    fun `restoring audio without Japanese takes only the shared values`() {
        val key = stringPreferencesKey("audio_settings")
        val current = mutablePreferencesOf(key to """{"sources":[{"type":"FORVO"}],"volume":50}""")
        val backup = BackupPreferences.decode(
            BackupPreferences.encode(mutablePreferencesOf(key to """{"sources":[],"autoPlay":true,"volume":80}""")),
        )
        BackupPreferences.restore(current, backup, setOf(BackupSection.AUDIO), setOf(Language.ENGLISH))
        assertThat(current[key]).isEqualTo("""{"sources":[{"type":"FORVO"}],"autoPlay":true,"volume":80}""")
    }

    @Test
    fun `restoring General adds the chosen languages to the turned-on ones`() {
        val current = mutablePreferencesOf(
            stringPreferencesKey("languages_turned_on") to "ja",
            stringPreferencesKey("language_active") to "ja",
            stringPreferencesKey("theme_mode") to "LIGHT",
        )
        val backup = BackupPreferences.decode(
            BackupPreferences.encode(
                mutablePreferencesOf(
                    stringPreferencesKey("languages_turned_on") to "en,zh",
                    stringPreferencesKey("language_active") to "zh",
                    stringPreferencesKey("theme_mode") to "DARK",
                ),
            ),
        )
        BackupPreferences.restore(current, backup, setOf(BackupSection.GENERAL), setOf(Language.ENGLISH, Language.CHINESE))
        assertThat(current[stringPreferencesKey("languages_turned_on")]).isEqualTo("ja,en,zh")
        assertThat(current[stringPreferencesKey("language_active")]).isEqualTo("zh")
        assertThat(current[stringPreferencesKey("theme_mode")]).isEqualTo("DARK")

        val untouched = mutablePreferencesOf(stringPreferencesKey("languages_turned_on") to "ja")
        BackupPreferences.restore(untouched, backup, setOf(BackupSection.BUBBLE), setOf(Language.ENGLISH))
        assertThat(untouched[stringPreferencesKey("languages_turned_on")]).isEqualTo("ja")
    }

    @Test
    fun `restoring General on a first run turns on the chosen languages alone`() {
        val current = mutablePreferencesOf(booleanPreferencesKey("languages_first_run") to true)
        val backup = BackupPreferences.decode(BackupPreferences.encode(mutablePreferencesOf(stringPreferencesKey("languages_turned_on") to "ja,en")))
        BackupPreferences.restore(current, backup, setOf(BackupSection.GENERAL), setOf(Language.ENGLISH))
        assertThat(current[stringPreferencesKey("languages_turned_on")]).isEqualTo("en")
        assertThat(current[booleanPreferencesKey("languages_first_run")]).isNull()
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
    fun `an older backup without the translation button keeps it on`() {
        val button = booleanPreferencesKey("translation_button")
        val current = mutablePreferencesOf(button to false)
        val older = BackupPreferences.decode(BackupPreferences.encode(mutablePreferencesOf(booleanPreferencesKey("translation_anki") to false)))
        BackupPreferences.restore(current, older, setOf(BackupSection.TRANSLATION))
        assertThat(current[button]).isTrue()

        val switchedOff = BackupPreferences.decode(BackupPreferences.encode(mutablePreferencesOf(button to false)))
        BackupPreferences.restore(current, switchedOff, setOf(BackupSection.TRANSLATION))
        assertThat(current[button]).isFalse()

        val other = mutablePreferencesOf()
        BackupPreferences.restore(other, older, setOf(BackupSection.BUBBLE))
        assertThat(other[button]).isNull()
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
        assertThat(BackupLayout.modelFile("models/det/dict/keys.txt")).isEqualTo("det" to "dict/keys.txt")
        assertThat(BackupLayout.modelFile("models/det/../../evil")).isNull()
        assertThat(BackupLayout.modelFile("models/../x")).isNull()
        assertThat(BackupLayout.modelFile("models/det/")).isNull()
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

    @Test
    fun `a backup without e-ink mode turns it off`() {
        val on = BackupPreferences.decode(BackupPreferences.encode(mutablePreferencesOf(booleanPreferencesKey("e_ink") to true)))
        assertThat(BackupPreferences.eInk(on)).isTrue()
        assertThat(BackupPreferences.eInk(emptyMap())).isFalse()
    }
}
