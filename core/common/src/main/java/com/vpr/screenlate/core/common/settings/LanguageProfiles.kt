package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The languages turned on, in the order they were turned on, and the active one, which scans, search and the
 * language-bound settings use.
 */
data class LanguageProfilesState(val turnedOn: List<Language>, val active: Language) {
    /** Whether the user switches between languages, which shows the language chips and blocks. */
    val several: Boolean get() = turnedOn.size > 1

    companion object {
        /** Before languages were added the app was Japanese only, so an upgrade starts with Japanese alone. */
        val DEFAULT = LanguageProfilesState(listOf(Language.JAPANESE), Language.JAPANESE)
    }
}

@Singleton
class LanguageProfiles @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val state: Flow<LanguageProfilesState> = dataStore.data.map(::read).distinctUntilChanged()

    val active: Flow<Language> = state.map { it.active }.distinctUntilChanged()

    /** [state] as last read, for a screen's first frame; null before the first read. */
    val cachedState: LanguageProfilesState? get() = dataStore.cached(::read)

    suspend fun current(): LanguageProfilesState = state.first()

    /** Makes a turned-on [language] the active one; a language that is off is ignored. */
    suspend fun setActive(language: Language) {
        dataStore.edit { prefs ->
            if (language in read(prefs).turnedOn) prefs[ACTIVE] = language.code
        }
    }

    /** Turns [language] on (it goes last) and, with [activate], makes it the active one. */
    suspend fun turnOn(language: Language, activate: Boolean = true) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            val turnedOn = if (language in current.turnedOn) current.turnedOn else current.turnedOn + language
            write(prefs, LanguageProfilesState(turnedOn, if (activate) language else current.active))
        }
    }

    /**
     * Turns [language] off; the active language moves to the first one left. The last language cannot be turned off.
     * Returns whether it was turned off.
     */
    suspend fun turnOff(language: Language): Boolean {
        var done = false
        dataStore.edit { prefs ->
            val current = read(prefs)
            val turnedOn = current.turnedOn - language
            if (turnedOn.isEmpty() || turnedOn.size == current.turnedOn.size) return@edit
            write(prefs, LanguageProfilesState(turnedOn, if (current.active == language) turnedOn.first() else current.active))
            done = true
        }
        return done
    }

    /** Replaces the whole state, e.g. from a backup; languages this version does not know are already left out. */
    suspend fun restore(state: LanguageProfilesState) {
        if (state.turnedOn.isEmpty()) return
        dataStore.edit { write(it, state) }
    }

    private fun write(prefs: MutablePreferences, state: LanguageProfilesState) {
        prefs[TURNED_ON] = state.turnedOn.joinToString(",") { it.code }
        prefs[ACTIVE] = (state.active.takeIf { it in state.turnedOn } ?: state.turnedOn.first()).code
    }

    internal companion object {
        val TURNED_ON = stringPreferencesKey("languages_turned_on")
        val ACTIVE = stringPreferencesKey("language_active")

        /** Codes this version does not know (written by a newer one) are skipped; nothing usable means the default. */
        fun read(prefs: Preferences): LanguageProfilesState {
            val turnedOn = prefs[TURNED_ON]?.split(',')?.mapNotNull { Language.of(it.trim()) }?.distinct().orEmpty()
            if (turnedOn.isEmpty()) return LanguageProfilesState.DEFAULT
            val active = Language.of(prefs[ACTIVE])?.takeIf { it in turnedOn } ?: turnedOn.first()
            return LanguageProfilesState(turnedOn, active)
        }
    }
}

/**
 * Name of a preference key that holds a value per language. Japanese keeps the name it had before languages were
 * added, so its settings need no migration; other languages get their code as a suffix.
 */
fun Language.preferenceKey(name: String): String = if (this == Language.JAPANESE) name else "${name}_$code"
