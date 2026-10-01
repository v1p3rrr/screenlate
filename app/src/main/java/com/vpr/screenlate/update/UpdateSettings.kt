package com.vpr.screenlate.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.settings.cached
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * @property announce new versions are looked for when the app opens and announced on the home screen.
 * @property lastCheck time of the last automatic check, in milliseconds.
 * @property announcedTag the release already announced; each version is announced once.
 */
data class UpdateSettings(
    val announce: Boolean = true,
    val lastCheck: Long = 0,
    val announcedTag: String? = null,
)

@Singleton
class UpdateSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val readUpdateSettings: (Preferences) -> UpdateSettings = { prefs ->
        UpdateSettings(
            announce = prefs[ANNOUNCE] ?: true,
            lastCheck = prefs[LAST_CHECK] ?: 0,
            announcedTag = prefs[ANNOUNCED],
        )
    }

    val settings: Flow<UpdateSettings> = dataStore.data.map { readUpdateSettings(it) }

    /** The settings as last read, for a screen's first frame; null before the first read. */
    val cachedSettings: UpdateSettings? get() = dataStore.cached(readUpdateSettings)

    suspend fun current(): UpdateSettings = settings.first()

    suspend fun setAnnounce(enabled: Boolean) {
        dataStore.edit { it[ANNOUNCE] = enabled }
    }

    suspend fun setLastCheck(time: Long) {
        dataStore.edit { it[LAST_CHECK] = time }
    }

    suspend fun setAnnounced(tag: String) {
        dataStore.edit { it[ANNOUNCED] = tag }
    }

    private companion object {
        val ANNOUNCE = booleanPreferencesKey("update_announce")
        val LAST_CHECK = longPreferencesKey("update_last_check")
        val ANNOUNCED = stringPreferencesKey("update_announced_tag")
    }
}
