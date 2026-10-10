package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LanguageProfilesTest {
    private val memory = MemoryDataStore()
    private val profiles = LanguageProfiles(memory)

    @Test
    fun `an upgrade starts with Japanese alone`() = runBlocking {
        memory.edit { it[stringPreferencesKey("lookup_scan_length")] = "16" }
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.JAPANESE), Language.JAPANESE))
        assertThat(profiles.current().several).isFalse()
    }

    @Test
    fun `a turned-on language goes last and becomes active`() = runBlocking {
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.JAPANESE, Language.ENGLISH), Language.ENGLISH))
        assertThat(profiles.current().several).isTrue()
        profiles.turnOn(Language.JAPANESE, activate = false)
        assertThat(profiles.current().turnedOn).containsExactly(Language.JAPANESE, Language.ENGLISH).inOrder()
        assertThat(profiles.current().active).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `only a turned-on language becomes active`() = runBlocking {
        profiles.setActive(Language.ENGLISH)
        assertThat(profiles.current().active).isEqualTo(Language.JAPANESE)
        profiles.turnOn(Language.ENGLISH, activate = false)
        profiles.setActive(Language.ENGLISH)
        assertThat(profiles.current().active).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `turning the active language off moves to the first one left`() = runBlocking {
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.turnOff(Language.ENGLISH)).isTrue()
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState.DEFAULT)
    }

    @Test
    fun `the last language stays on`() = runBlocking {
        assertThat(profiles.turnOff(Language.JAPANESE)).isFalse()
        assertThat(profiles.turnOff(Language.ENGLISH)).isFalse()
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.turnOff(Language.JAPANESE)).isTrue()
        assertThat(profiles.turnOff(Language.ENGLISH)).isFalse()
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
    }

    @Test
    fun `codes of a newer version are skipped`() = runBlocking {
        memory.edit {
            it[LanguageProfiles.TURNED_ON] = "xx, en,en"
            it[LanguageProfiles.ACTIVE] = "xx"
        }
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
        memory.edit { it[LanguageProfiles.TURNED_ON] = "xx" }
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState.DEFAULT)
    }

    @Test
    fun `a restored state replaces the current one`() = runBlocking {
        profiles.restore(LanguageProfilesState(listOf(Language.ENGLISH, Language.JAPANESE), Language.JAPANESE))
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH, Language.JAPANESE), Language.JAPANESE))
        profiles.restore(LanguageProfilesState(emptyList(), Language.ENGLISH))
        assertThat(profiles.current().turnedOn).containsExactly(Language.ENGLISH, Language.JAPANESE).inOrder()
    }

    @Test
    fun `the state is kept under the keys the backup and reset lists name`() = runBlocking {
        profiles.turnOn(Language.ENGLISH)
        val stored = memory.data.first()
        assertThat(stored[stringPreferencesKey("languages_turned_on")]).isEqualTo("ja,en")
        assertThat(stored[stringPreferencesKey("language_active")]).isEqualTo("en")
    }

    @Test
    fun `Japanese keeps the old key names, other languages get their code`() {
        assertThat(Language.JAPANESE.preferenceKey("lookup_scan_length")).isEqualTo("lookup_scan_length")
        assertThat(Language.ENGLISH.preferenceKey("lookup_scan_length")).isEqualTo("lookup_scan_length_en")
    }

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
