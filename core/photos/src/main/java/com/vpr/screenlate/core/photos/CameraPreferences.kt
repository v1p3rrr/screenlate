package com.vpr.screenlate.core.photos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CameraPreferenceStore

/** [active] is the app's active language; [flags] holds the flag country of each of the [enabled] languages. */
data class CameraLanguages(
    val selected: Language,
    val enabled: List<Language>,
    val active: Language = selected,
    val flags: Map<Language, String> = emptyMap(),
)

/** @property autoSave every new photo joins the history right after capture. */
data class HistorySettings(val autoSave: Boolean = false, val limits: HistoryLimits = HistoryLimits())

/**
 * Camera-only preferences, outside general settings resets and backup. The first use remembers the active language. A
 * remembered language that is turned off gives way to the active one and returns when it is turned on again.
 */
@Singleton
class CameraPreferences @Inject constructor(
    @param:CameraPreferenceStore private val store: DataStore<Preferences>,
    private val profiles: LanguageProfiles,
) {
    val languages = combine(store.data, profiles.state) { preferences, profile ->
        val selected = preferences[LANGUAGE]?.let(Language::of)?.takeIf { it in profile.turnedOn } ?: profile.active
        CameraLanguages(selected, profile.turnedOn, profile.active, profile.turnedOn.associateWith(profile::flagCountry))
    }.onEach { selection ->
        store.edit { preferences ->
            if (preferences[LANGUAGE]?.let(Language::of) == null) preferences[LANGUAGE] = selection.selected.code
        }
    }.distinctUntilChanged()

    suspend fun select(language: Language) {
        if (language in profiles.current().turnedOn) store.edit { it[LANGUAGE] = language.code }
    }

    val history = store.data.map { preferences ->
        HistorySettings(
            autoSave = preferences[AUTO_SAVE] ?: false,
            limits = HistoryLimits(
                count = preferences[COUNT_LIMIT]?.coerceAtLeast(0) ?: 0,
                megabytes = preferences[SIZE_LIMIT]?.coerceAtLeast(0) ?: 0,
            ),
        )
    }.distinctUntilChanged()

    suspend fun setAutoSave(on: Boolean) {
        store.edit { it[AUTO_SAVE] = on }
    }

    /** High accuracy: photos are read by tiles ([PhotoOcrSettings.tiled]) instead of whole. */
    val highAccuracy = store.data.map { it[HIGH_ACCURACY] ?: false }.distinctUntilChanged()

    suspend fun setHighAccuracy(on: Boolean) {
        store.edit { it[HIGH_ACCURACY] = on }
    }

    suspend fun setLimits(limits: HistoryLimits) {
        store.edit {
            it[COUNT_LIMIT] = limits.count
            it[SIZE_LIMIT] = limits.megabytes
        }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_SAVE = booleanPreferencesKey("history_auto_save")
        val COUNT_LIMIT = intPreferencesKey("history_count_limit")
        val SIZE_LIMIT = intPreferencesKey("history_size_limit_mb")
        val HIGH_ACCURACY = booleanPreferencesKey("high_accuracy")
    }
}
