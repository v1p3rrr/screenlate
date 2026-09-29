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

    /**
     * E-ink mode: a black-and-white theme without animations or translucent fills, for screens that refresh slowly and
     * show few shades. It overrides the theme mode.
     */
    val eInk: Flow<Boolean> = dataStore.data.map { it[E_INK] ?: false }

    suspend fun setEInk(enabled: Boolean) {
        dataStore.edit {
            it[E_INK] = enabled
            it[E_INK_HINT_SEEN] = true
        }
    }

    /** Whether the home screen has offered e-ink mode on a device that looks like an e-ink reader (offered once). */
    val eInkHintSeen: Flow<Boolean> = dataStore.data.map { it[E_INK_HINT_SEEN] ?: false }

    suspend fun setEInkHintSeen() {
        dataStore.edit { it[E_INK_HINT_SEEN] = true }
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
        val E_INK = booleanPreferencesKey("e_ink")
        val E_INK_HINT_SEEN = booleanPreferencesKey("e_ink_hint_seen")
    }
}
