package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.theme.AppPaletteConfig
import com.vpr.screenlate.core.common.theme.ColorPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppSettingsPaletteTest {

    private class MemoryDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `default palette config is violet preset`() = runTest {
        val repository = AppSettingsRepository(MemoryDataStore())
        val config = repository.paletteConfig.first()

        assertThat(config.useSystemWallpaper).isFalse()
        assertThat(config.preset).isEqualTo(ColorPreset.VIOLET)
        assertThat(config.customSeedColor).isNull()
        assertThat(config.effectiveSeed).isEqualTo(ColorPreset.VIOLET.seedColor)
        assertThat(repository.appColors.first()).isEqualTo(AppColors.SCREENLATE)
    }

    @Test
    fun `setting preset updates palette config and legacy colors flow`() = runTest {
        val repository = AppSettingsRepository(MemoryDataStore())
        repository.setPaletteConfig(AppPaletteConfig(preset = ColorPreset.EMERALD))

        val config = repository.paletteConfig.first()
        assertThat(config.useSystemWallpaper).isFalse()
        assertThat(config.preset).isEqualTo(ColorPreset.EMERALD)
        assertThat(config.customSeedColor).isNull()
        assertThat(config.effectiveSeed).isEqualTo(ColorPreset.EMERALD.seedColor)
        assertThat(repository.appColors.first()).isEqualTo(AppColors.SCREENLATE)
    }

    @Test
    fun `setting custom color updates palette config`() = runTest {
        val repository = AppSettingsRepository(MemoryDataStore())
        val customColor = 0xFF123456.toInt()
        repository.setPaletteConfig(AppPaletteConfig(customSeedColor = customColor))

        val config = repository.paletteConfig.first()
        assertThat(config.useSystemWallpaper).isFalse()
        assertThat(config.preset).isNull()
        assertThat(config.customSeedColor).isEqualTo(customColor)
        assertThat(config.effectiveSeed).isEqualTo(customColor)
    }

    @Test
    fun `setting system wallpaper updates palette config and legacy colors flow`() = runTest {
        val repository = AppSettingsRepository(MemoryDataStore())
        repository.setPaletteConfig(AppPaletteConfig(useSystemWallpaper = true))

        val config = repository.paletteConfig.first()
        assertThat(config.useSystemWallpaper).isTrue()
        assertThat(repository.appColors.first()).isEqualTo(AppColors.SYSTEM)
    }

    @Test
    fun `legacy preference fallback`() = runTest {
        val prefs = emptyPreferences().toMutablePreferences().apply {
            this[stringPreferencesKey("theme_colors")] = AppColors.SYSTEM.name
        }
        val repository = AppSettingsRepository(MemoryDataStore(prefs))
        val config = repository.paletteConfig.first()

        assertThat(config.useSystemWallpaper).isTrue()
        assertThat(repository.appColors.first()).isEqualTo(AppColors.SYSTEM)
    }
}
