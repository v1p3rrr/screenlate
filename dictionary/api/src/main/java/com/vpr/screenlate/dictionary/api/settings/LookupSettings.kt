package com.vpr.screenlate.dictionary.api.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.cached
import com.vpr.screenlate.core.common.settings.preferenceKey
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Lookup settings of one language: [scanLength], [singleKanji], [script] and [fixedScript] are the language's own, the
 * others are shared. [script] and [fixedScript] are set on the Languages screen.
 *
 * @property scanLength characters from the aim point that a lookup considers; the language's own default
 *   (`LanguageSupport.defaultScanLength`) until it is set.
 * @property maxResults entries shown for a lookup; 0 means no limit.
 * @property romaji Latin text is also looked up as romaji converted to kana.
 * @property singleKanji the kanji of the matched word get their own entries below the results.
 * @property script for a language written in several scripts (`LanguageSupport.scriptTags`), the one for words
 *   written alike in each and for typed searches; null for a language with one.
 * @property fixedScript every word is shown in [script], whatever the text's script, where a dictionary has it so.
 */
data class LookupSettings(
    val scanLength: Int = DEFAULT_SCAN_LENGTH,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val romaji: Boolean = false,
    val singleKanji: Boolean = true,
    val script: String? = null,
    val fixedScript: Boolean = false,
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
            scanLength = (prefs[scanLengthKey(language)] ?: language.support.defaultScanLength)
                .coerceIn(LookupSettings.MIN_SCAN_LENGTH, LookupSettings.MAX_SCAN_LENGTH),
            maxResults = (prefs[MAX_RESULTS] ?: LookupSettings.DEFAULT_MAX_RESULTS).coerceAtLeast(0),
            romaji = prefs[ROMAJI] ?: false,
            singleKanji = prefs[singleKanjiKey(language)] ?: true,
            script = language.support.scriptTags.takeIf { it.isNotEmpty() }?.let { tags ->
                prefs[scriptKey(language)]?.takeIf { it in tags } ?: language.support.defaultScriptTag(Locale.getDefault())
            },
            fixedScript = prefs[fixedScriptKey(language)] ?: false,
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

    /** [tag] is one of the language's `LanguageSupport.scriptTags`. */
    suspend fun setScript(language: Language, tag: String) {
        require(tag in language.support.scriptTags)
        dataStore.edit { it[scriptKey(language)] = tag }
    }

    suspend fun setFixedScript(language: Language, enabled: Boolean) {
        dataStore.edit { it[fixedScriptKey(language)] = enabled }
    }

    private companion object {
        val MAX_RESULTS = intPreferencesKey("lookup_max_results")
        val ROMAJI = booleanPreferencesKey("lookup_romaji")

        fun scanLengthKey(language: Language) = intPreferencesKey(language.preferenceKey("lookup_scan_length"))

        fun singleKanjiKey(language: Language) = booleanPreferencesKey(language.preferenceKey("lookup_single_kanji"))

        fun scriptKey(language: Language) = stringPreferencesKey(language.preferenceKey("language_script"))

        fun fixedScriptKey(language: Language) = booleanPreferencesKey(language.preferenceKey("language_fixed_script"))
    }
}
