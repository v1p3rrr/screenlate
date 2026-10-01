package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.settings.cached
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Text of the lookup page (popup and search screen).
 *
 * @property fontId the installed font to use; null for the phone's font for the language.
 * @property fontForAllText whether the installed font applies to all text; otherwise only to the language's script,
 *   and Latin and Cyrillic keep the default font.
 * @property fontSize base text size in CSS pixels.
 * @property textWeight CSS weight of normal text, [NORMAL_WEIGHT] to [MAX_WEIGHT] in steps of 100; bold text stays at
 *   least as heavy. It follows [fontForAllText]: all text when that is on, otherwise only the language's script.
 * @property letterThickness an outline that thickens letters, 0 (none) to [MAX_THICKNESS] steps of [STROKE_PER_STEP];
 *   the same scope as [textWeight].
 * @property customCss the user's CSS, applied after the dictionaries' styles.
 * @property copyDefinitions a copy button next to each dictionary's name in an entry copies its definitions.
 * @property copyMode what that button copies.
 */
data class PopupAppearance(
    val fontId: String? = null,
    val fontForAllText: Boolean = false,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val textWeight: Int = NORMAL_WEIGHT,
    val letterThickness: Int = 0,
    val customCss: String = "",
    val copyDefinitions: Boolean = false,
    val copyMode: DefinitionCopyMode = DefinitionCopyMode.ALL,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 15
        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 28
        const val NORMAL_WEIGHT = 400
        const val MAX_WEIGHT = 700
        const val WEIGHT_STEP = 100
        const val MAX_THICKNESS = 5

        /** Outline width per thickness step, in em. */
        const val STROKE_PER_STEP = 0.01

        /** [weight] as one of the offered steps. */
        fun textWeight(weight: Int): Int =
            ((weight.toFloat() / WEIGHT_STEP).roundToInt() * WEIGHT_STEP).coerceIn(NORMAL_WEIGHT, MAX_WEIGHT)

        fun letterThickness(thickness: Int): Int = thickness.coerceIn(0, MAX_THICKNESS)
    }
}

/** What a dictionary's copy button puts on the clipboard; [id] is the page's name for it (`definition.js`). */
enum class DefinitionCopyMode(val id: String) {
    /** Everything the dictionary shows, formatted, with plain text for apps that paste only text. */
    ALL("all"),

    /** The numbered meanings as plain text, without examples, notes and references where they can be told apart. */
    MEANINGS("meanings"),
    ;

    companion object {
        fun of(id: String?): DefinitionCopyMode = entries.firstOrNull { it.id == id } ?: ALL
    }
}

@Singleton
class PopupAppearanceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val readPopupAppearance: (Preferences) -> PopupAppearance = { prefs ->
        PopupAppearance(
            fontId = prefs[FONT]?.takeIf { it.isNotEmpty() },
            fontForAllText = prefs[FONT_ALL_TEXT] ?: false,
            // A restored backup may hold a size outside this version's range.
            fontSize = (prefs[FONT_SIZE] ?: PopupAppearance.DEFAULT_FONT_SIZE)
                .coerceIn(PopupAppearance.MIN_FONT_SIZE, PopupAppearance.MAX_FONT_SIZE),
            textWeight = PopupAppearance.textWeight(prefs[TEXT_WEIGHT] ?: PopupAppearance.NORMAL_WEIGHT),
            letterThickness = PopupAppearance.letterThickness(prefs[LETTER_THICKNESS] ?: 0),
            customCss = prefs[CUSTOM_CSS].orEmpty(),
            copyDefinitions = prefs[COPY_DEFINITIONS] ?: false,
            copyMode = DefinitionCopyMode.of(prefs[COPY_MODE]),
        )
    }

    val appearance: Flow<PopupAppearance> = dataStore.data.map { readPopupAppearance(it) }

    /** The appearance as last read, for a screen's first frame; null before the first read. */
    val cachedAppearance: PopupAppearance? get() = dataStore.cached(readPopupAppearance)

    suspend fun setCopyDefinitions(enabled: Boolean) {
        dataStore.edit { it[COPY_DEFINITIONS] = enabled }
    }

    suspend fun setCopyMode(mode: DefinitionCopyMode) {
        dataStore.edit { it[COPY_MODE] = mode.id }
    }

    suspend fun setFont(id: String?) {
        dataStore.edit { it[FONT] = id.orEmpty() }
    }

    suspend fun setFontForAllText(enabled: Boolean) {
        dataStore.edit { it[FONT_ALL_TEXT] = enabled }
    }

    suspend fun setFontSize(size: Int) {
        dataStore.edit {
            it[FONT_SIZE] = size.coerceIn(PopupAppearance.MIN_FONT_SIZE, PopupAppearance.MAX_FONT_SIZE)
        }
    }

    suspend fun setTextWeight(weight: Int) {
        dataStore.edit { it[TEXT_WEIGHT] = PopupAppearance.textWeight(weight) }
    }

    suspend fun setLetterThickness(thickness: Int) {
        dataStore.edit { it[LETTER_THICKNESS] = PopupAppearance.letterThickness(thickness) }
    }

    suspend fun setCustomCss(css: String) {
        // Empty is the default: a reset's pending save then leaves no key behind.
        dataStore.edit { if (css.isEmpty()) it.remove(CUSTOM_CSS) else it[CUSTOM_CSS] = css }
    }

    private companion object {
        val FONT = stringPreferencesKey("popup_font")
        val FONT_ALL_TEXT = booleanPreferencesKey("popup_font_all_text")
        val FONT_SIZE = intPreferencesKey("popup_font_size")
        val TEXT_WEIGHT = intPreferencesKey("popup_text_weight")
        val LETTER_THICKNESS = intPreferencesKey("popup_letter_thickness")
        val CUSTOM_CSS = stringPreferencesKey("popup_custom_css")
        val COPY_DEFINITIONS = booleanPreferencesKey("popup_copy_definitions")
        val COPY_MODE = stringPreferencesKey("popup_copy_mode")
    }
}
