package com.vpr.screenlate.dictionary.api.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LookupSettingsRepositoryTest {
    private val dataStore = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun valuesWrittenWithoutTheSettersAreClampedWhenRead() = runTest {
        // A restored backup writes the preferences directly.
        dataStore.edit {
            it[intPreferencesKey("lookup_scan_length")] = 0
            it[intPreferencesKey("lookup_max_results")] = -5
        }
        val repository = LookupSettingsRepository(dataStore)
        assertThat(repository.current().scanLength).isEqualTo(LookupSettings.MIN_SCAN_LENGTH)
        assertThat(repository.current().maxResults).isEqualTo(0)

        dataStore.edit { it[intPreferencesKey("lookup_scan_length")] = 5000 }
        assertThat(repository.current().scanLength).isEqualTo(LookupSettings.MAX_SCAN_LENGTH)
    }
}
