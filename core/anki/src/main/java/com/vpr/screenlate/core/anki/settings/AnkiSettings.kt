package com.vpr.screenlate.core.anki.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.cached
import com.vpr.screenlate.core.common.settings.preferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
@Serializable(with = DuplicateBehaviorSerializer::class)
enum class DuplicateBehavior {
    /** The ➕ button becomes 📖, which opens the existing note; holding it adds anyway. */
    PREVENT,

    /** The existing note's fields are updated according to each field's [OverwriteMode]. */
    OVERWRITE,

    /** Another note is added anyway. */
    NEW,
}

/**
 * Stores [DuplicateBehavior] by name, mapping the legacy `APPEND` mode to [DuplicateBehavior.OVERWRITE] and unknown
 * values to [DuplicateBehavior.PREVENT].
 */
internal object DuplicateBehaviorSerializer : KSerializer<DuplicateBehavior> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vpr.screenlate.core.anki.settings.DuplicateBehavior", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DuplicateBehavior) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): DuplicateBehavior = when (val name = decoder.decodeString()) {
        "APPEND" -> DuplicateBehavior.OVERWRITE
        else -> DuplicateBehavior.entries.firstOrNull { it.name == name } ?: DuplicateBehavior.PREVENT
    }
}

/** How a field of an existing note changes when a duplicate overwrites it (Yomitan's per-field overwrite modes). */
@Serializable(with = OverwriteModeSerializer::class)
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
        APPEND -> merge(existing, new, prepend = false)
        PREPEND -> merge(existing, new, prepend = true)
    }

    private fun merge(existing: String, new: String, prepend: Boolean): String {
        if (existing.isEmpty()) return new
        if (new.isEmpty()) return existing
        if (isMediaOrGroup(existing) || isMediaOrGroup(new)) {
            if (existing.contains(new)) return existing
            return if (prepend) new + existing else existing + new
        }
        val segments = existing.split(BR_REGEX)
        if (segments.any { it.trim() == new.trim() }) return existing
        return if (prepend) "$new<br>$existing" else "$existing<br>$new"
    }

    private fun isMediaOrGroup(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("<img") && trimmed.endsWith(">")) ||
            (trimmed.startsWith("[sound:") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("<span class=\"group\"") && trimmed.endsWith("</span>"))
    }

    companion object {
        private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    }
}

/**
 * Stores [OverwriteMode] by name. The modes are map values, which `coerceInputValues` does not cover: a mode this
 * version does not know reads as [OverwriteMode.COALESCE] instead of failing, and resetting, all Anki settings.
 */
internal object OverwriteModeSerializer : KSerializer<OverwriteMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vpr.screenlate.core.anki.settings.OverwriteMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OverwriteMode) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): OverwriteMode {
        val name = decoder.decodeString()
        return OverwriteMode.entries.firstOrNull { it.name == name } ?: OverwriteMode.COALESCE
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
 * @property duplicateScopeDeckOnly when [duplicateScope] is [DuplicateScope.COLLECTION], only update duplicates in the target deck.
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
    val duplicateScopeDeckOnly: Boolean = true,
    val duplicateAllModels: Boolean = false,
    val duplicateBehavior: DuplicateBehavior = DuplicateBehavior.PREVENT,
    val screenshotByDefault: Boolean = false,
) {
    val configured: Boolean get() = deckId != null && modelId != null && fields.values.any { it.isNotBlank() }

    companion object {
        const val DEFAULT_TAGS = "screenlate"
    }
}

/** Anki settings per language: each language has its own deck, note type and templates, and starts empty. */
@Singleton
class AnkiSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    // A value this version does not know (settings of a newer version) falls back to its default instead of
    // failing the whole object, which would reset every setting.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun read(prefs: Preferences, language: Language): AnkiSettings =
        prefs[key(language)]?.let { runCatching { json.decodeFromString<AnkiSettings>(it) }.getOrNull() } ?: AnkiSettings()

    fun settings(language: Language): Flow<AnkiSettings> = dataStore.data.map { read(it, language) }

    /** The settings as last read, for a screen's first frame; null before the first read. */
    fun cachedSettings(language: Language): AnkiSettings? = dataStore.cached { read(it, language) }

    suspend fun current(language: Language): AnkiSettings = settings(language).first()

    suspend fun update(language: Language, transform: (AnkiSettings) -> AnkiSettings) {
        dataStore.edit { prefs -> prefs[key(language)] = json.encodeToString(transform(read(prefs, language))) }
    }

    companion object {
        /** Name of the preference key; other languages than Japanese add their code (see [preferenceKey]). */
        const val KEY = "anki_settings"

        private fun key(language: Language) = stringPreferencesKey(language.preferenceKey(KEY))
    }
}
