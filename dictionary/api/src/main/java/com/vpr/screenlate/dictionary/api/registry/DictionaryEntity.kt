package com.vpr.screenlate.dictionary.api.registry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vpr.screenlate.core.common.Language

/**
 * An imported dictionary as known to the app. The converted data lives in [directory] under the dictionary
 * storage root (see `DictionaryStorage`).
 *
 * A dictionary may carry several kinds of data (for example terms and frequencies); the counts tell which
 * lookups it takes part in, [kind] is the primary one for display.
 *
 * @property priority lookup and display order; lower comes first.
 * @property frequencyMode Yomitan `frequencyMode`: `rank-based` or `occurrence-based`.
 * @property indexUrl Yomitan `indexUrl`, used to check for updates.
 * @property bundled whether the dictionary was installed from the APK.
 */
@Entity(tableName = "dictionaries", indices = [Index(value = ["title"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val revision: String,
    val kind: DictionaryKind,
    val sourceLanguage: String?,
    val targetLanguage: String?,
    val frequencyMode: String?,
    val enabled: Boolean,
    val priority: Int,
    val directory: String,
    val termCount: Long,
    val frequencyCount: Long,
    val pitchCount: Long,
    val kanjiCount: Long,
    val mediaCount: Long,
    val isUpdatable: Boolean,
    val indexUrl: String?,
    val downloadUrl: String?,
    val author: String?,
    val url: String?,
    val description: String?,
    val attribution: String?,
    val bundled: Boolean,
    val importedAt: Long,
)

/** Whether the dictionary is for words of [language]; one that does not say its language is used for any. */
fun DictionaryEntity.isFor(language: Language): Boolean = sourceLanguage == null || sourceLanguage == language.code

/**
 * Whether this is the only enabled dictionary with definitions, among [all], for one of the turned-on [languages] it is
 * for; lookups in that language would find nothing without it. Like lookups, it leaves out dictionaries whose files
 * are gone ([hasFiles]).
 */
fun DictionaryEntity.isLastTermDictionary(
    all: List<DictionaryEntity>,
    languages: Collection<Language>,
    hasFiles: (DictionaryEntity) -> Boolean,
): Boolean {
    fun searched(dictionary: DictionaryEntity) = dictionary.enabled && dictionary.termCount > 0 && hasFiles(dictionary)
    return searched(this) && languages.any { language ->
        isFor(language) && all.none { it.id != id && searched(it) && it.isFor(language) }
    }
}

/**
 * Dictionaries that an import's switches (from [before] to [after], both full lists) turn off although a turned-on
 * language (of [languages]) that had an enabled dictionary with definitions would then have none: per such language
 * the first of [after]'s order that was on before. Imports keep them on, as the Dictionaries screen does. Dictionaries
 * whose files are gone ([hasFiles]) do not count, as lookups leave them out.
 */
fun keptTermDictionaries(
    before: List<DictionaryEntity>,
    after: List<DictionaryEntity>,
    languages: Collection<Language>,
    hasFiles: (DictionaryEntity) -> Boolean,
): List<DictionaryEntity> {
    val wasOn = before.filter { it.enabled && it.termCount > 0 && hasFiles(it) }.mapTo(hashSetOf()) { it.id }
    val kept = mutableListOf<DictionaryEntity>()
    for (language in languages) {
        if (before.none { it.id in wasOn && it.isFor(language) }) continue
        val terms = after.filter { it.termCount > 0 && it.isFor(language) && hasFiles(it) }
        if (terms.any { it.enabled || kept.any { k -> k.id == it.id } }) continue
        terms.firstOrNull { it.id in wasOn }?.let { kept += it }
    }
    return kept
}
