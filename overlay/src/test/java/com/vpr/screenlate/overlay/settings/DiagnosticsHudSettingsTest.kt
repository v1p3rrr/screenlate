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

class DiagnosticsHudSettingsTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val repository = OverlaySettingsRepository(MemoryDataStore())

    @Test
    fun `diagnostics hud defaults to off and toggles correctly`() = runTest {
        assertThat(repository.settings.first().diagnosticsHud).isFalse()

        repository.setDiagnosticsHud(true)
        assertThat(repository.settings.first().diagnosticsHud).isTrue()

        repository.setDiagnosticsHud(false)
        assertThat(repository.settings.first().diagnosticsHud).isFalse()
    }
}
