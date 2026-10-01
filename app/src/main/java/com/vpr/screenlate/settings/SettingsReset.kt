package com.vpr.screenlate.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vpr.screenlate.overlay.BubbleKeepAliveService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Settings pages with their own reset. */
enum class SettingsSection { BUBBLE, LOOKUP, ANKI, POPUP, APPEARANCE, BACKGROUND }

/**
 * Returns settings to their defaults by removing the stored values. The dictionaries, the bundled dictionary
 * bookkeeping, migrations and the interface language (kept by Android, not here) are never touched.
 */
@Singleton
class SettingsReset @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val eInkSizes: EInkSizes,
) {
    /** Every section, update announcements and the one-time hints. */
    suspend fun resetAll() = reset(SettingsSection.entries.toSet(), everything = true)

    suspend fun reset(section: SettingsSection) = reset(setOf(section), everything = false)

    private suspend fun reset(sections: Set<SettingsSection>, everything: Boolean) {
        // E-ink mode goes off the way its switch turns it off: the sizes it made larger come back.
        if (SettingsSection.APPEARANCE in sections) eInkSizes.restore()
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { SettingsKeys.resets(it.name, sections, everything) }.forEach { prefs.remove(it) }
        }
        // As the switch does: the running service stops it too, but the system may have restarted the notification
        // without the service.
        if (SettingsSection.BACKGROUND in sections) BubbleKeepAliveService.keepAlive(context, false)
    }
}

/** Which reset clears a stored preference key. */
object SettingsKeys {
    /** Kept in the settings file but changed only on the About page or once by the app itself. */
    private val globalOnly = setOf(
        "update_announce",
        "update_announced_tag",
        "e_ink_hint_seen",
        "background_tip_seen",
        "notification_permission_asked",
    )

    /** The page a key belongs to; null for keys no page resets. */
    fun sectionOf(key: String): SettingsSection? = when {
        key == "overlay_keep_alive" -> SettingsSection.BACKGROUND
        key.startsWith("overlay_") -> SettingsSection.BUBBLE
        key.startsWith("lookup_") -> SettingsSection.LOOKUP
        key == "anki_settings" || key.startsWith("audio_") -> SettingsSection.ANKI
        key.startsWith("popup_") -> SettingsSection.POPUP
        key == "theme_mode" || key == "e_ink" -> SettingsSection.APPEARANCE
        // What e-ink's "Make larger" changed goes with the size it belongs to, so turning e-ink off later does not
        // change a size that was reset.
        key.startsWith("e_ink_bubble_") -> SettingsSection.BUBBLE
        key.startsWith("e_ink_font_") -> SettingsSection.POPUP
        else -> null
    }

    fun resets(key: String, sections: Set<SettingsSection>, everything: Boolean): Boolean =
        sectionOf(key) in sections || (everything && key in globalOnly)
}
