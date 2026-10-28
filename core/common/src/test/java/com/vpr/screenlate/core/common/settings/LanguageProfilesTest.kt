package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LanguageProfilesTest {
    private val memory = MemoryDataStore()
    private val profiles = LanguageProfiles(memory)

    @Test
    fun `an upgrade starts with Japanese alone`() = runBlocking<Unit> {
        memory.edit { it[stringPreferencesKey("lookup_scan_length")] = "16" }
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.JAPANESE), Language.JAPANESE))
        assertThat(profiles.current().several).isFalse()
    }

    @Test
    fun `a turned-on language goes last and becomes active`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.JAPANESE, Language.ENGLISH), Language.ENGLISH))
        assertThat(profiles.current().several).isTrue()
        profiles.turnOn(Language.JAPANESE, activate = false)
        assertThat(profiles.current().turnedOn).containsExactly(Language.JAPANESE, Language.ENGLISH).inOrder()
        assertThat(profiles.current().active).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `only a turned-on language becomes active`() = runBlocking<Unit> {
        profiles.setActive(Language.ENGLISH)
        assertThat(profiles.current().active).isEqualTo(Language.JAPANESE)
        profiles.turnOn(Language.ENGLISH, activate = false)
        profiles.setActive(Language.ENGLISH)
        assertThat(profiles.current().active).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `turning the active language off moves to the first one left`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.turnOff(Language.ENGLISH)).isTrue()
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState.DEFAULT)
    }

    @Test
    fun `the last language stays on`() = runBlocking<Unit> {
        assertThat(profiles.turnOff(Language.JAPANESE)).isFalse()
        assertThat(profiles.turnOff(Language.ENGLISH)).isFalse()
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.turnOff(Language.JAPANESE)).isTrue()
        assertThat(profiles.turnOff(Language.ENGLISH)).isFalse()
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
    }

    @Test
    fun `a fresh install asks for the language until it is answered`() = runBlocking<Unit> {
        profiles.settleFirstRun { false }
        assertThat(profiles.firstRunPending.first()).isTrue()
        profiles.settleFirstRun { true }
        assertThat(profiles.firstRunPending.first()).isTrue()
        profiles.finishFirstRun(Language.ENGLISH)
        assertThat(profiles.firstRunPending.first()).isFalse()
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
    }

    @Test
    fun `an upgrade keeps Japanese and never asks`() = runBlocking<Unit> {
        memory.edit { it[stringPreferencesKey("lookup_scan_length")] = "16" }
        profiles.settleFirstRun { false }
        assertThat(profiles.firstRunPending.first()).isFalse()
        assertThat(memory.data.first()[LanguageProfiles.TURNED_ON]).isEqualTo("ja")

        val withDictionaries = LanguageProfiles(MemoryDataStore())
        withDictionaries.settleFirstRun { true }
        assertThat(withDictionaries.firstRunPending.first()).isFalse()
        assertThat(withDictionaries.current()).isEqualTo(LanguageProfilesState.DEFAULT)
    }

    @Test
    fun `only an upgrade changes other settings`() = runBlocking<Unit> {
        val marker = booleanPreferencesKey("upgrade_marker")
        profiles.settleFirstRun(onUpgrade = { it[marker] = true }) { false }
        assertThat(memory.data.first()[marker]).isNull()

        val upgraded = MemoryDataStore()
        LanguageProfiles(upgraded).settleFirstRun(onUpgrade = { it[marker] = true }) { true }
        assertThat(upgraded.data.first()[marker]).isTrue()
        upgraded.edit { it.remove(marker) }
        LanguageProfiles(upgraded).settleFirstRun(onUpgrade = { it[marker] = true }) { true }
        assertThat(upgraded.data.first()[marker]).isNull()
    }

    @Test
    fun `a restored backup answers the first run`() = runBlocking<Unit> {
        profiles.settleFirstRun { false }
        profiles.restore(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
        assertThat(profiles.firstRunPending.first()).isFalse()
    }

    @Test
    fun `codes of a newer version are skipped`() = runBlocking<Unit> {
        memory.edit {
            it[LanguageProfiles.TURNED_ON] = "xx, en,en"
            it[LanguageProfiles.ACTIVE] = "xx"
        }
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
        memory.edit { it[LanguageProfiles.TURNED_ON] = "xx" }
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState.DEFAULT)
    }

    @Test
    fun `a restored state replaces the current one`() = runBlocking<Unit> {
        profiles.restore(LanguageProfilesState(listOf(Language.ENGLISH, Language.JAPANESE), Language.JAPANESE))
        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH, Language.JAPANESE), Language.JAPANESE))
        profiles.restore(LanguageProfilesState(emptyList(), Language.ENGLISH))
        assertThat(profiles.current().turnedOn).containsExactly(Language.ENGLISH, Language.JAPANESE).inOrder()
    }

    @Test
    fun `the state is kept under the keys the backup and reset lists name`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        val stored = memory.data.first()
        assertThat(stored[stringPreferencesKey("languages_turned_on")]).isEqualTo("ja,en")
        assertThat(stored[stringPreferencesKey("language_active")]).isEqualTo("en")
    }

    @Test
    fun `a starred language swaps roles with the active one`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.setStarred(Language.JAPANESE, true)).isTrue()
        assertThat(profiles.current().quick).containsExactly(Language.JAPANESE, Language.ENGLISH).inOrder()

        profiles.setActive(Language.JAPANESE)

        assertThat(profiles.current().active).isEqualTo(Language.JAPANESE)
        assertThat(profiles.current().starred).containsExactly(Language.ENGLISH)
        assertThat(memory.data.first()[LanguageProfiles.STARRED]).isEqualTo("en")
    }

    @Test
    fun `an unstarred language takes the active star and the old active one loses it`() = runBlocking<Unit> {
        // Turning a language on makes it active: Japanese is left without a star although a place is free.
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.current().starred).isEmpty()
        assertThat(profiles.current().quick).containsExactly(Language.ENGLISH)

        profiles.setActive(Language.JAPANESE)
        assertThat(profiles.current().starred).isEmpty()
        assertThat(profiles.current().quick).containsExactly(Language.JAPANESE)
    }

    @Test
    fun `the active language keeps its own star`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        assertThat(profiles.setStarred(Language.ENGLISH, true)).isFalse()
        assertThat(profiles.setStarred(Language.ENGLISH, false)).isFalse()
        assertThat(profiles.current().quick).containsExactly(Language.ENGLISH)
        assertThat(profiles.setStarred(Language.JAPANESE, false)).isFalse()
        assertThat(profiles.setStarred(Language.JAPANESE, true)).isTrue()
        assertThat(profiles.setStarred(Language.JAPANESE, false)).isTrue()
        assertThat(memory.data.first()[LanguageProfiles.STARRED]).isNull()
    }

    @Test
    fun `turning the active language off makes a starred one active`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        profiles.setStarred(Language.JAPANESE, true)
        profiles.setActive(Language.JAPANESE)

        profiles.turnOff(Language.JAPANESE)

        assertThat(profiles.current()).isEqualTo(LanguageProfilesState(listOf(Language.ENGLISH), Language.ENGLISH))
    }

    @Test
    fun `a starred language turned off loses its star`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        profiles.setStarred(Language.JAPANESE, true)
        profiles.turnOff(Language.JAPANESE)
        profiles.turnOn(Language.JAPANESE, activate = false)
        assertThat(profiles.current().starred).isEmpty()
    }

    @Test
    fun `stars past the limit and of languages that are off are left out when read`() = runBlocking<Unit> {
        memory.edit {
            it[LanguageProfiles.TURNED_ON] = "ja"
            it[LanguageProfiles.ACTIVE] = "ja"
            it[LanguageProfiles.STARRED] = "ja,en,xx"
        }
        assertThat(profiles.current().starred).isEmpty()
        assertThat(LanguageProfilesState.MAX_STARRED).isEqualTo(2)
    }

    @Test
    fun `a flag country is one of the language's, the default one is not stored`() = runBlocking<Unit> {
        assertThat(profiles.current().flagCountry(Language.ENGLISH)).isEqualTo("us")
        profiles.setFlag(Language.ENGLISH, "gb")
        assertThat(profiles.current().flagCountry(Language.ENGLISH)).isEqualTo("gb")
        assertThat(memory.data.first()[LanguageProfiles.FLAGS]).isEqualTo("en:gb")

        profiles.setFlag(Language.ENGLISH, "jp")
        assertThat(profiles.current().flagCountry(Language.ENGLISH)).isEqualTo("gb")
        profiles.setFlag(Language.ENGLISH, "us")
        assertThat(memory.data.first()[LanguageProfiles.FLAGS]).isNull()

        memory.edit { it[LanguageProfiles.FLAGS] = "en:xx,xx:us,ja" }
        assertThat(profiles.current().flags).isEmpty()
        assertThat(profiles.current().flagCountry(Language.JAPANESE)).isEqualTo("jp")
    }

    @Test
    fun `a flag chosen for a language that is off stays`() = runBlocking<Unit> {
        profiles.turnOn(Language.ENGLISH)
        profiles.setFlag(Language.ENGLISH, "gb")
        profiles.turnOff(Language.ENGLISH)
        assertThat(profiles.current().flagCountry(Language.ENGLISH)).isEqualTo("gb")
    }

    @Test
    fun `every flag country is a lower-case country code, a region of one, or the language's own flag`() {
        for (language in Language.entries) {
            val countries = language.support.flagCountries
            assertThat(countries).isNotEmpty()
            countries.forEach { assertThat(it).matches("[a-z]{2}(-[a-z]{3})?|lang_${language.code}") }
        }
        assertThat(Language.LATIN.support.flagCountries).containsExactly("lang_la")
        assertThat(Language.WELSH.support.flagCountries).containsExactly("gb-wls")
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
