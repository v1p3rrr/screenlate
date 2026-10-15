package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The languages turned on, in the order they were turned on, and the active one, which scans, search and the
 * language-bound settings use.
 *
 * @property starred turned-on languages besides the active one that are switched to from the bubble menu and the home
 * screen, at most [MAX_STARRED], in the order of [turnedOn].
 * @property flags the country whose flag stands for a language, where the user chose one; see [flagCountry].
 */
data class LanguageProfilesState(
    val turnedOn: List<Language>,
    val active: Language,
    val starred: List<Language> = emptyList(),
    val flags: Map<Language, String> = emptyMap(),
) {
    /** Whether the user switches between languages, which shows the language chips and blocks. */
    val several: Boolean get() = turnedOn.size > 1

    /** The languages to switch between quickly: the active one and the [starred] ones, in the order of [turnedOn]. */
    val quick: List<Language> get() = turnedOn.filter { it == active || it in starred }

    /** Whether another language can get a star. */
    val canStar: Boolean get() = starred.size < MAX_STARRED

    /** The country of [language]'s flag: the chosen one, or the language's default. */
    fun flagCountry(language: Language): String {
        val countries = language.support.flagCountries
        return flags[language]?.takeIf { it in countries } ?: countries.first()
    }

    /**
     * The state after [language] becomes active. A starred language swaps roles with the active one, which keeps a
     * star; any other language takes the active one's place, and the language that was active is left without a star.
     */
    internal fun activated(language: Language): LanguageProfilesState {
        if (language == active || language !in turnedOn) return this
        val stars = if (language in starred) starred - language + active else starred
        return copy(active = language, starred = turnedOn.filter { it in stars })
    }

    companion object {
        /** Before languages were added the app was Japanese only, so an upgrade starts with Japanese alone. */
        val DEFAULT = LanguageProfilesState(listOf(Language.JAPANESE), Language.JAPANESE)

        /** Starred languages besides the active one: three quick languages in all. */
        const val MAX_STARRED = 2
    }
}

@Singleton
class LanguageProfiles @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val state: Flow<LanguageProfilesState> = dataStore.data.map(::read).distinctUntilChanged()

    val active: Flow<Language> = state.map { it.active }.distinctUntilChanged()

    /** [state] as last read, for a screen's first frame; null before the first read. */
    val cachedState: LanguageProfilesState? get() = dataStore.cached(::read)

    suspend fun current(): LanguageProfilesState = state.first()

    /** Whether the app still has to ask which language the user learns: a first run not answered yet. */
    val firstRunPending: Flow<Boolean> = dataStore.data.map { it[FIRST_RUN] == true }.distinctUntilChanged()

    private val settled = CompletableDeferred<Unit>()

    /**
     * Decides once whether this is a first run, before anything else writes the settings. Settings without the language
     * state come from a version before languages or from a fresh install: any settings or [hasDictionaries] mean an
     * upgrade, which keeps Japanese alone; neither means a first run, which asks ([firstRunPending]). [onUpgrade] changes
     * other settings of an upgrade in the same write, such as a default that changed for new installs only.
     */
    suspend fun settleFirstRun(onUpgrade: (MutablePreferences) -> Unit = {}, hasDictionaries: suspend () -> Boolean) {
        try {
            val prefs = dataStore.data.first()
            if (prefs[TURNED_ON] != null || prefs[FIRST_RUN] != null) return
            val upgrade = prefs.asMap().isNotEmpty() || hasDictionaries()
            dataStore.edit { edited ->
                if (edited[TURNED_ON] != null || edited[FIRST_RUN] != null) return@edit
                if (upgrade) {
                    write(edited, LanguageProfilesState.DEFAULT)
                    onUpgrade(edited)
                } else {
                    edited[FIRST_RUN] = true
                }
            }
        } finally {
            settled.complete(Unit)
        }
    }

    /** Returns once [settleFirstRun] has run in this process. */
    suspend fun awaitFirstRunSettled() = settled.await()

    /** Answers the first run: [language] alone is turned on and active. */
    suspend fun finishFirstRun(language: Language) {
        dataStore.edit { prefs ->
            prefs.remove(FIRST_RUN)
            write(prefs, LanguageProfilesState(listOf(language), language))
        }
    }

    /**
     * Makes a turned-on [language] the active one, moving the stars as [LanguageProfilesState.activated] says; a
     * language that is off is ignored.
     */
    suspend fun setActive(language: Language) {
        dataStore.edit { prefs -> write(prefs, read(prefs).activated(language)) }
    }

    /** Turns [language] on (it goes last) and, with [activate], makes it the active one. */
    suspend fun turnOn(language: Language, activate: Boolean = true) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            val turnedOn = if (language in current.turnedOn) current.turnedOn else current.turnedOn + language
            val state = current.copy(turnedOn = turnedOn)
            write(prefs, if (activate) state.activated(language) else state)
        }
    }

    /**
     * Turns [language] off; the active language moves to the first starred one left, or to the first one left. The
     * last language cannot be turned off. Returns whether it was turned off.
     */
    suspend fun turnOff(language: Language): Boolean {
        var done = false
        dataStore.edit { prefs ->
            val current = read(prefs)
            val turnedOn = current.turnedOn - language
            if (turnedOn.isEmpty() || turnedOn.size == current.turnedOn.size) return@edit
            val starred = current.starred - language
            val active = if (current.active == language) starred.firstOrNull() ?: turnedOn.first() else current.active
            write(prefs, current.copy(turnedOn = turnedOn, active = active, starred = starred - active))
            done = true
        }
        return done
    }

    /**
     * Stars or unstars a turned-on [language] other than the active one, which always counts as quick. A star is not
     * given past [LanguageProfilesState.MAX_STARRED]. Returns whether the state changed.
     */
    suspend fun setStarred(language: Language, starred: Boolean): Boolean {
        var done = false
        dataStore.edit { prefs ->
            val current = read(prefs)
            if (language == current.active || language !in current.turnedOn || (language in current.starred) == starred) return@edit
            if (starred && !current.canStar) return@edit
            val stars = if (starred) current.starred + language else current.starred - language
            write(prefs, current.copy(starred = current.turnedOn.filter { it in stars }))
            done = true
        }
        return done
    }

    /** Shows [language] with the flag of [country], one of its flag countries; the default one clears the choice. */
    suspend fun setFlag(language: Language, country: String) {
        val countries = language.support.flagCountries
        if (country !in countries) return
        dataStore.edit { prefs ->
            val current = read(prefs)
            val flags = if (country == countries.first()) current.flags - language else current.flags + (language to country)
            write(prefs, current.copy(flags = flags))
        }
    }

    /** Replaces the whole state, e.g. from a backup; languages this version does not know are already left out. */
    suspend fun restore(state: LanguageProfilesState) {
        if (state.turnedOn.isEmpty()) return
        dataStore.edit { prefs ->
            prefs.remove(FIRST_RUN)
            write(prefs, state)
        }
    }

    companion object {
        val TURNED_ON = stringPreferencesKey("languages_turned_on")
        val ACTIVE = stringPreferencesKey("language_active")
        val STARRED = stringPreferencesKey("languages_starred")

        /** `code:country` pairs; a language with its default flag has none. */
        val FLAGS = stringPreferencesKey("language_flags")

        /** Set on a fresh install until the user says which language they learn; see [settleFirstRun]. */
        val FIRST_RUN = booleanPreferencesKey("languages_first_run")

        /** Stores [state]; stars past the limit and flags a language does not offer are dropped. */
        fun write(prefs: MutablePreferences, state: LanguageProfilesState) {
            prefs[TURNED_ON] = state.turnedOn.joinToString(",") { it.code }
            val active = state.active.takeIf { it in state.turnedOn } ?: state.turnedOn.first()
            prefs[ACTIVE] = active.code
            val starred = state.turnedOn.filter { it in state.starred && it != active }.take(LanguageProfilesState.MAX_STARRED)
            if (starred.isEmpty()) prefs.remove(STARRED) else prefs[STARRED] = starred.joinToString(",") { it.code }
            // Choices of languages turned off stay, so turning one on again brings its flag back.
            val flags = state.flags.filter { (language, country) -> country in language.support.flagCountries.drop(1) }
            if (flags.isEmpty()) prefs.remove(FLAGS) else prefs[FLAGS] = flags.entries.joinToString(",") { "${it.key.code}:${it.value}" }
        }

        /** Codes this version does not know (written by a newer one) are skipped; nothing usable means the default. */
        fun read(prefs: Preferences): LanguageProfilesState {
            val turnedOn = codes(prefs[TURNED_ON])
            if (turnedOn.isEmpty()) return LanguageProfilesState.DEFAULT
            val active = Language.of(prefs[ACTIVE])?.takeIf { it in turnedOn } ?: turnedOn.first()
            val starred = codes(prefs[STARRED]).toSet()
            val flags = prefs[FLAGS]?.split(',')?.mapNotNull { pair ->
                val language = Language.of(pair.substringBefore(':').trim()) ?: return@mapNotNull null
                val country = pair.substringAfter(':', "").trim().takeIf { it in language.support.flagCountries }
                country?.let { language to it }
            }?.toMap().orEmpty()
            return LanguageProfilesState(
                turnedOn,
                active,
                turnedOn.filter { it in starred && it != active }.take(LanguageProfilesState.MAX_STARRED),
                flags,
            )
        }

        private fun codes(list: String?): List<Language> =
            list?.split(',')?.mapNotNull { Language.of(it.trim()) }?.distinct().orEmpty()
    }
}

/**
 * Name of a preference key that holds a value per language. Japanese keeps the name it had before languages were
 * added, so its settings need no migration; other languages get their code as a suffix.
 */
fun Language.preferenceKey(name: String): String = if (this == Language.JAPANESE) name else "${name}_$code"
