package com.vpr.screenlate.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.settings.AppColors
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.theme.AppPaletteConfig
import com.vpr.screenlate.core.common.theme.ColorPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppColorsViewModelTest {

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: AppSettingsRepository
    private lateinit var viewModel: AppColorsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = AppSettingsRepository(MemoryStore())
        viewModel = AppColorsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default palette config is violet preset`() = runTest {
        advanceUntilIdle()
        val config = repository.paletteConfig.first()
        assertThat(config.useSystemWallpaper).isFalse()
        assertThat(config.preset).isEqualTo(ColorPreset.VIOLET)
        assertThat(repository.appColors.first()).isEqualTo(AppColors.SCREENLATE)
    }

    @Test
    fun `setPaletteConfig updates preset`() = runTest {
        viewModel.setPaletteConfig(AppPaletteConfig(preset = ColorPreset.EMERALD))
        advanceUntilIdle()

        val config = repository.paletteConfig.first()
        assertThat(config.preset).isEqualTo(ColorPreset.EMERALD)
        assertThat(config.useSystemWallpaper).isFalse()
    }

    @Test
    fun `setPaletteConfig updates custom seed color`() = runTest {
        viewModel.setPaletteConfig(AppPaletteConfig(customSeedColor = 0xFF123456.toInt()))
        advanceUntilIdle()

        val config = repository.paletteConfig.first()
        assertThat(config.preset).isNull()
        assertThat(config.customSeedColor).isEqualTo(0xFF123456.toInt())
        assertThat(config.effectiveSeed).isEqualTo(0xFF123456.toInt())
    }

    @Test
    fun `all presets have non-zero string resource for display name`() {
        ColorPreset.entries.forEach { preset ->
            assertThat(preset.displayNameRes()).isGreaterThan(0)
        }
    }
}
