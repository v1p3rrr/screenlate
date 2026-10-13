package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class InstallSettleTest {
    private val store = MemoryDataStore()
    private val settings = AppSettingsRepository(store)
    private val upgraded = booleanPreferencesKey("upgraded")

    @Test
    fun `a new install is not changed`() = runBlocking<Unit> {
        settings.settleInstall(onUpgrade = { it[upgraded] = true }) { false }
        assertThat(store.data.first()[upgraded]).isNull()
    }

    @Test
    fun `stored settings or dictionaries mean an upgrade`() = runBlocking<Unit> {
        store.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }
        settings.settleInstall(onUpgrade = { it[upgraded] = true }) { false }
        assertThat(store.data.first()[upgraded]).isTrue()

        val withDictionaries = MemoryDataStore()
        AppSettingsRepository(withDictionaries).settleInstall(onUpgrade = { it[upgraded] = true }) { true }
        assertThat(withDictionaries.data.first()[upgraded]).isTrue()
    }

    @Test
    fun `the install is settled once`() = runBlocking<Unit> {
        settings.settleInstall(onUpgrade = { it[upgraded] = true }) { false }
        store.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }
        settings.settleInstall(onUpgrade = { it[upgraded] = true }) { true }
        assertThat(store.data.first()[upgraded]).isNull()
    }

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
