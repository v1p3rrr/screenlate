package com.vpr.screenlate.dictionary.api.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
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
        assertThat(repository.current(Language.JAPANESE).scanLength).isEqualTo(LookupSettings.MIN_SCAN_LENGTH)
        assertThat(repository.current(Language.JAPANESE).maxResults).isEqualTo(0)

        dataStore.edit { it[intPreferencesKey("lookup_scan_length")] = 5000 }
        assertThat(repository.current(Language.JAPANESE).scanLength).isEqualTo(LookupSettings.MAX_SCAN_LENGTH)
    }

    @Test
    fun scanLengthAndSingleCharacterEntriesBelongToALanguageTheRestIsShared() = runTest {
        val repository = LookupSettingsRepository(dataStore)
        repository.setScanLength(Language.ENGLISH, 12)
        repository.setSingleKanji(Language.ENGLISH, false)
        repository.setMaxResults(8)
        repository.setRomaji(true)
        val english = repository.current(Language.ENGLISH)
        assertThat(english).isEqualTo(LookupSettings(scanLength = 12, maxResults = 8, romaji = true, singleKanji = false))
        val japanese = repository.current(Language.JAPANESE)
        assertThat(japanese).isEqualTo(LookupSettings(maxResults = 8, romaji = true))
        assertThat(dataStore.data.value.asMap().keys.map { it.name })
            .containsExactly("lookup_scan_length_en", "lookup_single_kanji_en", "lookup_max_results", "lookup_romaji")
    }
}
