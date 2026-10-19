package com.vpr.screenlate.backup

import androidx.compose.ui.state.ToggleableState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vpr.screenlate.core.common.Language
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The choice among the files of a list item (the fonts, the dictionaries): the item's mark and the files marked
 * otherwise. A file not seen before takes the item's mark.
 */
data class FileChoice(val on: Boolean, val flipped: Set<String> = emptySet()) {
    fun isOn(file: String): Boolean = on != (file in flipped)

    fun chosen(files: Collection<String>): Set<String> = files.filterTo(linkedSetOf(), ::isOn)

    /** All, none or some of [files]. */
    fun state(files: Collection<String>): ToggleableState {
        val count = files.count(::isOn)
        return when {
            count == 0 -> ToggleableState.Off
            count == files.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    }

    /** Marks every file the same: on, unless all are on already. */
    fun toggleAll(files: Collection<String>): FileChoice = FileChoice(on = state(files) != ToggleableState.On)

    fun toggle(file: String, files: Collection<String>): FileChoice {
        val next = (if (file in flipped) flipped - file else flipped + file).filterTo(hashSetOf()) { it in files }
        // Every file marked otherwise: the item takes their mark, so a file added later gets it too.
        return if (files.isNotEmpty() && files.all { it in next }) FileChoice(!on) else FileChoice(on, next)
    }
}

/**
 * What goes into a backup or comes out of one.
 *
 * @property off settings sections and the dictionary list left out; fonts and dictionaries are chosen in [fonts]
 *   and [dictionaries].
 * @property dictionaries by title when creating, by index in the backup's list when restoring.
 * @property languages by code: a language left out takes out its own settings, dictionaries, fonts and recognition
 *   models.
 */
data class BackupChoice(
    val off: Set<BackupSection> = emptySet(),
    val fonts: FileChoice = FileChoice(on = true),
    val dictionaries: FileChoice = FileChoice(on = false),
    val languages: FileChoice = FileChoice(on = true),
) {
    /**
     * Whether the [listed] languages (keyed by code, with the size of their recognition models) are offered: several of
     * them, or one with models. Otherwise each is taken.
     */
    fun offersLanguages(listed: List<BackupFile>): Boolean = listed.size > 1 || listed.any { (it.bytes ?: 0) > 0 }

    /**
     * The codes of the [listed] languages that are taken, with the codes of their dictionaries where these differ (sh
     * for both Serbo-Croatian entries).
     */
    fun chosenLanguages(listed: List<BackupFile>): Set<String> {
        val codes = listed.map { it.key }
        val chosen = if (offersLanguages(listed)) languages.chosen(codes) else codes.toSet()
        return chosen + chosen.mapNotNull { Language.of(it)?.dictionaryCode }
    }

    /** Whether a language taken from [listed] has recognition models, which go in or come out with it. */
    fun takesModels(listed: List<BackupFile>): Boolean {
        val chosen = chosenLanguages(listed)
        return listed.any { it.key in chosen && (it.bytes ?: 0) > 0 }
    }

    /** The [files] that are shared or belong to a language taken from [listed]. */
    fun visible(files: List<BackupFile>, listed: List<BackupFile>): List<BackupFile> {
        val chosen = chosenLanguages(listed)
        return files.filter { it.language == null || it.language in chosen }
    }

    /** The sections [fonts] and [dictionaries] leave something in, in the order of [BackupSection]. */
    fun sections(offered: Collection<BackupSection>, fontFiles: Collection<String>, dictionaryFiles: Collection<String>): Set<BackupSection> =
        BackupSection.entries.filterTo(linkedSetOf()) { section ->
            section in offered && when (section) {
                BackupSection.FONTS -> fonts.chosen(fontFiles).isNotEmpty()
                BackupSection.DICTIONARY_FILES -> dictionaries.chosen(dictionaryFiles).isNotEmpty()
                else -> section !in off
            }
        }

    fun toggle(section: BackupSection): BackupChoice = copy(off = if (section in off) off - section else off + section)

    companion object {
        /** Restoring starts with everything the backup holds. */
        val ALL = BackupChoice(dictionaries = FileChoice(on = true), languages = FileChoice(on = true))
    }
}

/** The last choice of what goes into a backup; the next backup starts from it. App state, not backed up. */
@Singleton
class BackupChoiceStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val choice: Flow<BackupChoice> = dataStore.data.map(::read)

    suspend fun save(choice: BackupChoice) {
        dataStore.edit { preferences ->
            preferences[SECTIONS_OFF] = choice.off.mapTo(hashSetOf()) { it.name }
            preferences[FONTS_ON] = choice.fonts.on
            preferences[FONTS_FLIPPED] = choice.fonts.flipped
            preferences[DICTIONARIES_ON] = choice.dictionaries.on
            preferences[DICTIONARIES_FLIPPED] = choice.dictionaries.flipped
            preferences[LANGUAGES_ON] = choice.languages.on
            preferences[LANGUAGES_FLIPPED] = choice.languages.flipped
        }
    }

    companion object {
        private val SECTIONS_OFF = stringSetPreferencesKey("backup_sections_off")
        private val FONTS_ON = booleanPreferencesKey("backup_fonts_on")
        private val FONTS_FLIPPED = stringSetPreferencesKey("backup_fonts_flipped")
        private val DICTIONARIES_ON = booleanPreferencesKey("backup_dictionaries_on")
        private val DICTIONARIES_FLIPPED = stringSetPreferencesKey("backup_dictionaries_flipped")
        private val LANGUAGES_ON = booleanPreferencesKey("backup_languages_on")
        private val LANGUAGES_FLIPPED = stringSetPreferencesKey("backup_languages_flipped")

        /** The keys, which the full reset clears. */
        val KEYS = setOf(SECTIONS_OFF, FONTS_ON, FONTS_FLIPPED, DICTIONARIES_ON, DICTIONARIES_FLIPPED, LANGUAGES_ON, LANGUAGES_FLIPPED)
            .map { it.name }.toSet()

        /** The first time every settings section, font and language is on and the dictionaries are not. */
        fun read(preferences: Preferences): BackupChoice = BackupChoice(
            off = preferences[SECTIONS_OFF].orEmpty().mapNotNullTo(hashSetOf()) { name -> BackupSection.entries.firstOrNull { it.name == name } },
            fonts = FileChoice(preferences[FONTS_ON] ?: true, preferences[FONTS_FLIPPED].orEmpty()),
            dictionaries = FileChoice(preferences[DICTIONARIES_ON] ?: false, preferences[DICTIONARIES_FLIPPED].orEmpty()),
            languages = FileChoice(preferences[LANGUAGES_ON] ?: true, preferences[LANGUAGES_FLIPPED].orEmpty()),
        )
    }
}
