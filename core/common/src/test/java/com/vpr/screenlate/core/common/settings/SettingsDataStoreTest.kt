package com.vpr.screenlate.core.common.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a damaged file gives the defaults and can be written again`() = runBlocking {
        // DataStore's file storage cannot rename its new file over an existing one on Windows; CI runs this on Linux.
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val file = folder.root.resolve("settings.preferences_pb")
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x01, 0x02))
        val settings = AppSettingsRepository(settingsDataStore { file })
        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        settings.setThemeMode(ThemeMode.DARK)
        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.DARK)
    }
}
