package com.vpr.screenlate.dictionary.api.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * @property scanLength characters from the aim point that a lookup considers.
 * @property maxResults entries shown for a lookup; 0 means no limit.
 * @property romaji Latin text is also looked up as romaji converted to kana.
 * @property singleKanji the kanji of the matched word get their own entries below the results.
 */
data class LookupSettings(
    val scanLength: Int = DEFAULT_SCAN_LENGTH,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val romaji: Boolean = false,
    val singleKanji: Boolean = true,
) {
    companion object {
        /** Yomitan's defaults. */
        const val DEFAULT_SCAN_LENGTH = 16
        const val DEFAULT_MAX_RESULTS = 32
        const val MIN_SCAN_LENGTH = 1
        const val MAX_SCAN_LENGTH = 100

        /** Above this, rendering long entry lists gets noticeably slower. */
        const val SLOW_MAX_RESULTS = 64
    }
}

@Singleton
class LookupSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<LookupSettings> = dataStore.data.map { prefs ->
        LookupSettings(
            scanLength = prefs[SCAN_LENGTH] ?: LookupSettings.DEFAULT_SCAN_LENGTH,
            maxResults = prefs[MAX_RESULTS] ?: LookupSettings.DEFAULT_MAX_RESULTS,
            romaji = prefs[ROMAJI] ?: false,
            singleKanji = prefs[SINGLE_KANJI] ?: true,
        )
    }

    suspend fun current(): LookupSettings = settings.first()

    suspend fun setScanLength(value: Int) {
        dataStore.edit {
            it[SCAN_LENGTH] = value.coerceIn(LookupSettings.MIN_SCAN_LENGTH, LookupSettings.MAX_SCAN_LENGTH)
        }
    }

    suspend fun setMaxResults(value: Int) {
        dataStore.edit { it[MAX_RESULTS] = value.coerceAtLeast(0) }
    }

    suspend fun setRomaji(enabled: Boolean) {
        dataStore.edit { it[ROMAJI] = enabled }
    }

    suspend fun setSingleKanji(enabled: Boolean) {
        dataStore.edit { it[SINGLE_KANJI] = enabled }
    }

    private companion object {
        val SCAN_LENGTH = intPreferencesKey("lookup_scan_length")
        val MAX_RESULTS = intPreferencesKey("lookup_max_results")
        val ROMAJI = booleanPreferencesKey("lookup_romaji")
        val SINGLE_KANJI = booleanPreferencesKey("lookup_single_kanji")
    }
}
