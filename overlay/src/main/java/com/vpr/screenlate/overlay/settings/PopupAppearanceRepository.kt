package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Text of the lookup page (popup and search screen).
 *
 * @property fontId the installed font to use; null for the phone's font for the language.
 * @property fontForAllText whether the installed font applies to all text; otherwise only to the language's script,
 *   and Latin and Cyrillic keep the default font.
 * @property fontSize base text size in CSS pixels.
 * @property customCss the user's CSS, applied after the dictionaries' styles.
 */
data class PopupAppearance(
    val fontId: String? = null,
    val fontForAllText: Boolean = false,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val customCss: String = "",
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 15
        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 28
    }
}

@Singleton
class PopupAppearanceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val appearance: Flow<PopupAppearance> = dataStore.data.map { prefs ->
        PopupAppearance(
            fontId = prefs[FONT]?.takeIf { it.isNotEmpty() },
            fontForAllText = prefs[FONT_ALL_TEXT] ?: false,
            fontSize = prefs[FONT_SIZE] ?: PopupAppearance.DEFAULT_FONT_SIZE,
            customCss = prefs[CUSTOM_CSS].orEmpty(),
        )
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

    suspend fun setCustomCss(css: String) {
        dataStore.edit { it[CUSTOM_CSS] = css }
    }

    private companion object {
        val FONT = stringPreferencesKey("popup_font")
        val FONT_ALL_TEXT = booleanPreferencesKey("popup_font_all_text")
        val FONT_SIZE = intPreferencesKey("popup_font_size")
        val CUSTOM_CSS = stringPreferencesKey("popup_custom_css")
    }
}
