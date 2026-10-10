package com.vpr.screenlate.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import com.vpr.screenlate.settings.SHOW_SOURCE_TEXT_KEY
import com.vpr.screenlate.settings.SettingsKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Parts of a backup that are restored separately; each replaces what the app has. */
enum class BackupSection {
    /** Theme, e-ink mode, update announcements. */
    GENERAL,
    BUBBLE,
    LOOKUP,

    /** Popup font, text size, custom CSS and the installed fonts. */
    POPUP,
    ANKI,
    AUDIO,

    /** The translation button, notes, services, language and favorites. */
    TRANSLATION,

    /** Order, switches and languages of the dictionaries, and the one used for sorting. */
    DICTIONARY_LIST,

    /** The dictionaries themselves, in the app's own converted format. */
    DICTIONARY_FILES,
}

/**
 * Archive layout: [MANIFEST] first, then [SETTINGS], [DICTIONARIES], the font files under [FONTS] and the files of
 * each dictionary under `dictionary-files/<index in the list>/`.
 */
object BackupLayout {
    const val FORMAT = 1
    const val MANIFEST = "screenlate-backup.json"
    const val SETTINGS = "settings.json"
    const val DICTIONARIES = "dictionaries.json"
    const val FONTS = "fonts/"
    const val DICTIONARY_FILES = "dictionary-files/"

    /** The list index and the path inside the dictionary directory, or null for other entries. */
    fun dictionaryFile(entry: String): Pair<Int, String>? {
        if (!entry.startsWith(DICTIONARY_FILES)) return null
        val rest = entry.removePrefix(DICTIONARY_FILES)
        val index = rest.substringBefore('/').toIntOrNull() ?: return null
        val path = rest.substringAfter('/', "")
        return if (safePath(path)) index to path else null
    }

    /** The font file name, or null for other entries. */
    fun fontFile(entry: String): String? =
        entry.takeIf { it.startsWith(FONTS) }?.removePrefix(FONTS)?.takeIf { safePath(it) && '/' !in it }

    /** A relative path that stays inside its directory. */
    fun safePath(path: String): Boolean =
        path.isNotEmpty() && !path.startsWith('/') && '\\' !in path && path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

/**
 * @property sections what the backup holds; [BackupSection.DICTIONARY_FILES] only when the dictionaries were included.
 */
@Serializable
data class BackupManifest(
    val format: Int = BackupLayout.FORMAT,
    val appVersion: String,
    val createdAt: Long,
    val sections: List<BackupSection>,
)

/** Settings kept in the shared preferences file, grouped by the section each key belongs to. */
object BackupPreferences {
    private const val E_INK = "e_ink"
    private val general = setOf(
        "theme_mode",
        "theme_colors",
        E_INK,
        "update_announce",
        LanguageProfiles.TURNED_ON.name,
        LanguageProfiles.ACTIVE.name,
    )

    /**
     * The section of a preference key; null for state the app keeps for itself (one-time hints, update checks,
     * migrations), which a backup leaves out. The sort dictionaries are kept by title with the dictionary list. Each
     * language's settings go with their page's section.
     */
    fun sectionOf(key: String): BackupSection? = when {
        key in general -> BackupSection.GENERAL
        key == SHOW_SOURCE_TEXT_KEY -> BackupSection.POPUP
        key.startsWith("overlay_") -> BackupSection.BUBBLE
        key.startsWith("lookup_") -> BackupSection.LOOKUP
        key.startsWith("popup_") -> BackupSection.POPUP
        key.startsWith("anki_settings") && SettingsKeys.languageOf(key) != null -> BackupSection.ANKI
        key.startsWith("audio_") -> BackupSection.AUDIO
        key.startsWith(TranslationSettingsRepository.KEY_PREFIX) -> BackupSection.TRANSLATION
        else -> null
    }

    /** The backed-up preferences as `{"key": {"type": "int", "value": 3}}`. */
    fun encode(preferences: Preferences): JsonObject = buildJsonObject {
        for ((key, value) in preferences.asMap().entries.sortedBy { it.key.name }) {
            if (sectionOf(key.name) == null) continue
            val (type, json) = when (value) {
                is Boolean -> "boolean" to JsonPrimitive(value)
                is Int -> "int" to JsonPrimitive(value)
                is Long -> "long" to JsonPrimitive(value)
                is Float -> "float" to JsonPrimitive(value)
                is Double -> "double" to JsonPrimitive(value)
                is String -> "string" to JsonPrimitive(value)
                is Set<*> -> "stringSet" to JsonArray(value.map { JsonPrimitive(it as String) })
                else -> continue
            }
            put(key.name, JsonObject(mapOf(TYPE to JsonPrimitive(type), VALUE to json)))
        }
    }

    /** Typed values read back; unknown types and keys outside the backed-up sections are skipped. */
    fun decode(json: JsonObject): Map<Preferences.Key<*>, Any> = buildMap {
        for ((name, element) in json) {
            if (sectionOf(name) == null) continue
            runCatching { typed(name, element.jsonObject) }.getOrNull()?.let { (key, value) -> put(key, value) }
        }
    }

    private fun typed(name: String, entry: JsonObject): Pair<Preferences.Key<*>, Any>? {
        val value: JsonElement = entry[VALUE] ?: return null
        return when (entry[TYPE]?.jsonPrimitive?.content) {
            "boolean" -> Pair(booleanPreferencesKey(name), value.jsonPrimitive.boolean)
            "int" -> Pair(intPreferencesKey(name), value.jsonPrimitive.int)
            "long" -> Pair(longPreferencesKey(name), value.jsonPrimitive.long)
            "float" -> Pair(floatPreferencesKey(name), value.jsonPrimitive.float)
            "double" -> Pair(doublePreferencesKey(name), value.jsonPrimitive.double)
            "string" -> Pair(stringPreferencesKey(name), value.jsonPrimitive.content)
            "stringSet" -> Pair(stringSetPreferencesKey(name), value.jsonArray.map { it.jsonPrimitive.content }.toSet())
            else -> null
        }
    }

    /** Whether the decoded [backup] has e-ink mode on; a restored General section without the key turns it off. */
    fun eInk(backup: Map<Preferences.Key<*>, Any>): Boolean =
        backup.entries.firstOrNull { it.key.name == E_INK }?.value as? Boolean ?: false

    /** Replaces the keys of [sections]: the app's own values go, the backup's come in. */
    @Suppress("UNCHECKED_CAST")
    fun restore(preferences: MutablePreferences, backup: Map<Preferences.Key<*>, Any>, sections: Set<BackupSection>) {
        preferences.asMap().keys.filter { sectionOf(it.name) in sections }.toList().forEach { preferences.remove(it) }
        for ((key, value) in backup) {
            if (sectionOf(key.name) in sections) preferences[key as Preferences.Key<Any>] = value
        }
    }

    private const val TYPE = "type"
    private const val VALUE = "value"
}

/**
 * A dictionary as a backup keeps it: its registry entry without the local id and directory.
 *
 * @property files whether its files are in the archive.
 */
@Serializable
data class BackupDictionary(
    val title: String,
    val revision: String,
    val kind: String,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val frequencyMode: String? = null,
    val enabled: Boolean,
    val priority: Int,
    val termCount: Long = 0,
    val frequencyCount: Long = 0,
    val pitchCount: Long = 0,
    val kanjiCount: Long = 0,
    val mediaCount: Long = 0,
    val isUpdatable: Boolean = false,
    val indexUrl: String? = null,
    val downloadUrl: String? = null,
    val author: String? = null,
    val url: String? = null,
    val description: String? = null,
    val attribution: String? = null,
    val bundled: Boolean = false,
    val importedAt: Long = 0,
    val files: Boolean = false,
) {
    /** A registry entry for [directory]; the id is left for the registry to assign. */
    fun toEntity(directory: String): DictionaryEntity = DictionaryEntity(
        title = title,
        revision = revision,
        kind = DictionaryKind.entries.firstOrNull { it.name == kind } ?: DictionaryKind.TERM,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        frequencyMode = frequencyMode,
        enabled = enabled,
        priority = priority,
        directory = directory,
        termCount = termCount,
        frequencyCount = frequencyCount,
        pitchCount = pitchCount,
        kanjiCount = kanjiCount,
        mediaCount = mediaCount,
        isUpdatable = isUpdatable,
        indexUrl = indexUrl,
        downloadUrl = downloadUrl,
        author = author,
        url = url,
        description = description,
        attribution = attribution,
        bundled = bundled,
        importedAt = importedAt,
    )

    companion object {
        fun of(entity: DictionaryEntity, files: Boolean) = BackupDictionary(
            title = entity.title,
            revision = entity.revision,
            kind = entity.kind.name,
            sourceLanguage = entity.sourceLanguage,
            targetLanguage = entity.targetLanguage,
            frequencyMode = entity.frequencyMode,
            enabled = entity.enabled,
            priority = entity.priority,
            termCount = entity.termCount,
            frequencyCount = entity.frequencyCount,
            pitchCount = entity.pitchCount,
            kanjiCount = entity.kanjiCount,
            mediaCount = entity.mediaCount,
            isUpdatable = entity.isUpdatable,
            indexUrl = entity.indexUrl,
            downloadUrl = entity.downloadUrl,
            author = entity.author,
            url = entity.url,
            description = entity.description,
            attribution = entity.attribution,
            bundled = entity.bundled,
            importedAt = entity.importedAt,
            files = files,
        )
    }
}

/**
 * @property dictionaries in priority order.
 * @property sortDictionary title of the frequency dictionary chosen for sorting Japanese, the only one older versions
 *   read.
 * @property sortDictionaries titles of the frequency dictionaries chosen for sorting, by language code; a backup of an
 *   older version has only [sortDictionary].
 */
@Serializable
data class BackupDictionaryList(
    val dictionaries: List<BackupDictionary>,
    val sortDictionary: String? = null,
    val sortDictionaries: Map<String, String> = emptyMap(),
) {
    /** The title of [language]'s sort dictionary. */
    fun sortDictionary(language: Language): String? =
        sortDictionaries[language.code] ?: sortDictionary.takeIf { language == Language.JAPANESE }
}

/**
 * @property updated every installed dictionary with its new priority, switch and languages.
 * @property missing titles in the backup that are not installed.
 */
data class DictionaryListOutcome(val updated: List<DictionaryEntity>, val missing: List<String>)

object BackupDictionaries {
    /**
     * Applies the backup's order, switches and languages to the [installed] dictionaries. A dictionary is matched
     * by title, else by a title that differs only in its revision mark. Installed ones the backup does not list
     * keep their relative order after the listed ones.
     */
    fun applyList(installed: List<DictionaryEntity>, backup: List<BackupDictionary>): DictionaryListOutcome {
        val left = installed.sortedWith(compareBy({ it.priority }, { it.id })).toMutableList()
        val listed = mutableListOf<DictionaryEntity>()
        val missing = mutableListOf<String>()
        for (entry in backup.sortedBy { it.priority }) {
            val match = match(left, entry.title)
            if (match == null) {
                missing += entry.title
                continue
            }
            left -= match
            listed += match.copy(
                enabled = entry.enabled,
                sourceLanguage = entry.sourceLanguage,
                targetLanguage = entry.targetLanguage.takeIf { match.kind.hasTarget },
            )
        }
        val updated = (listed + left).mapIndexed { index, dictionary -> dictionary.copy(priority = index) }
        return DictionaryListOutcome(updated, missing)
    }

    /** The installed dictionary a backup title stands for. */
    fun match(installed: List<DictionaryEntity>, title: String): DictionaryEntity? =
        installed.firstOrNull { it.title == title }
            ?: installed.filter { dictionaryKey(it.title) == dictionaryKey(title) }.singleOrNull()
}
