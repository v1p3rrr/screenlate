package com.vpr.screenlate.core.photos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CameraPreferenceStore

/** [active] is the app's active language; [flags] holds the flag country of each of the [enabled] languages. */
data class CameraLanguages(
    val selected: Language,
    val enabled: List<Language>,
    val active: Language = selected,
    val flags: Map<Language, String> = emptyMap(),
)

/** Camera-only preferences, outside general settings resets and backup. The first use remembers the active language. */
@Singleton
class CameraPreferences @Inject constructor(
    @param:CameraPreferenceStore private val store: DataStore<Preferences>,
    private val profiles: LanguageProfiles,
) {
    val languages = combine(store.data, profiles.state) { preferences, profile ->
        val selected = preferences[LANGUAGE]?.let(Language::of)?.takeIf { it in profile.turnedOn } ?: profile.active
        CameraLanguages(selected, profile.turnedOn, profile.active, profile.turnedOn.associateWith(profile::flagCountry))
    }.onEach { selection ->
        store.edit { preferences ->
            if (preferences[LANGUAGE]?.let(Language::of) !in selection.enabled) preferences[LANGUAGE] = selection.selected.code
        }
    }.distinctUntilChanged()

    suspend fun select(language: Language) {
        if (language in profiles.current().turnedOn) store.edit { it[LANGUAGE] = language.code }
    }

    private companion object { val LANGUAGE = stringPreferencesKey("language") }
}
