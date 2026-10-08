package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vpr.screenlate.core.common.settings.cached
import com.vpr.screenlate.core.ocr.OcrEngines
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DockSide {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    ;

    /** At the top or bottom edge, where the dock moves along the screen's width. */
    val horizontal: Boolean get() = this == TOP || this == BOTTOM

    companion object {
        /**
         * Where a top or bottom dock goes when those edges are not allowed: the nearer side, at its end next to the old
         * edge. [position] is along the old edge; a side dock comes back unchanged.
         */
        fun sideFor(side: DockSide, position: Float): Pair<DockSide, Float> {
            if (!side.horizontal) return side to position
            val newSide = if (position < 0.5f) LEFT else RIGHT
            return newSide to if (side == TOP) 0f else 1f
        }
    }
}

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

    /** Only the app's own text: no screenshot and no recognition, for weak devices and e-ink readers. */
    APP_TEXT_ONLY,
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
 * @property dockPosition where the docked bubble sits along its edge: a fraction of the screen height at the sides, of
 *   its width at the top and bottom.
 * @property dockTopBottom the bubble may also dock at the top and bottom edges.
 * @property hideAfterAdd the popup closes once ➕ has added the shown word to Anki.
 * @property hideOffWord the popup closes when the moving aim leaves the word for a place without text.
 * @property hiddenPackages apps in which the bubble is hidden.
 * @property showSourceText the popup starts with the recognized text; off, it starts with the first entry.
 * @property ocrSaving the device reads the whole screen only when cloud recognition is late or fails.
 * @property keepAlive the service holds a foreground notification so the phone stops the app less often.
 */
data class OverlaySettings(
    val bubbleVisible: Boolean = true,
    val dockSide: DockSide = DockSide.RIGHT,
    val dockPosition: Float = 0.45f,
    val dockTopBottom: Boolean = false,
    val aimMode: AimMode = AimMode.ABOVE_FINGER,
    val highlightWord: Boolean = true,
    val haptics: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
    val textSource: TextSource = TextSource.SCREEN,
    val bubbleSizeDp: Int = DEFAULT_BUBBLE_DP,
    val smallText: SmallTextMode = SmallTextMode.OFF,
    val showSourceText: Boolean = true,
    val ocrEngines: OcrEngines = OcrEngines.BOTH,
    val ocrSaving: Boolean = false,
    val keepAlive: Boolean = false,
    val hideAfterAdd: Boolean = false,
    val hideOffWord: Boolean = false,
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
    private val readOverlaySettings: (Preferences) -> OverlaySettings = { prefs ->
        val defaults = OverlaySettings()
        val dockTopBottom = prefs[DOCK_TOP_BOTTOM] ?: defaults.dockTopBottom
        val storedSide = prefs[DOCK_SIDE]?.let { stored -> DockSide.entries.firstOrNull { it.name == stored } }
            ?: defaults.dockSide
        // A restored backup may hold a top dock without the switch that allows it.
        val (dockSide, dockPosition) = (prefs[DOCK_POSITION] ?: defaults.dockPosition).let { position ->
            if (dockTopBottom) storedSide to position else DockSide.sideFor(storedSide, position)
        }
        OverlaySettings(
            bubbleVisible = prefs[BUBBLE_VISIBLE] ?: defaults.bubbleVisible,
            dockSide = dockSide,
            dockPosition = dockPosition,
            dockTopBottom = dockTopBottom,
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
            showSourceText = prefs[SHOW_SOURCE_TEXT] ?: defaults.showSourceText,
            ocrEngines = prefs[OCR_ENGINES]?.let { stored -> OcrEngines.entries.firstOrNull { it.name == stored } }
                ?: defaults.ocrEngines,
            ocrSaving = prefs[OCR_SAVING] ?: defaults.ocrSaving,
            keepAlive = prefs[KEEP_ALIVE] ?: defaults.keepAlive,
            hideAfterAdd = prefs[HIDE_AFTER_ADD] ?: defaults.hideAfterAdd,
            hideOffWord = prefs[HIDE_OFF_WORD] ?: defaults.hideOffWord,
        )
    }

    val settings: Flow<OverlaySettings> = dataStore.data.map { readOverlaySettings(it) }

    /** The settings as last read, for a screen's first frame; null before the first read. */
    val cachedSettings: OverlaySettings? get() = dataStore.cached(readOverlaySettings)

    suspend fun setBubbleVisible(visible: Boolean) {
        dataStore.edit { it[BUBBLE_VISIBLE] = visible }
    }

    suspend fun setDock(side: DockSide, position: Float) {
        dataStore.edit {
            it[DOCK_SIDE] = side.name
            it[DOCK_POSITION] = position.coerceIn(0f, 1f)
        }
    }

    /** Turned off, a top or bottom dock moves to the nearer side. */
    suspend fun setDockTopBottom(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DOCK_TOP_BOTTOM] = enabled
            if (enabled) return@edit
            val side = prefs[DOCK_SIDE]?.let { stored -> DockSide.entries.firstOrNull { it.name == stored } } ?: return@edit
            val (newSide, position) = DockSide.sideFor(side, prefs[DOCK_POSITION] ?: OverlaySettings().dockPosition)
            prefs[DOCK_SIDE] = newSide.name
            prefs[DOCK_POSITION] = position
        }
    }

    suspend fun setHideAfterAdd(enabled: Boolean) {
        dataStore.edit { it[HIDE_AFTER_ADD] = enabled }
    }

    suspend fun setHideOffWord(enabled: Boolean) {
        dataStore.edit { it[HIDE_OFF_WORD] = enabled }
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

    suspend fun setShowSourceText(enabled: Boolean) {
        dataStore.edit { it[SHOW_SOURCE_TEXT] = enabled }
    }

    suspend fun setOcrEngines(engines: OcrEngines) {
        dataStore.edit { it[OCR_ENGINES] = engines.name }
    }

    suspend fun setOcrSaving(enabled: Boolean) {
        dataStore.edit { it[OCR_SAVING] = enabled }
    }

    suspend fun setKeepAlive(enabled: Boolean) {
        dataStore.edit { it[KEEP_ALIVE] = enabled }
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
        // The key keeps its first name, from when the bubble docked only at the sides.
        val DOCK_POSITION = floatPreferencesKey("overlay_dock_y")
        val DOCK_TOP_BOTTOM = booleanPreferencesKey("overlay_dock_top_bottom")
        val AIM_MODE = stringPreferencesKey("overlay_aim_mode")
        val HIGHLIGHT_WORD = booleanPreferencesKey("overlay_highlight_word")
        val HAPTICS = booleanPreferencesKey("overlay_haptics")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("overlay_hidden_packages")
        val TEXT_SOURCE = stringPreferencesKey("overlay_text_source")
        val BUBBLE_SIZE = intPreferencesKey("overlay_bubble_size_dp")
        val SMALL_TEXT = stringPreferencesKey("overlay_small_text")
        val SHOW_SOURCE_TEXT = booleanPreferencesKey("overlay_show_source_text")
        val OCR_ENGINES = stringPreferencesKey("overlay_ocr_engines")
        val OCR_SAVING = booleanPreferencesKey("overlay_ocr_saving")
        val KEEP_ALIVE = booleanPreferencesKey("overlay_keep_alive")
        val HIDE_AFTER_ADD = booleanPreferencesKey("popup_hide_after_add")
        val HIDE_OFF_WORD = booleanPreferencesKey("popup_hide_off_word")
    }
}
