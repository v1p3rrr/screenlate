package com.vpr.screenlate.backup

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class BackupLanguagesTest {
    private val ja = Language.JAPANESE
    private val en = Language.ENGLISH
    private val zh = Language.CHINESE
    private val ko = Language.KOREAN

    @Test
    fun `a backup holds the profile of the chosen languages`() {
        val state = LanguageProfilesState(listOf(ja, en, zh), active = en, starred = listOf(zh), flags = mapOf(en to "gb", zh to "tw"))
        assertThat(BackupLanguages.limited(state, setOf(ja, zh)))
            .isEqualTo(LanguageProfilesState(listOf(ja, zh), active = ja, starred = listOf(zh), flags = mapOf(zh to "tw")))
        assertThat(BackupLanguages.limited(state, setOf(en))).isEqualTo(LanguageProfilesState(listOf(en), active = en, flags = mapOf(en to "gb")))
        assertThat(BackupLanguages.limited(state, setOf(ko))).isNull()
        assertThat(BackupLanguages.limited(state, emptySet())).isNull()
    }

    @Test
    fun `restoring adds the chosen languages and turns none off`() {
        val current = LanguageProfilesState(listOf(ja, ko), active = ja, starred = listOf(ko), flags = mapOf(en to "us"))
        val backup = LanguageProfilesState(listOf(en, zh), active = zh, starred = listOf(en), flags = mapOf(en to "gb", zh to "tw"))

        val english = BackupLanguages.merged(current, backup, listOf(en))
        assertThat(english.turnedOn).containsExactly(ja, ko, en).inOrder()
        // The backup's active language is not chosen, so the current one stays.
        assertThat(english.active).isEqualTo(ja)
        assertThat(english.starred).containsExactly(ko, en).inOrder()
        assertThat(english.flags).containsExactly(en, "gb")

        val both = BackupLanguages.merged(current, backup, listOf(zh, en))
        assertThat(both.turnedOn).containsExactly(ja, ko, en, zh).inOrder()
        assertThat(both.active).isEqualTo(zh)
        assertThat(both.starred).containsExactly(ko, en).inOrder()
        assertThat(both.flags).containsExactly(en, "gb", zh, "tw")
    }

    @Test
    fun `a backup without a profile only turns its languages on`() {
        val current = LanguageProfilesState(listOf(en), active = en, flags = mapOf(en to "gb"))
        assertThat(BackupLanguages.merged(current, null, listOf(ja)))
            .isEqualTo(LanguageProfilesState(listOf(en, ja), active = en, flags = mapOf(en to "gb")))
    }

    @Test
    fun `on a first run the backup's chosen languages are all there is`() {
        val backup = LanguageProfilesState(listOf(ja, en), active = en, starred = listOf(ja))
        assertThat(BackupLanguages.merged(null, backup, listOf(ja, en)))
            .isEqualTo(LanguageProfilesState(listOf(ja, en), active = en, starred = listOf(ja)))
        assertThat(BackupLanguages.merged(null, null, listOf(zh))).isEqualTo(LanguageProfilesState(listOf(zh), active = zh))
    }

    @Test
    fun `a backup's languages come from its manifest`() {
        val manifest = BackupManifest(appVersion = "2", createdAt = 0, sections = emptyList(), languages = listOf("en", "xx", "ja"))
        assertThat(BackupLanguages.of(manifest, JsonObject(emptyMap()), listOf("zh"))).containsExactly(en, ja).inOrder()
        val none = manifest.copy(languages = emptyList())
        assertThat(BackupLanguages.of(none, JsonObject(emptyMap()), listOf("zh"))).isEmpty()
    }

    @Test
    fun `an older backup's languages are its turned-on ones and those of its files`() {
        val older = BackupManifest(appVersion = "1", createdAt = 0, sections = emptyList())
        val settings = JsonObject(
            mapOf("languages_turned_on" to JsonObject(mapOf("type" to JsonPrimitive("string"), "value" to JsonPrimitive("en,ja")))),
        )
        assertThat(BackupLanguages.of(older, settings, listOf(null, "zh", "xx", "en"))).containsExactly(en, ja, zh).inOrder()
        // Before languages the app was Japanese only.
        assertThat(BackupLanguages.of(older, JsonObject(emptyMap()), listOf("ja", null))).containsExactly(ja)
        assertThat(BackupLanguages.of(older, JsonObject(emptyMap()), listOf("ko"))).containsExactly(ja, ko).inOrder()
    }
}
