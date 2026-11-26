package com.vpr.screenlate.core.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.vpr.screenlate.core.common.settings.cached
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RecordingSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val readSettings: (Preferences) -> RecordingSettings = { prefs ->
        val enabled = prefs[RECORDING_ENABLED] ?: false
        val defaultEditor = prefs[DEFAULT_EDITOR] ?: true
        val h = prefs[HISTORY_MILLIS] ?: 300_000L
        val w = prefs[WAITING_MILLIS] ?: 300_000L
        val e = prefs[EDITOR_MILLIS] ?: 120_000L
        val validatedLimits = runCatching {
            RecordingLimits(historyMillis = h, waitingMillis = w, editorMillis = e)
        }.getOrDefault(RecordingLimits())
        val minSilence = prefs[MIN_SILENCE_MILLIS]
            ?.takeIf { it in RecordingSettings.MIN_SILENCE_RANGE }
            ?: RecordingSettings.DEFAULT_MIN_SILENCE_MILLIS
        val silenceMargin = prefs[SILENCE_MARGIN_MILLIS]
            ?.takeIf { it in RecordingSettings.SILENCE_MARGIN_RANGE }
            ?: RecordingSettings.DEFAULT_SILENCE_MARGIN_MILLIS
        RecordingSettings(
            recordingEnabled = enabled,
            defaultEditor = defaultEditor,
            limits = validatedLimits,
            minSilenceMillis = minSilence,
            silenceMarginMillis = silenceMargin,
        )
    }

    val settings: Flow<RecordingSettings> = dataStore.data.map { readSettings(it) }

    val cachedSettings: RecordingSettings get() = dataStore.cached(readSettings) ?: RecordingSettings()

    suspend fun setRecordingEnabled(enabled: Boolean) {
        dataStore.edit { it[RECORDING_ENABLED] = enabled }
    }

    suspend fun setDefaultEditor(defaultEditor: Boolean) {
        dataStore.edit { it[DEFAULT_EDITOR] = defaultEditor }
    }

    suspend fun setLimits(limits: RecordingLimits) {
        dataStore.edit {
            it[HISTORY_MILLIS] = limits.historyMillis
            it[WAITING_MILLIS] = limits.waitingMillis
            it[EDITOR_MILLIS] = limits.editorMillis
        }
    }

    suspend fun setMinSilenceMillis(millis: Long) {
        require(millis in RecordingSettings.MIN_SILENCE_RANGE)
        dataStore.edit { it[MIN_SILENCE_MILLIS] = millis }
    }

    suspend fun setSilenceMarginMillis(millis: Long) {
        require(millis in RecordingSettings.SILENCE_MARGIN_RANGE)
        dataStore.edit { it[SILENCE_MARGIN_MILLIS] = millis }
    }

    companion object {
        val RECORDING_ENABLED = booleanPreferencesKey("sentence_recording_enabled")
        val DEFAULT_EDITOR = booleanPreferencesKey("sentence_audio_default_editor")
        val HISTORY_MILLIS = longPreferencesKey("sentence_audio_history_millis")
        val WAITING_MILLIS = longPreferencesKey("sentence_audio_waiting_millis")
        val EDITOR_MILLIS = longPreferencesKey("sentence_audio_editor_millis")
        val MIN_SILENCE_MILLIS = longPreferencesKey("sentence_audio_min_silence_millis")
        val SILENCE_MARGIN_MILLIS = longPreferencesKey("sentence_audio_silence_margin_millis")
    }
}
