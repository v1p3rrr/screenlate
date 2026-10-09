package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class AccessibilityAgreedTest {
    private val memory = MemoryDataStore()
    private val store = SnapshotDataStore(memory)
    private val settings = AppSettingsRepository(store)

    @Test
    fun `not agreed until the user agrees`() = runBlocking<Unit> {
        assertThat(settings.accessibilityAgreed.first()).isFalse()
        withTimeout(5_000) { store.snapshot.first { it != null } }
        assertThat(settings.cachedAccessibilityAgreed).isFalse()

        settings.setAccessibilityAgreed()

        assertThat(settings.accessibilityAgreed.first()).isTrue()
        withTimeout(5_000) { store.snapshot.first { settings.cachedAccessibilityAgreed == true } }
    }

    @Test
    fun `the agreement is kept under the key the reset and backup lists name`() = runBlocking {
        settings.setAccessibilityAgreed()
        assertThat(memory.data.first()[booleanPreferencesKey("accessibility_agreed")]).isTrue()
    }

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
