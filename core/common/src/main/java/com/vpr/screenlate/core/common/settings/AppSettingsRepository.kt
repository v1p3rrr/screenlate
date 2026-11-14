package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.theme.AppPaletteConfig
import com.vpr.screenlate.core.common.theme.ColorPreset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-wide user preferences backed by DataStore. */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val readThemeMode: (Preferences) -> ThemeMode = { prefs ->
        prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { readThemeMode(it) }

    /** [themeMode] as last read, for a screen's first frame; null before the first read. */
    val cachedThemeMode: ThemeMode? get() = dataStore.cached(readThemeMode)

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    private val readPaletteConfig: (Preferences) -> AppPaletteConfig = { prefs ->
        val mode = prefs[APP_COLOR_MODE]
        when (mode) {
            "SYSTEM" -> AppPaletteConfig(useSystemWallpaper = true)
            "CUSTOM" -> {
                val custom = prefs[APP_COLOR_CUSTOM]
                if (custom != null) {
                    AppPaletteConfig(useSystemWallpaper = false, preset = null, customSeedColor = custom)
                } else {
                    AppPaletteConfig(useSystemWallpaper = false, preset = ColorPreset.VIOLET)
                }
            }
            "PRESET" -> {
                val preset = ColorPreset.fromId(prefs[APP_COLOR_PRESET]) ?: ColorPreset.VIOLET
                AppPaletteConfig(useSystemWallpaper = false, preset = preset)
            }
            else -> {
                val legacy = prefs[APP_COLORS]?.let { stored -> AppColors.entries.firstOrNull { it.name == stored } }
                if (legacy == AppColors.SYSTEM) {
                    AppPaletteConfig(useSystemWallpaper = true)
                } else {
                    AppPaletteConfig(useSystemWallpaper = false, preset = ColorPreset.VIOLET)
                }
            }
        }
    }

    val paletteConfig: Flow<AppPaletteConfig> = dataStore.data.map { readPaletteConfig(it) }

    /** [paletteConfig] as last read, for a screen's first frame; null before the first read. */
    val cachedPaletteConfig: AppPaletteConfig? get() = dataStore.cached(readPaletteConfig)

    suspend fun setPaletteConfig(config: AppPaletteConfig) {
        dataStore.edit { prefs ->
            when {
                config.useSystemWallpaper -> {
                    prefs[APP_COLOR_MODE] = "SYSTEM"
                    prefs.remove(APP_COLOR_PRESET)
                    prefs.remove(APP_COLOR_CUSTOM)
                    prefs[APP_COLORS] = AppColors.SYSTEM.name
                }
                config.customSeedColor != null -> {
                    prefs[APP_COLOR_MODE] = "CUSTOM"
                    prefs[APP_COLOR_CUSTOM] = config.customSeedColor
                    prefs.remove(APP_COLOR_PRESET)
                    prefs[APP_COLORS] = AppColors.SCREENLATE.name
                }
                else -> {
                    prefs[APP_COLOR_MODE] = "PRESET"
                    prefs[APP_COLOR_PRESET] = (config.preset ?: ColorPreset.VIOLET).id
                    prefs.remove(APP_COLOR_CUSTOM)
                    prefs[APP_COLORS] = AppColors.SCREENLATE.name
                }
            }
        }
    }

    private val readAppColors: (Preferences) -> AppColors = { prefs ->
        if (readPaletteConfig(prefs).useSystemWallpaper) AppColors.SYSTEM else AppColors.SCREENLATE
    }

    val appColors: Flow<AppColors> = paletteConfig.map { if (it.useSystemWallpaper) AppColors.SYSTEM else AppColors.SCREENLATE }

    /** [appColors] as last read, for a screen's first frame; null before the first read. */
    val cachedAppColors: AppColors? get() = cachedPaletteConfig?.let { if (it.useSystemWallpaper) AppColors.SYSTEM else AppColors.SCREENLATE }

    suspend fun setAppColors(colors: AppColors) {
        setPaletteConfig(
            when (colors) {
                AppColors.SYSTEM -> AppPaletteConfig(useSystemWallpaper = true)
                AppColors.SCREENLATE -> AppPaletteConfig(useSystemWallpaper = false, preset = ColorPreset.VIOLET)
            }
        )
    }

    /**
     * E-ink mode: a black-and-white theme without animations or translucent fills, for screens that refresh slowly and
     * show few shades. It overrides the theme mode.
     */
    val eInk: Flow<Boolean> = dataStore.data.map { it[E_INK] ?: false }

    /** [eInk] as last read, for a screen's first frame; null before the first read. */
    val cachedEInk: Boolean? get() = dataStore.cached { it[E_INK] ?: false }

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

    /** What "Make larger" changed, kept until e-ink mode is turned off; not part of backups. */
    val eInkEnlargement: Flow<EInkEnlargement?> = dataStore.data.map { prefs ->
        fun change(before: Preferences.Key<Int>, after: Preferences.Key<Int>) =
            prefs[before]?.let { b -> prefs[after]?.let { a -> EInkEnlargement.Change(b, a) } }
        val bubble = change(E_INK_BUBBLE_BEFORE, E_INK_BUBBLE_AFTER)
        val font = change(E_INK_FONT_BEFORE, E_INK_FONT_AFTER)
        if (bubble == null && font == null) null else EInkEnlargement(bubble, font)
    }

    /** Stores [enlargement], or clears it when null. */
    suspend fun setEInkEnlargement(enlargement: EInkEnlargement?) {
        dataStore.edit { prefs ->
            fun put(change: EInkEnlargement.Change?, before: Preferences.Key<Int>, after: Preferences.Key<Int>) {
                if (change == null) {
                    prefs.remove(before)
                    prefs.remove(after)
                } else {
                    prefs[before] = change.before
                    prefs[after] = change.after
                }
            }
            put(enlargement?.bubble, E_INK_BUBBLE_BEFORE, E_INK_BUBBLE_AFTER)
            put(enlargement?.font, E_INK_FONT_BEFORE, E_INK_FONT_AFTER)
        }
    }

    /** Whether the notification permission was asked for when an import or download started (asked once). */
    val notificationPermissionAsked: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ASKED] ?: false }

    suspend fun setNotificationPermissionAsked() {
        dataStore.edit { it[NOTIFICATIONS_ASKED] = true }
    }

    /** Whether the user has seen the background work tip, which is badged in the settings until then. */
    val backgroundTipSeen: Flow<Boolean> = dataStore.data.map { it[BACKGROUND_TIP_SEEN] ?: false }

    /** [backgroundTipSeen] as last read, for a screen's first frame; null before the first read. */
    val cachedBackgroundTipSeen: Boolean? get() = dataStore.cached { it[BACKGROUND_TIP_SEEN] ?: false }

    suspend fun setBackgroundTipSeen() {
        dataStore.edit { it[BACKGROUND_TIP_SEEN] = true }
    }

    /**
     * Whether the user agreed to what the accessibility service reads and sends, in the dialog shown before its
     * settings open. Not part of backups: a new device asks again.
     */
    val accessibilityAgreed: Flow<Boolean> = dataStore.data.map { it[ACCESSIBILITY_AGREED] ?: false }

    /** [accessibilityAgreed] as last read, for a screen's first frame; null before the first read. */
    val cachedAccessibilityAgreed: Boolean? get() = dataStore.cached { it[ACCESSIBILITY_AGREED] ?: false }

    suspend fun setAccessibilityAgreed() {
        dataStore.edit { it[ACCESSIBILITY_AGREED] = true }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_COLORS = stringPreferencesKey("theme_colors")
        val APP_COLOR_MODE = stringPreferencesKey("theme_color_mode")
        val APP_COLOR_PRESET = stringPreferencesKey("theme_color_preset")
        val APP_COLOR_CUSTOM = intPreferencesKey("theme_color_custom")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notification_permission_asked")
        val BACKGROUND_TIP_SEEN = booleanPreferencesKey("background_tip_seen")
        val ACCESSIBILITY_AGREED = booleanPreferencesKey("accessibility_agreed")
        val E_INK = booleanPreferencesKey("e_ink")
        val E_INK_HINT_SEEN = booleanPreferencesKey("e_ink_hint_seen")
        val E_INK_BUBBLE_BEFORE = intPreferencesKey("e_ink_bubble_before")
        val E_INK_BUBBLE_AFTER = intPreferencesKey("e_ink_bubble_after")
        val E_INK_FONT_BEFORE = intPreferencesKey("e_ink_font_before")
        val E_INK_FONT_AFTER = intPreferencesKey("e_ink_font_after")
    }
}
