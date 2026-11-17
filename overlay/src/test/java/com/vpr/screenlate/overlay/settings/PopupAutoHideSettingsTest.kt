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

class PopupAutoHideSettingsTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val repository = OverlaySettingsRepository(MemoryDataStore())

    @Test
    fun `outside tap auto-hide defaults to off and persists changes`() = runTest {
        assertThat(repository.settings.first().hideOnOutsideTap).isFalse()

        repository.setHideOnOutsideTap(true)
        assertThat(repository.settings.first().hideOnOutsideTap).isTrue()

        repository.setHideOnOutsideTap(false)
        assertThat(repository.settings.first().hideOnOutsideTap).isFalse()
    }
}
