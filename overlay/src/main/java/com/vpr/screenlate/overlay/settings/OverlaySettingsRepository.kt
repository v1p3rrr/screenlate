package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DockSide { LEFT, RIGHT }

enum class AimMode {
    /** Aim point floats above the finger so the finger does not cover the text. */
    ABOVE_FINGER,

    /** Aim point is the bubble center; useful near the bottom edge of the screen. */
    BUBBLE_CENTER,
}

/**
 * @property dockY vertical position of the docked bubble as a fraction of the screen height.
 * @property hiddenPackages apps in which the bubble is hidden.
 */
data class OverlaySettings(
    val bubbleVisible: Boolean = true,
    val dockSide: DockSide = DockSide.RIGHT,
    val dockY: Float = 0.45f,
    val aimMode: AimMode = AimMode.ABOVE_FINGER,
    val highlightWord: Boolean = true,
    val haptics: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
)

@Singleton
class OverlaySettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<OverlaySettings> = dataStore.data.map { prefs ->
        val defaults = OverlaySettings()
        OverlaySettings(
            bubbleVisible = prefs[BUBBLE_VISIBLE] ?: defaults.bubbleVisible,
            dockSide = prefs[DOCK_SIDE]?.let { stored -> DockSide.entries.firstOrNull { it.name == stored } }
                ?: defaults.dockSide,
            dockY = prefs[DOCK_Y] ?: defaults.dockY,
            aimMode = prefs[AIM_MODE]?.let { stored -> AimMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.aimMode,
            highlightWord = prefs[HIGHLIGHT_WORD] ?: defaults.highlightWord,
            haptics = prefs[HAPTICS] ?: defaults.haptics,
            hiddenPackages = prefs[HIDDEN_PACKAGES] ?: defaults.hiddenPackages,
        )
    }

    suspend fun setBubbleVisible(visible: Boolean) {
        dataStore.edit { it[BUBBLE_VISIBLE] = visible }
    }

    suspend fun setDock(side: DockSide, y: Float) {
        dataStore.edit {
            it[DOCK_SIDE] = side.name
            it[DOCK_Y] = y.coerceIn(0f, 1f)
        }
    }

    suspend fun setAimMode(mode: AimMode) {
        dataStore.edit { it[AIM_MODE] = mode.name }
    }

    suspend fun setHighlightWord(enabled: Boolean) {
        dataStore.edit { it[HIGHLIGHT_WORD] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        dataStore.edit { it[HAPTICS] = enabled }
    }

    suspend fun setDockSide(side: DockSide) {
        dataStore.edit { it[DOCK_SIDE] = side.name }
    }

    suspend fun setHidden(packageName: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[HIDDEN_PACKAGES].orEmpty()
            prefs[HIDDEN_PACKAGES] = if (hidden) current + packageName else current - packageName
        }
    }

    private companion object {
        val BUBBLE_VISIBLE = booleanPreferencesKey("overlay_bubble_visible")
        val DOCK_SIDE = stringPreferencesKey("overlay_dock_side")
        val DOCK_Y = floatPreferencesKey("overlay_dock_y")
        val AIM_MODE = stringPreferencesKey("overlay_aim_mode")
        val HIGHLIGHT_WORD = booleanPreferencesKey("overlay_highlight_word")
        val HAPTICS = booleanPreferencesKey("overlay_haptics")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("overlay_hidden_packages")
    }
}
