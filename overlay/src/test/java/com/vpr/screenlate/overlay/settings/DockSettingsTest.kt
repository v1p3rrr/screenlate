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

class DockSettingsTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val repository = OverlaySettingsRepository(MemoryDataStore())

    private suspend fun dock() = repository.settings.first().let { it.dockSide to it.dockPosition }

    @Test
    fun `a top or bottom dock goes to the nearer side, at its end next to that edge`() {
        assertThat(DockSide.sideFor(DockSide.TOP, 0.3f)).isEqualTo(DockSide.LEFT to 0f)
        assertThat(DockSide.sideFor(DockSide.BOTTOM, 0.7f)).isEqualTo(DockSide.RIGHT to 1f)
        assertThat(DockSide.sideFor(DockSide.RIGHT, 0.45f)).isEqualTo(DockSide.RIGHT to 0.45f)
    }

    @Test
    fun `turning top and bottom off moves the dock to a side`() = runTest {
        repository.setDockTopBottom(true)
        repository.setDock(DockSide.BOTTOM, 0.2f)
        assertThat(dock()).isEqualTo(DockSide.BOTTOM to 0.2f)

        repository.setDockTopBottom(false)
        assertThat(dock()).isEqualTo(DockSide.LEFT to 1f)
        // Turned on again, the dock stays at the side.
        repository.setDockTopBottom(true)
        assertThat(dock()).isEqualTo(DockSide.LEFT to 1f)
    }

    @Test
    fun `a top dock without the switch reads as a side dock`() = runTest {
        // As a restored backup may hold it.
        repository.setDock(DockSide.TOP, 0.8f)
        assertThat(dock()).isEqualTo(DockSide.RIGHT to 0f)
    }

    @Test
    fun `auto-hide is off by default`() = runTest {
        val settings = repository.settings.first()
        assertThat(settings.hideAfterAdd).isFalse()
        assertThat(settings.hideOffWord).isFalse()
        assertThat(settings.dockTopBottom).isFalse()
        repository.setHideAfterAdd(true)
        repository.setHideOffWord(true)
        assertThat(repository.settings.first().hideAfterAdd).isTrue()
        assertThat(repository.settings.first().hideOffWord).isTrue()
    }
}
