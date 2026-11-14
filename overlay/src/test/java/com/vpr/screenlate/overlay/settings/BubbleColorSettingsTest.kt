package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BubbleColorSettingsTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val repository = OverlaySettingsRepository(MemoryDataStore())

    @Test
    fun `bubble color settings default to using app theme`() = runTest {
        val settings = repository.settings.first()
        assertThat(settings.bubbleUseAppColor).isTrue()
        assertThat(settings.bubbleCustomColor).isEqualTo(OverlaySettings.DEFAULT_BUBBLE_COLOR)
    }

    @Test
    fun `changing bubble use app color and custom color persists`() = runTest {
        val customColor = 0xFF059669.toInt() // Emerald
        repository.setBubbleUseAppColor(false)
        repository.setBubbleCustomColor(customColor)

        val settings = repository.settings.first()
        assertThat(settings.bubbleUseAppColor).isFalse()
        assertThat(settings.bubbleCustomColor).isEqualTo(customColor)
    }
}
