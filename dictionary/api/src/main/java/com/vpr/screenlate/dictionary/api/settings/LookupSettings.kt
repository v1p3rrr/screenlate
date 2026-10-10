package com.vpr.screenlate.dictionary.api.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.cached
import com.vpr.screenlate.core.common.settings.preferenceKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Lookup settings of one language: [scanLength] and [singleKanji] are the language's own, the others are shared.
 *
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
    }
}

@Singleton
class LookupSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    // Restored backups write the values without the setters' checks.
    private fun read(prefs: Preferences, language: Language): LookupSettings =
        LookupSettings(
            scanLength = (prefs[scanLengthKey(language)] ?: LookupSettings.DEFAULT_SCAN_LENGTH)
                .coerceIn(LookupSettings.MIN_SCAN_LENGTH, LookupSettings.MAX_SCAN_LENGTH),
            maxResults = (prefs[MAX_RESULTS] ?: LookupSettings.DEFAULT_MAX_RESULTS).coerceAtLeast(0),
            romaji = prefs[ROMAJI] ?: false,
            singleKanji = prefs[singleKanjiKey(language)] ?: true,
        )

    fun settings(language: Language): Flow<LookupSettings> = dataStore.data.map { read(it, language) }

    /** The settings as last read, for a screen's first frame; null before the first read. */
    fun cachedSettings(language: Language): LookupSettings? = dataStore.cached { read(it, language) }

    suspend fun current(language: Language): LookupSettings = settings(language).first()

    suspend fun setScanLength(language: Language, value: Int) {
        dataStore.edit {
            it[scanLengthKey(language)] = value.coerceIn(LookupSettings.MIN_SCAN_LENGTH, LookupSettings.MAX_SCAN_LENGTH)
        }
    }

    suspend fun setMaxResults(value: Int) {
        dataStore.edit { it[MAX_RESULTS] = value.coerceAtLeast(0) }
    }

    suspend fun setRomaji(enabled: Boolean) {
        dataStore.edit { it[ROMAJI] = enabled }
    }

    suspend fun setSingleKanji(language: Language, enabled: Boolean) {
        dataStore.edit { it[singleKanjiKey(language)] = enabled }
    }

    private companion object {
        val MAX_RESULTS = intPreferencesKey("lookup_max_results")
        val ROMAJI = booleanPreferencesKey("lookup_romaji")

        fun scanLengthKey(language: Language) = intPreferencesKey(language.preferenceKey("lookup_scan_length"))

        fun singleKanjiKey(language: Language) = booleanPreferencesKey(language.preferenceKey("lookup_single_kanji"))
    }
}
