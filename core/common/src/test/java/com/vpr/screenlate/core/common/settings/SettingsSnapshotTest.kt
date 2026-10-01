package com.vpr.screenlate.core.common.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsSnapshotTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the snapshot follows writes and gives repositories a first value`() = runBlocking {
        // DataStore's file storage cannot rename its new file over an existing one on Windows; CI runs this on Linux.
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val store = SnapshotDataStore(settingsDataStore { folder.root.resolve("snapshot.preferences_pb") })
        val settings = AppSettingsRepository(store)
        withTimeout(5_000) { store.snapshot.first { it != null } }
        assertThat(settings.cachedThemeMode).isEqualTo(ThemeMode.SYSTEM)

        store.edit { it[stringPreferencesKey("theme_mode")] = ThemeMode.DARK.name }
        withTimeout(5_000) { store.snapshot.first { settings.cachedThemeMode == ThemeMode.DARK } }
        assertThat(store.cached { it[stringPreferencesKey("theme_mode")] }).isEqualTo("DARK")
    }

    @Test
    fun `a store without a snapshot has no cached values`() {
        val store = settingsDataStore { folder.root.resolve("plain.preferences_pb") }
        assertThat(AppSettingsRepository(store).cachedThemeMode).isNull()
    }
}
