package com.vpr.screenlate.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.preferenceKey
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.overlay.BubbleKeepAliveService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Settings pages with their own reset. */
enum class SettingsSection { BUBBLE, LOOKUP, ANKI, POPUP, TRANSLATION, APPEARANCE, BACKGROUND }

/**
 * Returns settings to their defaults by removing the stored values. The dictionaries, the bundled dictionary
 * bookkeeping, migrations, the turned-on languages and the interface language (kept by Android, not here) are never
 * touched.
 */
@Singleton
class SettingsReset @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val eInkSizes: EInkSizes,
    private val audioSettings: AudioSettingsRepository,
) {
    /** Every section of every language, update announcements and the one-time hints. */
    suspend fun resetAll() = reset(SettingsSection.entries.toSet(), everything = true, language = null)

    /** The page's shared settings and [language]'s own; other languages keep theirs. */
    suspend fun reset(section: SettingsSection, language: Language? = null) =
        reset(setOf(section), everything = false, language = language)

    private suspend fun reset(sections: Set<SettingsSection>, everything: Boolean, language: Language?) {
        // E-ink mode goes off the way its switch turns it off: the sizes it made larger come back.
        if (SettingsSection.APPEARANCE in sections) eInkSizes.restore()
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { SettingsKeys.resets(it.name, sections, everything, language) }.forEach { prefs.remove(it) }
        }
        // The shared audio settings are stored with Japanese's sources, which stay when another language is reset.
        if (SettingsSection.ANKI in sections && language != null && language != Language.JAPANESE) audioSettings.resetShared()
        // As the switch does: the running service stops it too, but the system may have restarted the notification
        // without the service.
        if (SettingsSection.BACKGROUND in sections) BubbleKeepAliveService.keepAlive(context, false)
    }
}

/** Whether the popup starts with the recognized text: set on the Popup page, kept under its old bubble key. */
const val SHOW_SOURCE_TEXT_KEY = "overlay_show_source_text"

/** Which reset clears a stored preference key. */
object SettingsKeys {
    /** Kept in the settings file but changed only on the About page or once by the app itself. */
    private val globalOnly = setOf(
        "update_announce",
        "update_announced_tag",
        "e_ink_hint_seen",
        "background_tip_seen",
        "notification_permission_asked",
        "accessibility_agreed",
    )

    /**
     * Names of the keys whose value belongs to a language: Japanese's key carries the name alone, another language's
     * adds its code (see [preferenceKey]).
     */
    private val perLanguage = setOf(
        "lookup_scan_length",
        "lookup_single_kanji",
        "popup_font",
        "popup_font_all_text",
        "popup_custom_css",
        "anki_settings",
        "audio_settings",
        "audio_sources_chosen",
        "sort_dictionary_id",
    )

    /** The language a key's value belongs to; null for a value every language shares. */
    fun languageOf(key: String): Language? =
        perLanguage.firstNotNullOfOrNull { name -> Language.entries.firstOrNull { it.preferenceKey(name) == key } }

    /** The page a key belongs to; null for keys no page resets. */
    fun sectionOf(key: String): SettingsSection? = when {
        key == "overlay_keep_alive" -> SettingsSection.BACKGROUND
        key == SHOW_SOURCE_TEXT_KEY -> SettingsSection.POPUP
        key.startsWith("overlay_") -> SettingsSection.BUBBLE
        key.startsWith("lookup_") -> SettingsSection.LOOKUP
        languageOf(key) != null && key.startsWith("anki_settings") || key.startsWith("audio_") -> SettingsSection.ANKI
        key.startsWith("popup_") -> SettingsSection.POPUP
        key.startsWith(TranslationSettingsRepository.KEY_PREFIX) -> SettingsSection.TRANSLATION
        key == "theme_mode" || key == "theme_colors" || key == "e_ink" -> SettingsSection.APPEARANCE
        // What e-ink's "Make larger" changed goes with the size it belongs to, so turning e-ink off later does not
        // change a size that was reset.
        key.startsWith("e_ink_bubble_") -> SettingsSection.BUBBLE
        key.startsWith("e_ink_font_") -> SettingsSection.POPUP
        else -> null
    }

    /** Whether a reset of [sections] clears [key]; with a [language], other languages' values stay. */
    fun resets(key: String, sections: Set<SettingsSection>, everything: Boolean, language: Language? = null): Boolean {
        if (everything && key in globalOnly) return true
        if (sectionOf(key) !in sections) return false
        val owner = languageOf(key)
        return language == null || owner == null || owner == language
    }
}
