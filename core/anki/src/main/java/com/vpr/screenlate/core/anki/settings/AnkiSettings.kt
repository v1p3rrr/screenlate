package com.vpr.screenlate.core.anki.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Where a duplicate is searched for, as in Yomitan. */
enum class DuplicateScope {
    COLLECTION,
    DECK,

    /** The target deck's top-level deck and all its subdecks. */
    DECK_ROOT,
}

/** What happens when the note being added already exists, as in Yomitan. */
enum class DuplicateBehavior {
    /** The ➕ button becomes 📖, which opens the existing note; holding it adds anyway. */
    PREVENT,

    /** The existing note's fields are replaced. */
    OVERWRITE,

    /** Another note is added anyway. */
    NEW,
}

/** How a field of an existing note changes when a duplicate overwrites it (Yomitan's per-field overwrite modes). */
enum class OverwriteMode {
    /** Keep the existing value unless it is empty. */
    COALESCE,

    /** Take the new value unless it is empty. */
    COALESCE_NEW,
    OVERWRITE,
    SKIP,
    APPEND,
    PREPEND,
    ;

    fun apply(existing: String, new: String): String = when (this) {
        COALESCE -> existing.ifEmpty { new }
        COALESCE_NEW -> new.ifEmpty { existing }
        OVERWRITE -> new
        SKIP -> existing
        APPEND -> existing + new
        PREPEND -> new + existing
    }
}

/** Field templates and overwrite modes of one note type, kept while another note type is selected. */
@Serializable
data class NoteTemplate(
    val fields: Map<String, String> = emptyMap(),
    val overwriteModes: Map<String, OverwriteMode> = emptyMap(),
)

/**
 * Note export settings. Field templates are keyed by field name and use `{marker}` placeholders
 * (see `FieldTemplate`); fields without a template stay empty.
 *
 * @property overwriteModes per field, used by [DuplicateBehavior.OVERWRITE]; missing fields use [OverwriteMode.COALESCE].
 * @property savedTemplates templates of note types used before, by note type name.
 * @property duplicateAllModels search every note type, not just [modelId].
 */
@Serializable
data class AnkiSettings(
    val deckId: Long? = null,
    val deckName: String? = null,
    val modelId: Long? = null,
    val modelName: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val overwriteModes: Map<String, OverwriteMode> = emptyMap(),
    val savedTemplates: Map<String, NoteTemplate> = emptyMap(),
    val tags: String = DEFAULT_TAGS,
    val duplicateCheck: Boolean = true,
    val duplicateScope: DuplicateScope = DuplicateScope.COLLECTION,
    val duplicateAllModels: Boolean = false,
    val duplicateBehavior: DuplicateBehavior = DuplicateBehavior.PREVENT,
) {
    val configured: Boolean get() = deckId != null && modelId != null && fields.values.any { it.isNotBlank() }

    companion object {
        const val DEFAULT_TAGS = "screenlate"
    }
}

@Singleton
class AnkiSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AnkiSettings> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { json.decodeFromString<AnkiSettings>(it) }.getOrNull() } ?: AnkiSettings()
    }

    suspend fun current(): AnkiSettings = settings.first()

    suspend fun update(transform: (AnkiSettings) -> AnkiSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { runCatching { json.decodeFromString<AnkiSettings>(it) }.getOrNull() }
                ?: AnkiSettings()
            prefs[KEY] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("anki_settings")
    }
}
