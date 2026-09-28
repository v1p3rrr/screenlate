package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-wide user preferences backed by DataStore. */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /** Whether the notification permission was asked for when an import or download started (asked once). */
    val notificationPermissionAsked: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ASKED] ?: false }

    suspend fun setNotificationPermissionAsked() {
        dataStore.edit { it[NOTIFICATIONS_ASKED] = true }
    }

    /** Whether the user has seen the background work tip, which is badged in the settings until then. */
    val backgroundTipSeen: Flow<Boolean> = dataStore.data.map { it[BACKGROUND_TIP_SEEN] ?: false }

    suspend fun setBackgroundTipSeen() {
        dataStore.edit { it[BACKGROUND_TIP_SEEN] = true }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notification_permission_asked")
        val BACKGROUND_TIP_SEEN = booleanPreferencesKey("background_tip_seen")
    }
}
