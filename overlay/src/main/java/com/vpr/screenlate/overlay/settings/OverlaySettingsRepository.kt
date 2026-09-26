package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

/** Where the text under the bubble comes from. */
enum class TextSource {
    /** Text recognition on a screenshot. */
    SCREEN,

    /** The app's own text when it exposes character positions, text recognition otherwise. */
    APP_TEXT,
}

/** Extra Lens requests for small text, which Lens skips in a full-screen image but reads in a crop. */
enum class SmallTextMode {
    OFF,

    /** A band of the screen is recognized again when the aim rests where nothing was found. */
    ON_DEMAND,

    /** All bands are recognized with every scan. */
    ALWAYS,
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
    val textSource: TextSource = TextSource.SCREEN,
    val bubbleSizeDp: Int = DEFAULT_BUBBLE_DP,
    val smallText: SmallTextMode = SmallTextMode.OFF,
) {
    companion object {
        const val DEFAULT_BUBBLE_DP = 48
        const val MIN_BUBBLE_DP = 36
        const val MAX_BUBBLE_DP = 64
    }
}

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
            textSource = prefs[TEXT_SOURCE]?.let { stored -> TextSource.entries.firstOrNull { it.name == stored } }
                ?: defaults.textSource,
            bubbleSizeDp = (prefs[BUBBLE_SIZE] ?: defaults.bubbleSizeDp)
                .coerceIn(OverlaySettings.MIN_BUBBLE_DP, OverlaySettings.MAX_BUBBLE_DP),
            smallText = prefs[SMALL_TEXT]?.let { stored -> SmallTextMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.smallText,
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

    suspend fun setTextSource(source: TextSource) {
        dataStore.edit { it[TEXT_SOURCE] = source.name }
    }

    suspend fun setBubbleSize(dp: Int) {
        dataStore.edit { it[BUBBLE_SIZE] = dp.coerceIn(OverlaySettings.MIN_BUBBLE_DP, OverlaySettings.MAX_BUBBLE_DP) }
    }

    suspend fun setSmallText(mode: SmallTextMode) {
        dataStore.edit { it[SMALL_TEXT] = mode.name }
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
        val TEXT_SOURCE = stringPreferencesKey("overlay_text_source")
        val BUBBLE_SIZE = intPreferencesKey("overlay_bubble_size_dp")
        val SMALL_TEXT = stringPreferencesKey("overlay_small_text")
    }
}
