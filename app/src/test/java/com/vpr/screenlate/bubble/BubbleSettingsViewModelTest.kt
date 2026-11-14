package com.vpr.screenlate.bubble

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.theme.ColorPreset
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
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
class BubbleSettingsViewModelTest {

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: OverlaySettingsRepository
    private lateinit var viewModel: BubbleSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = OverlaySettingsRepository(MemoryStore())
        viewModel = BubbleSettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default bubble color settings are use app color true and violet custom color`() = runTest {
        advanceUntilIdle()
        val settings = repository.settings.first()
        assertThat(settings.bubbleUseAppColor).isTrue()
        assertThat(settings.bubbleCustomColor).isEqualTo(ColorPreset.VIOLET.primarySeed)
    }

    @Test
    fun `setBubbleUseAppColor updates repository`() = runTest {
        viewModel.setBubbleUseAppColor(false)
        advanceUntilIdle()

        val settings = repository.settings.first()
        assertThat(settings.bubbleUseAppColor).isFalse()
    }

    @Test
    fun `setBubbleCustomColor updates repository`() = runTest {
        viewModel.setBubbleCustomColor(ColorPreset.OCEAN.primarySeed)
        advanceUntilIdle()

        val settings = repository.settings.first()
        assertThat(settings.bubbleCustomColor).isEqualTo(ColorPreset.OCEAN.primarySeed)
    }
}
